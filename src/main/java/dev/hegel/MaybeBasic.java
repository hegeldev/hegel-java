package dev.hegel;

/**
 * Implemented by generators that <em>may</em> have a schema (basic) representation.
 *
 * <p>{@link Gen#asBasic} consults this to decide whether a generator can be drawn in a single
 * engine call. A generator that is conditionally basic (a list whose elements are basic, a {@code
 * one_of} whose alternatives are all basic) returns its {@link BasicGenerator} or {@code null}.
 */
interface MaybeBasic<T> {
  /** The basic representation, or {@code null} if this generator must use the composite path. */
  BasicGenerator<T> asBasic();
}
