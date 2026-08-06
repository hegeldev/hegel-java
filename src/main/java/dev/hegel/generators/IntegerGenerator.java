package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates {@code int} values within an inclusive {@code [min, max]} range.
 *
 * <p>The default range is the full {@code int} range; narrow it with the fluent {@link #min(int)} /
 * {@link #max(int)} methods.
 */
public final class IntegerGenerator implements Generator<Integer> {
    private final int min;
    private final int max;

    public IntegerGenerator(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("integers: min (" + min + ") > max (" + max + ")");
        }
        this.min = min;
        this.max = max;
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public IntegerGenerator min(int min) {
        return new IntegerGenerator(min, max);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public IntegerGenerator max(int max) {
        return new IntegerGenerator(min, max);
    }

    /** @hidden */
    @Override
    public Integer doDraw(TestCase tc) {
        return (int) tc.generateInteger(min, max);
    }
}
