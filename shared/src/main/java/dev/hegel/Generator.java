package dev.hegel;

import dev.hegel.generators.FilteredGenerator;
import dev.hegel.generators.FlatMappedGenerator;
import dev.hegel.generators.MappedGenerator;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Produces values of type {@code T} from a {@link TestCase}.
 *
 * <p>Generators are deterministic functions of the engine's choices, not sources of randomness. Use
 * the factory methods on {@link Generators} to construct them and the combinators here to transform
 * them. The engine's typed draw functions and span protocol are implementation details and never
 * surface through this interface.
 *
 * @param <T> the type of value produced
 */
public interface Generator<T> {
    /**
     * Draw a value. Always draw through {@link TestCase#draw(Generator)}; this is the plumbing
     * beneath it, called only by the generator implementations in {@code dev.hegel.generators}.
     *
     * @param tc the current test case
     * @return the generated value
     * @hidden internal: user code draws via {@link TestCase#draw(Generator)}
     */
    T doDraw(TestCase tc);

    /**
     * Transform each generated value with {@code f}.
     *
     * @param f the mapping function
     * @param <U> the result type
     * @return a generator of mapped values
     */
    default <U> Generator<U> map(Function<? super T, ? extends U> f) {
        return new MappedGenerator<>(this, f);
    }

    /**
     * Keep only values satisfying {@code predicate}. Prefer constraining a generator at
     * construction time (e.g. bounds) over filtering when possible.
     *
     * @param predicate the acceptance test
     * @return a filtered generator
     */
    default Generator<T> filter(Predicate<? super T> predicate) {
        return new FilteredGenerator<>(this, predicate);
    }

    /**
     * Dependent generation: draw a value, then draw from the generator {@code f} returns for it.
     *
     * @param f maps a drawn value to the next generator
     * @param <U> the result type
     * @return a generator of dependent values
     */
    default <U> Generator<U> flatMap(Function<? super T, ? extends Generator<U>> f) {
        return new FlatMappedGenerator<>(this, f);
    }
}
