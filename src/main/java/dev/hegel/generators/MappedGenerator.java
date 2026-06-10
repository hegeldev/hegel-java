package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.function.Function;

/**
 * Result of {@link Generator#map}. Preserves the efficient single-draw path: when the source is
 * basic, {@link #asBasic} composes {@code f} over the source's parse (via {@link
 * BasicGenerator#mapBasic}) so the chain stays a single engine call; otherwise the draw is bracketed
 * in a {@code map} span.
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
        BasicGenerator<U> basic = asBasic();
        if (basic != null) {
            return basic.doDraw(tc);
        }
        tc.startSpan(Abi.LABEL_MAPPED);
        try {
            return f.apply(source.doDraw(tc));
        } finally {
            tc.stopSpan(false);
        }
    }

    /** @hidden */
    @Override
    public BasicGenerator<U> asBasic() {
        BasicGenerator<T> sourceBasic = source.asBasic();
        return sourceBasic == null ? null : sourceBasic.mapBasic(f);
    }
}
