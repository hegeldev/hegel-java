package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates maps. Basic (one engine call) when both key and value generators are basic; otherwise
 * drives the collection API, drawing a key then a value and rejecting duplicate keys.
 *
 * <p>The entry-count range defaults to any size; narrow it with the fluent {@link #minSize(int)} /
 * {@link #maxSize(int)} methods. {@code maps(k, v, a, b)} is exactly {@code maps(k,
 * v).minSize(a).maxSize(b)}.
 *
 * <p>The engine's basic {@code dict} value is an array of {@code [key, value]} pairs, not a CBOR
 * map.
 */
public final class MapGenerator<K, V> implements Generator<Map<K, V>>, MaybeBasic<Map<K, V>> {
  private final Generator<K> keys;
  private final Generator<V> values;
  private final long minSize;
  private final long maxSize;

  MapGenerator(Generator<K> keys, Generator<V> values, long minSize, long maxSize) {
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

  @Override
  public BasicGenerator<Map<K, V>> asBasic() {
    BasicGenerator<K> k = Gen.asBasic(keys);
    BasicGenerator<V> v = Gen.asBasic(values);
    if (k == null || v == null) {
      return null;
    }
    CBORObject schema =
        CBORObject.NewMap()
            .Add("type", "dict")
            .Add("keys", k.schema)
            .Add("values", v.schema)
            .Add("min_size", minSize);
    if (maxSize != Abi.UNBOUNDED) {
      schema.Add("max_size", maxSize);
    }
    return new BasicGenerator<>(
        schema,
        raw -> {
          Map<K, V> out = new LinkedHashMap<>();
          for (Object pairRaw : Cbor.asList(raw)) {
            List<Object> pair = Cbor.asList(pairRaw);
            out.put(k.parseRaw(pair.get(0)), v.parseRaw(pair.get(1)));
          }
          return out;
        });
  }

  @Override
  public Map<K, V> generate(TestCase tc) {
    BasicGenerator<Map<K, V>> basic = asBasic();
    if (basic != null) {
      return basic.generate(tc);
    }
    tc.startSpan(Abi.LABEL_MAP);
    try {
      long id = tc.newCollection(minSize, maxSize);
      Map<K, V> out = new LinkedHashMap<>();
      while (tc.collectionMore(id)) {
        K key = keys.generate(tc);
        if (out.containsKey(key)) {
          tc.collectionReject(id, "duplicate key");
        } else {
          out.put(key, values.generate(tc));
        }
      }
      return out;
    } finally {
      tc.stopSpan(false);
    }
  }
}
