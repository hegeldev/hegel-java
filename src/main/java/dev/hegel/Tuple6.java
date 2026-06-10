package dev.hegel;

/**
 * An immutable tuple of six heterogeneously-typed values.
 *
 * @param value1 the first element
 * @param value2 the second element
 * @param value3 the third element
 * @param value4 the fourth element
 * @param value5 the fifth element
 * @param value6 the sixth element
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 * @param <T3> the type of the third element
 * @param <T4> the type of the fourth element
 * @param <T5> the type of the fifth element
 * @param <T6> the type of the sixth element
 */
public record Tuple6<T1, T2, T3, T4, T5, T6>(T1 value1, T2 value2, T3 value3, T4 value4, T5 value5, T6 value6) {}
