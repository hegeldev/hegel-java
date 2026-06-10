package dev.hegel;

/**
 * An immutable tuple of three heterogeneously-typed values.
 *
 * @param value1 the first element
 * @param value2 the second element
 * @param value3 the third element
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 * @param <T3> the type of the third element
 */
public record Tuple3<T1, T2, T3>(T1 value1, T2 value2, T3 value3) {}
