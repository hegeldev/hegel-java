package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatefulTest {

  /** A well-behaved counter: multiple rules and an invariant that always holds. */
  static final class Counter implements StateMachine {
    private long n = 0;

    @Override
    public List<Rule> rules() {
      return List.of(
          Rule.of("inc", tc -> n++),
          Rule.of("dec", tc -> n--),
          Rule.of("add", tc -> n += tc.draw(integers(0, 5))));
    }

    @Override
    public List<Rule> invariants() {
      return List.of(Rule.of("finite", tc -> assertTrue(n > Long.MIN_VALUE)));
    }
  }

  @Test
  void counterModelPasses() {
    Hegel.with().testCases(30).noDatabase().check(tc -> Stateful.run(tc, new Counter()));
  }

  /** A stack whose invariant has a bug: it claims the size never exceeds two. */
  static final class BuggyStack implements StateMachine {
    private final Deque<Integer> stack = new ArrayDeque<>();

    @Override
    public List<Rule> rules() {
      return List.of(
          Rule.of("push", tc -> stack.push(tc.draw(integers(0, 100)))),
          Rule.of(
              "pop",
              tc -> {
                tc.assume(!stack.isEmpty()); // skip this rule when there is nothing to pop
                stack.pop();
              }));
    }

    @Override
    public List<Rule> invariants() {
      return List.of(Rule.of("smallStack", tc -> assertTrue(stack.size() <= 2)));
    }
  }

  @Test
  void buggyModelFailsAndShrinks() {
    assertThrows(
        AssertionError.class,
        () ->
            Hegel.with()
                .testCases(300)
                .noDatabase()
                .check(tc -> Stateful.run(tc, new BuggyStack())));
  }

  @Test
  void singleRuleNoInvariantsSucceeds() {
    // Single rule (no index draw), no invariants (empty post-step loop).
    Hegel.with()
        .testCases(20)
        .noDatabase()
        .check(tc -> Stateful.run(tc, () -> List.of(Rule.of("noop", t -> {}))));
  }

  @Test
  void rulesThatAlwaysRejectTerminate() {
    // The rule always rejects; the attempt budget must end the run rather than loop forever.
    Hegel.with()
        .testCases(20)
        .noDatabase()
        .check(tc -> Stateful.run(tc, () -> List.of(Rule.of("nope", t -> t.assume(false)))));
  }

  @Test
  void emptyRulesRejected() {
    TestCase tc = new TestCase(null, false, System.err);
    assertThrows(IllegalArgumentException.class, () -> Stateful.run(tc, List::of));
  }
}
