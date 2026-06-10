package dev.hegel;

/**
 * An immutable tuple of five heterogeneously-typed values.
 *
 * @param value1 the first element
 * @param value2 the second element
 * @param value3 the third element
 * @param value4 the fourth element
 * @param value5 the fifth element
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 * @param <T3> the type of the third element
 * @param <T4> the type of the fourth element
 * @param <T5> the type of the fifth element
 */
public record Tuple5<T1, T2, T3, T4, T5>(T1 value1, T2 value2, T3 value3, T4 value4, T5 value5) {}
