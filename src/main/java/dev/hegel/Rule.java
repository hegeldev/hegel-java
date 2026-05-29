package dev.hegel;

import java.util.function.Consumer;

/**
 * A named action (or invariant) of a {@link StateMachine}. A rule may draw values from and mutate
 * the system under test; an invariant should only inspect it. A rule that calls {@link
 * TestCase#assume} to reject the current state is skipped and another rule is tried.
 */
public final class Rule {
  final String name;
  final Consumer<TestCase> action;

  private Rule(String name, Consumer<TestCase> action) {
    this.name = name;
    this.action = action;
  }

  /**
   * Create a rule.
   *
   * @param name a label shown in the step trace of a failing run
   * @param action the action to apply, given the current test case
   * @return the rule
   */
  public static Rule of(String name, Consumer<TestCase> action) {
    return new Rule(name, action);
  }
}
