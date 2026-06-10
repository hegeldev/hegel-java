package dev.hegel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Hegel-testing-Hegel utilities used by the conformance suite. */
final class Utils {
    private Utils() {}

    /** Assert that every generated value satisfies {@code predicate}. */
    static <T> void assertAllExamples(Generator<T> gen, Predicate<T> predicate) {
        Hegel.test(
                tc -> {
                    T value = tc.draw(gen);
                    if (!predicate.test(value)) {
                        throw new AssertionError("predicate failed for: " + TestCase.repr(value));
                    }
                },
                new Settings().database(Database.disabled()));
    }

    /** Assert that no generated value satisfies {@code condition}. */
    static <T> void assertNoExamples(Generator<T> gen, Predicate<T> condition) {
        Hegel.test(
                tc -> {
                    T value = tc.draw(gen);
                    if (condition.test(value)) {
                        throw new AssertionError("unexpected example: " + TestCase.repr(value));
                    }
                },
                new Settings().database(Database.disabled()));
    }

    /** Find the minimal generated value satisfying {@code condition} (exercises shrinking). */
    static <T> T minimal(Generator<T> gen, Predicate<T> condition) {
        return minimal(gen, condition, new Settings().testCases(500));
    }

    /**
     * Find the minimal generated value satisfying {@code condition} under the given {@code
     * settings} (exercises shrinking). The database is always disabled, so callers only need to set
     * what they care about (typically a larger {@code testCases} budget for harder shrink targets).
     */
    static <T> T minimal(Generator<T> gen, Predicate<T> condition, Settings settings) {
        AtomicReference<T> found = new AtomicReference<>();
        AtomicBoolean any = new AtomicBoolean(false);
        try {
            Hegel.test(
                    tc -> {
                        T value = tc.draw(gen);
                        if (condition.test(value)) {
                            found.set(value);
                            any.set(true);
                            throw new AssertionError("found");
                        }
                    },
                    settings.database(Database.disabled()));
        } catch (AssertionError expected) {
            // The run threw because we found and shrank a counterexample.
        }
        if (!any.get()) {
            throw new AssertionError("no example satisfied the condition");
        }
        return found.get();
    }

    /** Find any generated value satisfying {@code condition}. */
    static <T> T findAny(Generator<T> gen, Predicate<T> condition) {
        return minimal(gen, condition);
    }
}
