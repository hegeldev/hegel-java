package dev.hegel;

import java.util.List;

/**
 * A model for stateful (model-based) testing: a set of {@link Rule}s to apply in a generated order,
 * and {@link #invariants()} checked after each successful step. Implement this over a fresh
 * instance of the system under test per test case and drive it with {@link Stateful#run}:
 *
 * <pre>{@code
 * Hegel.check(tc -> Stateful.run(tc, new CounterModel()));
 * }</pre>
 */
public interface StateMachine {
  /**
   * The rules (actions) that may be applied to the system under test. Must be non-empty.
   *
   * @return the rules
   */
  List<Rule> rules();

  /**
   * Invariants checked after each successful rule application (and once before any rule). The
   * default is none.
   *
   * @return the invariants
   */
  default List<Rule> invariants() {
    return List.of();
  }
}
