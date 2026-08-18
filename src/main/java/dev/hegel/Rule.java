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
public @interface Rule {}
