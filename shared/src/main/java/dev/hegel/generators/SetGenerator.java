package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates sets of distinct elements by driving the engine's collection API and rejecting
 * duplicates, so the engine keeps producing until the set reaches its size.
 *
 * <p>The size range defaults to any size; narrow it with the fluent {@link #minSize(int)} / {@link
 * #maxSize(int)} methods.
 */
public final class SetGenerator<T> implements Generator<Set<T>> {
    private final Generator<T> element;
    private final long minSize;
    private final long maxSize;

    public SetGenerator(Generator<T> element, long minSize, long maxSize) {
        Sizes.validate(minSize, maxSize, "sets");
        this.element = element;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * @param minSize the minimum size (inclusive)
     * @return a copy with the minimum size set
     */
    public SetGenerator<T> minSize(int minSize) {
        return new SetGenerator<>(element, minSize, maxSize);
    }

    /**
     * @param maxSize the maximum size (inclusive)
     * @return a copy with the maximum size set
     */
    public SetGenerator<T> maxSize(int maxSize) {
        return new SetGenerator<>(element, minSize, maxSize);
    }

    /** @hidden */
    @Override
    public Set<T> doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_SET);
        try {
            long id = tc.newCollection(minSize, maxSize);
            Set<T> out = new LinkedHashSet<>();
            while (tc.collectionMore(id)) {
                T value = element.doDraw(tc);
                if (!out.add(value)) {
                    tc.collectionReject(id, "duplicate element");
                }
            }
            return out;
        } finally {
            tc.stopSpan(false);
        }
    }
}
