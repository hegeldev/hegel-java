package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates maps by driving the engine's collection API, drawing a key then a value and rejecting
 * duplicate keys.
 *
 * <p>The entry-count range defaults to any size; narrow it with the fluent {@link #minSize(int)} /
 * {@link #maxSize(int)} methods.
 */
public final class MapGenerator<K, V> implements Generator<Map<K, V>> {
    private final Generator<K> keys;
    private final Generator<V> values;
    private final long minSize;
    private final long maxSize;

    public MapGenerator(Generator<K> keys, Generator<V> values, long minSize, long maxSize) {
        Sizes.validate(minSize, maxSize, "maps");
        this.keys = keys;
        this.values = values;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * @param minSize the minimum entry count (inclusive)
     * @return a copy with the minimum size set
     */
    public MapGenerator<K, V> minSize(int minSize) {
        return new MapGenerator<>(keys, values, minSize, maxSize);
    }

    /**
     * @param maxSize the maximum entry count (inclusive)
     * @return a copy with the maximum size set
     */
    public MapGenerator<K, V> maxSize(int maxSize) {
        return new MapGenerator<>(keys, values, minSize, maxSize);
    }

    /** @hidden */
    @Override
    public Map<K, V> doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_MAP);
        try {
            long id = tc.newCollection(minSize, maxSize);
            Map<K, V> out = new LinkedHashMap<>();
            while (tc.collectionMore(id)) {
                K key = keys.doDraw(tc);
                if (out.containsKey(key)) {
                    tc.collectionReject(id, "duplicate key");
                } else {
                    out.put(key, values.doDraw(tc));
                }
            }
            return out;
        } finally {
            tc.stopSpan(false);
        }
    }
}
