package dev.hegel;

import java.util.List;

/**
 * Drives a {@link StateMachine} as a property test, mirroring hegel-go's {@code RunStateful}.
 *
 * <p>It checks the invariants once, draws a step count, and for each step draws a rule, applies it
 * inside a STATEFUL span, and re-checks the invariants. Rules that reject the current state via
 * {@link TestCase#assume} are skipped and another rule is drawn, up to a retry budget. A failing
 * rule or invariant fails the test, and the engine shrinks the sequence of choices to a minimal
 * reproducing run.
 *
 * <p>Value pools ({@code Variables}) are not yet supported; hold the system's state in the {@link
 * StateMachine} instance, created fresh per test case.
 */
public final class Stateful {
  private Stateful() {}

  /** Maximum number of successful steps per test case. */
  static final int MAX_STEPS = 50;

  /**
   * Run {@code machine} against the current test case.
   *
   * @param tc the current test case
   * @param machine the model to drive (created fresh per test case)
   */
  public static void run(TestCase tc, StateMachine machine) {
    List<Rule> rules = machine.rules();
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("state machine has no rules");
    }
    List<Rule> invariants = machine.invariants();

    tc.note("Initial invariant check.");
    for (Rule inv : invariants) {
      callInvariant(tc, inv);
    }

    int nSteps = tc.draw(Generators.integers(1, MAX_STEPS));
    int maxAttempts = nSteps * 10 + 100; // budget against rules that always reject
    int succeeded = 0;
    int attempts = 0;
    int step = 0;
    while (succeeded < nSteps && attempts < maxAttempts) {
      step++;
      attempts++;
      int idx = rules.size() == 1 ? 0 : tc.draw(Generators.integers(0, rules.size() - 1));
      Rule rule = rules.get(idx);
      tc.note("Step " + step + ": " + rule.name);
      if (callRule(tc, rule)) {
        succeeded++;
        for (Rule inv : invariants) {
          callInvariant(tc, inv);
        }
      }
    }
  }

  /** Run an invariant inside a STATEFUL span; failures propagate as test failures. */
  private static void callInvariant(TestCase tc, Rule inv) {
    tc.startSpan(Abi.LABEL_STATEFUL);
    try {
      inv.action.accept(tc);
    } finally {
      tc.stopSpan(false);
    }
  }

  /**
   * Run a rule inside a STATEFUL span, recovering an {@link TestCase#assume} rejection.
   *
   * @return {@code true} if the rule ran to completion, {@code false} if it rejected the state
   */
  private static boolean callRule(TestCase tc, Rule rule) {
    tc.startSpan(Abi.LABEL_STATEFUL);
    try {
      rule.action.accept(tc);
      return true;
    } catch (AssumeRejected e) {
      return false;
    } finally {
      tc.stopSpan(false);
    }
  }
}
