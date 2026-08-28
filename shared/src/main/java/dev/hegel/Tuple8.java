package dev.hegel;

/**
 * An immutable tuple of eight heterogeneously-typed values.
 *
 * @param value1 the first element
 * @param value2 the second element
 * @param value3 the third element
 * @param value4 the fourth element
 * @param value5 the fifth element
 * @param value6 the sixth element
 * @param value7 the seventh element
 * @param value8 the eighth element
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 * @param <T3> the type of the third element
 * @param <T4> the type of the fourth element
 * @param <T5> the type of the fifth element
 * @param <T6> the type of the sixth element
 * @param <T7> the type of the seventh element
 * @param <T8> the type of the eighth element
 */
public record Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>(
        T1 value1, T2 value2, T3 value3, T4 value4, T5 value5, T6 value6, T7 value7, T8 value8) {}
