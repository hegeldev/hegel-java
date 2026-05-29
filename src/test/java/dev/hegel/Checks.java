package dev.hegel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Hegel-testing-Hegel utilities used by the conformance suite. */
final class Checks {
  private Checks() {}

  /** Assert that every generated value satisfies {@code predicate}. */
  static <T> void assertAllExamples(Generator<T> gen, Predicate<T> predicate) {
    Hegel.with()
        .testCases(200)
        .noDatabase()
        .check(
            tc -> {
              T value = tc.draw(gen);
              if (!predicate.test(value)) {
                throw new AssertionError("predicate failed for: " + TestCase.repr(value));
              }
            });
  }

  /** Assert that no generated value satisfies {@code condition}. */
  static <T> void assertNoExamples(Generator<T> gen, Predicate<T> condition) {
    Hegel.with()
        .testCases(200)
        .noDatabase()
        .check(
            tc -> {
              T value = tc.draw(gen);
              if (condition.test(value)) {
                throw new AssertionError("unexpected example: " + TestCase.repr(value));
              }
            });
  }

  /** Find the minimal generated value satisfying {@code condition} (exercises shrinking). */
  static <T> T minimal(Generator<T> gen, Predicate<T> condition) {
    AtomicReference<T> found = new AtomicReference<>();
    AtomicBoolean any = new AtomicBoolean(false);
    try {
      Hegel.with()
          .testCases(500)
          .noDatabase()
          .check(
              tc -> {
                T value = tc.draw(gen);
                if (condition.test(value)) {
                  found.set(value);
                  any.set(true);
                  throw new AssertionError("found");
                }
              });
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
