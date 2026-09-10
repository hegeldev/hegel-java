package dev.hegel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a state-machine class as an invariant: a property {@link Stateful#run} checks
 * on the machine's initial and final state and samples after rules in between (each check runs with
 * probability {@code 1 / stepCount}, keeping an invariant's expected cost per test case constant as
 * the step count grows). Set {@link #alwaysRun} to check it after every rule instead. The method
 * must take a single {@link TestCase} parameter and assert on the machine's state.
 *
 * <p>See {@link Stateful} for a complete example.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Invariant {
    /**
     * Check this invariant after every rule instead of sampling it. Use it for invariants that must
     * observe every intermediate state, including invariants that mutate state when checked.
     *
     * @return whether the invariant runs at every step
     */
    boolean alwaysRun() default false;
}
