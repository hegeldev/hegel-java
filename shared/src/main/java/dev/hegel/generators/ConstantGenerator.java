package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Always generates the same value, drawing nothing from the engine.
 *
 * @param <T> the constant value type
 */
public final class ConstantGenerator<T> implements Generator<T> {
    private final T value;

    public ConstantGenerator(T value) {
        this.value = value;
    }

    /** @hidden */
    @Override
    public T doDraw(TestCase tc) {
        return value;
    }
}
