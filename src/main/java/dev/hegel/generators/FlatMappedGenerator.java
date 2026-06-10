package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.function.Function;

/**
 * Result of {@link Generator#flatMap}. Always uses the composite draw path: it draws a value, asks
 * {@code f} for the next generator, and draws from it, bracketing the pair in a {@code flat_map}
 * span.
 *
 * @param <T> the source value type
 * @param <U> the dependent value type
 */
public final class FlatMappedGenerator<T, U> implements Generator<U> {
    private final Generator<T> source;
    private final Function<? super T, ? extends Generator<U>> f;

    public FlatMappedGenerator(Generator<T> source, Function<? super T, ? extends Generator<U>> f) {
        this.source = source;
        this.f = f;
    }

    @Override
    public U doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_FLAT_MAP);
        try {
            T value = source.doDraw(tc);
            Generator<U> next = f.apply(value);
            return next.doDraw(tc);
        } finally {
            tc.stopSpan(false);
        }
    }
}
