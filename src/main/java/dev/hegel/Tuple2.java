package dev.hegel;

/**
 * An immutable tuple of two heterogeneously-typed values.
 *
 * @param value1 the first element
 * @param value2 the second element
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 */
public record Tuple2<T1, T2>(T1 value1, T2 value2) {}
