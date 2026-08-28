package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates {@code true} or {@code false} with equal probability.
 */
public final class BooleanGenerator implements Generator<Boolean> {
    /** @hidden */
    @Override
    public Boolean doDraw(TestCase tc) {
        return tc.generateBoolean(0.5);
    }
}
