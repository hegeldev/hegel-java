package dev.hegel;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The handle a property test body uses to draw values and steer the engine.
 *
 * <p>An instance is supplied to the test body for each case the engine runs. Draw values with
 * {@link #draw(Generator)}, reject uninteresting inputs with {@link #assume(boolean)}, attach debug
 * context with {@link #note(String)}, and guide the search with {@link #target(double)}.
 *
 * <p>On the replay of a minimal failing example, each top-level {@code draw} is printed as an
 * assignment (for example {@code x = 42;}) so the counterexample is readable.
 */
public final class TestCase {
    private final DataSource source;
    private final boolean reporting;
    private final PrintStream out;
    private int drawDepth;
    private int drawCounter;

    TestCase(DataSource source, boolean reporting, PrintStream out) {
        this.source = source;
        this.reporting = reporting;
        this.out = out;
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
        try {
            value = generator.doDraw(this);
        } finally {
            drawDepth--;
        }
        if (top) {
            drawCounter++;
            if (reporting) {
                String name = (label != null) ? label : "draw_" + drawCounter;
                out.println(name + " = " + repr(value) + ";");
            }
        }
        return value;
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
     * Record a debug message, shown only on the replay of a failing case.
     *
     * @param message the message to record
     */
    public void note(String message) {
        if (reporting) {
            out.println(message);
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

    /** @hidden */
    public void startSpan(long label) {
        source.startSpan(label);
    }

    /** @hidden */
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

    long newStateMachine(List<String> ruleNames, List<String> invariantNames) {
        return source.newStateMachine(ruleNames, invariantNames);
    }

    long stateMachineNextRule(long stateMachineId) {
        return source.stateMachineNextRule(stateMachineId);
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
