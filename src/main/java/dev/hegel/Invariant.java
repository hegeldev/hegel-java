package dev.hegel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a state-machine class as an invariant: a property {@link Stateful#run} checks
 * before the first rule and after each successful rule application. The method must take a single
 * {@link TestCase} parameter and assert on the machine's state.
 *
 * <p>See {@link Stateful} for a complete example.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Invariant {}
