package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.List;

/**
 * Picks one of a fixed, non-empty list of values (the first is the simplest for shrinking). Drawn
 * as an index into the list.
 *
 * @param <T> the value type
 */
public final class SampledFromGenerator<T> implements Generator<T> {
    private final List<T> values;

    /**
     * @param values a non-empty list of candidates; defensively copied
     */
    public SampledFromGenerator(List<T> values) {
        this.values = List.copyOf(values);
    }

    /** @hidden */
    @Override
    public T doDraw(TestCase tc) {
        return values.get((int) tc.generateInteger(0, values.size() - 1));
    }
}
