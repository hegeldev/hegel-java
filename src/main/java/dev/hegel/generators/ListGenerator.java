package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates lists by driving the engine's collection API element by element: the engine decides how
 * many elements to produce and the shrinker can delete or reorder them. The element generator opens
 * its own spans, so no per-element span is needed here (matching the engine's canonical client).
 *
 * <p>The length range defaults to any size; narrow it with the fluent {@link #minSize(int)} /
 * {@link #maxSize(int)} methods.
 */
public final class ListGenerator<T> implements Generator<List<T>> {
    private final Generator<T> element;
    private final long minSize;
    private final long maxSize;

    public ListGenerator(Generator<T> element, long minSize, long maxSize) {
        Sizes.validate(minSize, maxSize, "lists");
        this.element = element;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * @param minSize the minimum length (inclusive)
     * @return a copy with the minimum size set
     */
    public ListGenerator<T> minSize(int minSize) {
        return new ListGenerator<>(element, minSize, maxSize);
    }

    /**
     * @param maxSize the maximum length (inclusive)
     * @return a copy with the maximum size set
     */
    public ListGenerator<T> maxSize(int maxSize) {
        return new ListGenerator<>(element, minSize, maxSize);
    }

    /** @hidden */
    @Override
    public List<T> doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_LIST);
        try {
            long id = tc.newCollection(minSize, maxSize);
            List<T> out = new ArrayList<>();
            while (tc.collectionMore(id)) {
                out.add(element.doDraw(tc));
            }
            return out;
        } finally {
            tc.stopSpan(false);
        }
    }
}
