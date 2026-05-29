package dev.hegel;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Produces values of type {@code T} from a {@link TestCase}.
 *
 * <p>Generators are deterministic functions of the engine's choices, not sources of randomness. Use
 * the factory methods on {@link Generators} to construct them and the combinators here to transform
 * them. Schemas and the CBOR wire format are implementation details and never surface through this
 * interface.
 *
 * @param <T> the type of value produced
 */
@FunctionalInterface
public interface Generator<T> {
  /**
   * Draw a value. Prefer {@link TestCase#draw(Generator)} in test bodies; call this directly only
   * from inside another generator's composite implementation.
   *
   * @param tc the current test case
   * @return the generated value
   */
  T generate(TestCase tc);

  /**
   * Transform each generated value with {@code f}. Preserves the efficient single-draw path when
   * this generator is schema-describable.
   *
   * @param f the mapping function
   * @param <U> the result type
   * @return a generator of mapped values
   */
  default <U> Generator<U> map(Function<? super T, ? extends U> f) {
    BasicGenerator<T> basic = Gen.asBasic(this);
    if (basic != null) {
      return basic.mapBasic(f);
    }
    Generator<T> self = this;
    return new Gen.Composite<>(Abi.LABEL_MAPPED, tc -> f.apply(self.generate(tc)));
  }

  /**
   * Keep only values satisfying {@code predicate}. Always uses the composite draw path; prefer
   * constraining a generator at construction time (e.g. bounds) over filtering when possible.
   *
   * @param predicate the acceptance test
   * @return a filtered generator
   */
  default Generator<T> filter(Predicate<? super T> predicate) {
    return Gen.filter(this, predicate);
  }

  /**
   * Dependent generation: draw a value, then draw from the generator {@code f} returns for it.
   *
   * @param f maps a drawn value to the next generator
   * @param <U> the result type
   * @return a generator of dependent values
   */
  default <U> Generator<U> flatMap(Function<? super T, ? extends Generator<U>> f) {
    return Gen.flatMap(this, f);
  }
}
