package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.function.Function;

/**
 * Result of {@link Generator#map}. Draws from the source and applies {@code f}, bracketing the pair
 * in a {@code map} span so the shrinker treats them as one unit.
 *
 * @param <T> the source value type
 * @param <U> the mapped value type
 */
public final class MappedGenerator<T, U> implements Generator<U> {
    private final Generator<T> source;
    private final Function<? super T, ? extends U> f;

    public MappedGenerator(Generator<T> source, Function<? super T, ? extends U> f) {
        this.source = source;
        this.f = f;
    }

    @Override
    public U doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_MAPPED);
        try {
            return f.apply(source.doDraw(tc));
        } finally {
            tc.stopSpan(false);
        }
    }
}
