package dev.hegel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>On the replay of a minimal failing example ({@link #isFinal()}), each top-level {@code draw}
 * and each note is handed to the run's {@link Reporter} (the default prints {@code x = 42;}) and
 * recorded on the resulting {@link Failure}, so the counterexample is readable. Under {@link
 * Verbosity#VERBOSE} or higher the reporter also sees every other case's draws and notes. A
 * label drawn more than once in a case is numbered from its second use ({@code x}, {@code x_2},
 * {@code x_3}); unlabelled draws are {@code draw_1}, {@code draw_2}, ...
 *
 * <p>Frontends implementing their own composite generators enclose their draws in a labelled
 * {@link #span(long, Supplier) span} so the engine can shrink the structure they build.
 */
public final class TestCase {
    private final DataSource source;
    private final boolean finalReplay;
    /** Whether draws and notes reach the reporter: on a final replay, or on every case when verbose. */
    private final boolean reporting;

    private final Reporter reporter;
    private final Map<String, Object> draws = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    /** Uses per draw name, for numbering repeats ({@code x}, {@code x_2}, ...; {@code draw_N}). */
    private final Map<String, Integer> nameUses = new HashMap<>();
    /** Notes made while a top-level draw is in progress; flushed after that draw's line. */
    private final List<String> pendingNotes = new ArrayList<>();

    private int drawDepth;

    TestCase(DataSource source, boolean finalReplay, Reporter reporter) {
        this(source, finalReplay, false, reporter);
    }

    TestCase(DataSource source, boolean finalReplay, boolean verbose, Reporter reporter) {
        this.source = source;
        this.finalReplay = finalReplay;
        this.reporting = finalReplay || verbose;
        this.reporter = reporter;
    }

    /**
     * Whether this case is the final replay of a minimal counterexample (or of a {@link
     * Settings#reproduceFailure(String)} blob), as opposed to one of the many cases the engine runs
     * while generating and shrinking. Draws and notes are reported only on a final replay; a test
     * body can use this to do its own expensive diagnostics only where they will be seen.
     *
     * @return {@code true} on a final replay
     */
    public boolean isFinal() {
        return finalReplay;
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
            if (reporting) {
                String name = displayName(label);
                if (finalReplay) {
                    draws.put(name, value);
                }
                reporter.draw(name, value, finalReplay);
            }
            flushPendingNotes();
        }
        return value;
    }

    /**
     * The name a top-level draw reports under: a label prints bare the first time and numbered from
     * its second use ({@code x}, {@code x_2}, ...); an unlabelled draw is {@code draw_N}, counting
     * unlabelled draws only.
     */
    private String displayName(String label) {
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
        if (finalReplay) {
            notes.add(message);
        }
        reporter.note(message, finalReplay);
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
     * Record a debug message, reported on the final replay of a failing case (and on every case
     * under {@link Verbosity#VERBOSE}). A note made while a top-level draw is in progress — from
     * inside a composite generator — is reported after that draw's value, never before it.
     *
     * @param message the message to record
     */
    public void note(String message) {
        if (!reporting) {
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

    long newStateMachine(List<String> ruleNames, List<String> invariantNames, boolean[] invariantAlwaysCheck) {
        return source.newStateMachine(ruleNames, invariantNames, invariantAlwaysCheck);
    }

    long stateMachineNextGroup(long stateMachineId) {
        return source.stateMachineNextGroup(stateMachineId);
    }

    long stateMachineNextRule(long stateMachineId) {
        return source.stateMachineNextRule(stateMachineId);
    }

    void stateMachineRuleRejected(long stateMachineId) {
        source.stateMachineRuleRejected(stateMachineId);
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

    /** The top-level draws recorded on a final replay, in draw order. */
    Map<String, Object> draws() {
        return draws;
    }

    /** The notes recorded on a final replay, in order. */
    List<String> notes() {
        return notes;
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
