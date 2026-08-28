package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.function.Function;

/**
 * A generator built from imperative code that draws from other generators, backing {@link
 * dev.hegel.Generators#composite}. Always non-basic: it brackets the body in a single {@code
 * composite} span.
 *
 * @param <T> the value type
 */
public final class CompositeGenerator<T> implements Generator<T> {
    private final Function<TestCase, T> body;

    public CompositeGenerator(Function<TestCase, T> body) {
        this.body = body;
    }

    @Override
    public T doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_COMPOSITE);
        try {
            return body.apply(tc);
        } finally {
            tc.stopSpan(false);
        }
    }
}
