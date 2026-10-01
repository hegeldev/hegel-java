package dev.hegel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a state-machine class as a rule: an action {@link Stateful#run} may apply to
 * the machine during a stateful test. The method must take a single {@link TestCase} parameter and
 * typically mutates the machine and asserts on the outcome; draw any values the action needs from
 * the test case.
 *
 * <p>See {@link Stateful} for a complete example.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule {
    /**
     * The rule's concurrency group, for machines run with {@link Stateful.Options#maxConcurrency
     * maxConcurrency} above 1. Each round the engine picks one group and hands out only that
     * group's rules, so rules in the same group may run concurrently with each other and rules in
     * different groups never overlap. Rules that name no group share the {@link
     * Stateful#ANONYMOUS_GROUP anonymous group}. Group names are arbitrary and only appear in the
     * round headers of a failure report. Groups have no observable effect on a sequential machine.
     *
     * @return the group name, or the empty string for the anonymous group
     */
    String group() default "";

    /**
     * How often the engine should pick this rule relative to the machine's other rules: a rule of
     * weight 5 is offered about five times as often as a rule of weight 1. The weight is a hint,
     * not a distributional guarantee — each test case enables a random subset of rules, and a
     * rule's realized frequency depends on which others are enabled alongside it. Must be finite
     * and strictly positive.
     *
     * @return the rule's selection weight
     */
    double weight() default 1.0;
}
