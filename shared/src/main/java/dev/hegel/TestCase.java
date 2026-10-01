package dev.hegel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The handle a property test body uses to draw values and steer the engine.
 *
 * <p>An instance is supplied to the test body for each case the engine runs. Draw values with
 * {@link #draw(Generator)}, reject uninteresting inputs with {@link #assume(boolean)}, attach debug
 * context with {@link #note(String)}, and guide the search with {@link #target(double)}.
 *
 * <p>On an execution the engine stamped for capture ({@link #isFinal()}) — the final replay of a
 * minimal failing example among them — each top-level {@code draw} and each note is recorded, and
 * if the case fails and the engine reports that failure, they are handed to the run's {@link
 * Reporter} (the default prints {@code x = 42;}) and carried by the resulting {@link Failure}, so
 * the counterexample is readable. Under {@link Verbosity#VERBOSE} or higher the reporter sees every
 * case's draws and notes live. A label drawn more than once in a case is numbered from its second
 * use ({@code x}, {@code x_2}, {@code x_3}); unlabelled draws are {@code draw_1}, {@code draw_2},
 * ...
 *
 * <p>Frontends implementing their own composite generators enclose their draws in a labelled
 * {@link #span(long, Supplier) span} so the engine can shrink the structure they build.
 */
public final class TestCase {
    private final DataSource source;
    /** Whether the engine stamped this execution for capture: its draws and notes are recorded. */
    private final boolean captured;
    /** Whether draws and notes reach the reporter live (a verbose run). */
    private final boolean reporting;

    private final Reporter reporter;
    /**
     * The root handle of this test case, whose draw-name counter every handle of the case shares:
     * {@code this} for the root, the root for a worker handle (see {@link #forWorker}).
     */
    private final TestCase root;
    /** The concurrent worker this is a per-round handle for, or {@code -1} for the root. */
    private final int worker;
    /** Reference instant for a worker handle's line stamps ({@link System#nanoTime()}). */
    private final long startNanos;

    private final Map<String, Object> draws = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    /**
     * The recorded draws and notes in report order. On a worker handle this is the round's
     * buffer, absorbed by the root at the join point ({@link #absorb}).
     */
    private final List<Event> events = new ArrayList<>();
    /**
     * Uses per draw name, for numbering repeats ({@code x}, {@code x_2}, ...; {@code draw_N}).
     * Only the root's is used; worker handles go through {@link #nextName} on the root.
     */
    private final Map<String, Integer> nameUses = new HashMap<>();
    /** Notes made while a top-level draw is in progress; flushed after that draw's line. */
    private final List<String> pendingNotes = new ArrayList<>();

    private int drawDepth;

    /** A recorded draw ({@code message == null}) or note, as the reporter would be told about it. */
    private static final class Event {
        /** The draw's name as reported (stamped with the worker prefix on a worker handle). */
        final String name;
        /** The draw's plain name, the key it is recorded under. */
        final String key;

        final Object value;
        final String message;

        Event(String name, String key, Object value, String message) {
            this.name = name;
            this.key = key;
            this.value = value;
            this.message = message;
        }

        void replay(Reporter reporter, boolean finalReplay) {
            if (message != null) {
                reporter.note(message, finalReplay);
            } else {
                reporter.draw(name, value, finalReplay);
            }
        }
    }

    TestCase(DataSource source, boolean captured, Reporter reporter) {
        this(source, captured, false, reporter);
    }

    TestCase(DataSource source, boolean captured, boolean verbose, Reporter reporter) {
        this.source = source;
        this.captured = captured;
        this.reporting = verbose;
        this.reporter = reporter;
        this.root = this;
        this.worker = -1;
        this.startNanos = 0;
    }

    private TestCase(DataSource source, TestCase root, int worker, long startNanos) {
        this.source = source;
        this.captured = root.captured;
        this.reporting = root.reporting;
        this.reporter = root.reporter;
        this.root = root;
        this.worker = worker;
        this.startNanos = startNanos;
    }

    /**
     * A handle for concurrent worker {@code worker} to draw through for one round of a stateful
     * machine: an independent choice stream of the same case (see {@link
     * DataSource#cloneForWorker}) whose draws and notes are buffered, stamped {@code [worker N
     * +X.XXXms]} with the time since {@code startNanos}, and handed to this root at the join point
     * by {@link #absorb}. Release it with {@link #release()} once the round is over.
     */
    TestCase forWorker(int worker, long startNanos) {
        return new TestCase(source.cloneForWorker(worker), this, worker, startNanos);
    }

    /** The worker this handle belongs to, or {@code -1} for the root. */
    int worker() {
        return worker;
    }

    /** Free the clone handle behind a worker handle from {@link #forWorker}. */
    void release() {
        source.release();
    }

    /**
     * Take over a worker handle's buffered draws and notes: record them here (on a captured
     * execution) and hand them to the reporter (on a verbose one), in the order the worker made
     * them. Called on the root at a join point, once the worker has finished its round, so the
     * reporter only ever hears from the driving thread.
     */
    void absorb(TestCase workerHandle) {
        for (Event event : workerHandle.events) {
            record(event);
        }
        workerHandle.events.clear();
    }

    /** The stamp a worker handle's lines carry; empty on the root. */
    private String prefix() {
        if (worker < 0) {
            return "";
        }
        double elapsedMillis = (System.nanoTime() - startNanos) / 1e6;
        return String.format(Locale.ROOT, "[worker %d +%.3fms] ", worker, elapsedMillis);
    }

    /**
     * Deliver a draw or note: buffered on a worker handle until the root absorbs it; on the root,
     * kept for the report when captured and reported live when verbose.
     */
    private void record(Event event) {
        if (worker >= 0) {
            events.add(event);
            return;
        }
        if (captured) {
            events.add(event);
            if (event.message != null) {
                notes.add(event.message);
            } else {
                draws.put(event.key, event.value);
            }
        }
        if (reporting) {
            event.replay(reporter, false);
        }
    }

    /**
     * Whether the engine stamped this execution as one a failure report can be built from — the
     * final replay of a minimal counterexample (or of a {@link Settings#reproduceFailure(String)}
     * blob), the replays that confirm a discovered failure — as opposed to one of the many cases it
     * runs while generating and shrinking. Draws and notes are recorded only on a stamped
     * execution, and reported only if it fails and the engine reports that failure; a test body can
     * use this to do its own expensive diagnostics only where they may be seen.
     *
     * @return {@code true} on an execution stamped for capture
     */
    public boolean isFinal() {
        return captured;
    }

    /**
     * Draw a value from {@code generator}.
     *
     * @param generator the generator to draw from
     * @param <T> the value type
     * @return the generated value
     */
    public <T> T draw(Generator<T> generator) {
        return draw(generator, null);
    }

    /**
     * Draw a value, naming it {@code label} in the falsifying-example output.
     *
     * @param generator the generator to draw from
     * @param label the variable name to show in counterexample output
     * @param <T> the value type
     * @return the generated value
     */
    public <T> T draw(Generator<T> generator, String label) {
        boolean top = drawDepth == 0;
        drawDepth++;
        T value;
        boolean completed = false;
        try {
            value = generator.doDraw(this);
            completed = true;
        } finally {
            drawDepth--;
            if (top && !completed) {
                // No draw line will follow; do not lose the notes made on the way to the failure.
                flushPendingNotes();
            }
        }
        if (top) {
            if (captured || reporting) {
                String name = root.nextName(label);
                record(new Event(prefix() + name, name, value, null));
            }
            flushPendingNotes();
        }
        return value;
    }

    /**
     * The name a top-level draw reports under: a label prints bare the first time and numbered from
     * its second use ({@code x}, {@code x_2}, ...); an unlabelled draw is {@code draw_N}, counting
     * unlabelled draws only. Names are unique across the whole case — worker handles number
     * through the root — hence synchronized: concurrent workers name their draws at the same time.
     */
    private synchronized String nextName(String label) {
        String base = label != null ? label : "draw";
        int uses = nameUses.merge(base, 1, Integer::sum);
        if (label == null) {
            return base + "_" + uses;
        }
        return uses == 1 ? label : label + "_" + uses;
    }

    private void flushPendingNotes() {
        for (String message : pendingNotes) {
            emitNote(message);
        }
        pendingNotes.clear();
    }

    private void emitNote(String message) {
        record(new Event(null, null, null, prefix() + message));
    }

    /**
     * Reject the current test case unless {@code condition} holds. The engine discards it without
     * counting it against the test-case budget and tries another input.
     *
     * @param condition the precondition that must hold
     */
    public void assume(boolean condition) {
        if (!condition) {
            throw new AssumeRejected();
        }
    }

    /**
     * Record a debug message, reported with the counterexample when this case's failure is
     * reported (and live on every case under {@link Verbosity#VERBOSE}). A note made while a
     * top-level draw is in progress — from inside a composite generator — is reported after that
     * draw's value, never before it.
     *
     * @param message the message to record
     */
    public void note(String message) {
        if (!(captured || reporting)) {
            return;
        }
        if (drawDepth > 0) {
            pendingNotes.add(message);
        } else {
            emitNote(message);
        }
    }

    /**
     * Provide a score for the coverage-guided search; higher is treated as more interesting.
     *
     * @param value the observation
     */
    public void target(double value) {
        target(value, "");
    }

    /**
     * Provide a labelled score for the coverage-guided search.
     *
     * @param value the observation
     * @param label groups observations for multi-objective search
     */
    public void target(double value, String label) {
        source.target(value, label);
    }

    // --- engine primitives used by generators in dev.hegel.generators (public for cross-package
    // access; not part of the user-facing API) ---

    /** @hidden */
    public boolean generateBoolean(double p) {
        return source.generateBoolean(p);
    }

    /** @hidden */
    public long generateInteger(long min, long max) {
        return source.generateInteger(min, max);
    }

    /** @hidden */
    public double generateFloat(
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude) {
        return source.generateFloat(
                width, min, max, allowNan, allowInfinity, excludeMin, excludeMax, smallestNonzeroMagnitude);
    }

    /** @hidden */
    public byte[] generateBytes(long minSize, long maxSize) {
        return source.generateBytes(minSize, maxSize);
    }

    /** @hidden */
    public String generateString(StringGeneratorHandle generator) {
        return source.generateString(generator);
    }

    /** @hidden */
    public LocalDate generateDate(LocalDate min, LocalDate max) {
        return source.generateDate(min, max);
    }

    /** @hidden */
    public LocalTime generateTime(LocalTime min, LocalTime max) {
        return source.generateTime(min, max);
    }

    /** @hidden */
    public LocalDateTime generateDatetime(LocalDateTime min, LocalDateTime max) {
        return source.generateDatetime(min, max);
    }

    /** @hidden */
    public UUID generateUuid(Integer version) {
        return source.generateUuid(version);
    }

    /** @hidden */
    public byte[] generateIpv4() {
        return source.generateIpv4();
    }

    /** @hidden */
    public byte[] generateIpv6() {
        return source.generateIpv6();
    }

    /** @hidden */
    public StringGeneratorHandle textGenerator(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters) {
        return source.textGenerator(
                minSize,
                maxSize,
                codec,
                minCodepoint,
                maxCodepoint,
                categories,
                excludeCategories,
                includeCharacters,
                excludeCharacters);
    }

    /** @hidden */
    public StringGeneratorHandle regexGenerator(String pattern, boolean fullmatch, StringGeneratorHandle alphabet) {
        return source.regexGenerator(pattern, fullmatch, alphabet);
    }

    /** @hidden */
    public StringGeneratorHandle emailGenerator() {
        return source.emailGenerator();
    }

    /** @hidden */
    public StringGeneratorHandle urlGenerator() {
        return source.urlGenerator();
    }

    /** @hidden */
    public StringGeneratorHandle domainGenerator(long maxLength) {
        return source.domainGenerator(maxLength);
    }

    /** @hidden */
    public boolean ownsStringGenerator(StringGeneratorHandle generator) {
        return source.ownsStringGenerator(generator);
    }

    /**
     * Draw a structured value inside a labelled span: open the span, run {@code body}, and close
     * the span whether or not the body completes. This is how composite generators tell the engine
     * which draws belong together, so it can shrink the structure as a unit; {@link Label} lists
     * the engine's structural labels and {@link Label#of(String)} mints custom ones.
     *
     * @param label the span's label
     * @param body the draws making up the value
     * @param <T> the value type
     * @return the body's result
     */
    public <T> T span(long label, Supplier<T> body) {
        startSpan(label);
        try {
            return body.get();
        } finally {
            stopSpan(false);
        }
    }

    /**
     * Open a labelled span. Every {@code startSpan} must be matched by a {@link #stopSpan(boolean)}
     * on every exit path, including exceptional ones; prefer {@link #span(long, Supplier)}, which
     * handles that.
     *
     * @param label the span's label (see {@link Label})
     */
    public void startSpan(long label) {
        source.startSpan(label);
    }

    /**
     * Close the innermost open span. Passing {@code discard = true} tells the engine the span's
     * draws were rejected (for example, a filtered value that failed its predicate) and will be
     * retried. A no-op once the case has been concluded by the engine, so span-closing {@code
     * finally} blocks are safe while a case unwinds.
     *
     * @param discard whether the span's draws were rejected
     */
    public void stopSpan(boolean discard) {
        source.stopSpan(discard);
    }

    /** @hidden */
    public long newCollection(long minSize, long maxSize) {
        return source.newCollection(minSize, maxSize);
    }

    /** @hidden */
    public boolean collectionMore(long id) {
        return source.collectionMore(id);
    }

    /** @hidden */
    public void collectionReject(long id, String why) {
        source.collectionReject(id, why);
    }

    // --- stateful-testing primitives, used by Stateful and Pool in this package ---

    long newPool() {
        return source.newPool();
    }

    long poolAdd(long poolId) {
        return source.poolAdd(poolId);
    }

    long poolGenerate(long poolId, boolean consume) {
        return source.poolGenerate(poolId, consume);
    }

    DataSource.StateMachine newStateMachine(
            List<String> ruleNames,
            long[] ruleGroups,
            double[] ruleWeights,
            List<String> invariantNames,
            boolean[] invariantAlwaysCheck,
            long minConcurrency,
            long maxConcurrency,
            int stepCount) {
        return source.newStateMachine(
                ruleNames,
                ruleGroups,
                ruleWeights,
                invariantNames,
                invariantAlwaysCheck,
                minConcurrency,
                maxConcurrency,
                stepCount);
    }

    long stateMachineNextGroup(long stateMachineId) {
        return source.stateMachineNextGroup(stateMachineId);
    }

    long stateMachineNextRule(long stateMachineId, long workerIndex) {
        return source.stateMachineNextRule(stateMachineId, workerIndex);
    }

    void stateMachineRuleRejected(long stateMachineId, long workerIndex) {
        source.stateMachineRuleRejected(stateMachineId, workerIndex);
    }

    boolean stateMachineShouldCheckInvariant(long stateMachineId, long invariantIndex) {
        return source.stateMachineShouldCheckInvariant(stateMachineId, invariantIndex);
    }

    void stateMachineFree(long stateMachineId) {
        source.stateMachineFree(stateMachineId);
    }

    /** Whether the engine has concluded this case (overrun or invalid), so its body must unwind. */
    boolean isAborted() {
        return source.isAborted();
    }

    /** The top-level draws recorded on a captured execution, in draw order. */
    Map<String, Object> draws() {
        return draws;
    }

    /** The notes recorded on a captured execution, in order. */
    List<String> notes() {
        return notes;
    }

    /** Hand the recorded draws and notes to {@code reporter}, in report order, flagged as a replay. */
    void replayTo(Reporter reporter) {
        for (Event event : events) {
            event.replay(reporter, true);
        }
    }

    static String repr(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof byte[] b) {
            return Arrays.toString(b);
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(repr(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(repr(e.getKey())).append(": ").append(repr(e.getValue()));
            }
            return sb.append("}").toString();
        }
        return String.valueOf(value);
    }
}
