package dev.hegel;

import dev.hegel.generators.BasicGenerator;
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
 * them. Schemas and the CBOR wire format are implementation details and never surface through this
 * interface.
 *
 * @param <T> the type of value produced
 */
public interface Generator<T> {
    /**
     * Draw a value. Always draw through {@link TestCase#draw(Generator)}; this is the plumbing
     * beneath it, called only by the generator implementations in {@code dev.hegel.generators}.
     *
     * <p>The default delegates to {@link #asBasic()}, so a schema-describable generator need only
     * implement {@code asBasic()}. A generator with no schema representation must override this.
     *
     * @param tc the current test case
     * @return the generated value
     * @hidden internal: user code draws via {@link TestCase#draw(Generator)}
     */
    default T doDraw(TestCase tc) {
        BasicGenerator<T> basic = asBasic();
        if (basic == null) {
            throw new IllegalStateException("a Generator must override doDraw(TestCase) or asBasic()");
        }
        return basic.doDraw(tc);
    }

    /**
     * The schema (basic) representation of this generator, or {@code null} if it must use the
     * composite draw path. A generator is basic when it can be drawn in a single engine call; a
     * conditionally-basic generator (a list whose elements are basic, a {@code one_of} whose
     * alternatives are all basic) returns its representation or {@code null} accordingly.
     *
     * @return the basic representation, or {@code null}
     * @hidden internal: schemas never surface through the public API
     */
    default BasicGenerator<T> asBasic() {
        return null;
    }

    /**
     * Transform each generated value with {@code f}. Preserves the efficient single-draw path when
     * this generator is schema-describable.
     *
     * @param f the mapping function
     * @param <U> the result type
     * @return a generator of mapped values
     */
    default <U> Generator<U> map(Function<? super T, ? extends U> f) {
        return new MappedGenerator<>(this, f);
    }

    /**
     * Keep only values satisfying {@code predicate}. Always uses the composite draw path; prefer
     * constraining a generator at construction time (e.g. bounds) over filtering when possible.
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
