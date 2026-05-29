package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a map with a fixed set of keys, each drawn from its own generator. Basic (one engine
 * call) when every field generator is basic — the engine speaks a {@code tuple} of the field values
 * in key order, reconstructed client-side into a map. Otherwise composite: each field is drawn in
 * order inside a FIXED_DICT span.
 */
final class FixedDictGenerator
    implements Generator<Map<String, Object>>, MaybeBasic<Map<String, Object>> {
  private final List<String> keys;
  private final List<Generator<?>> gens;

  FixedDictGenerator(Map<String, Generator<?>> fields) {
    this.keys = new ArrayList<>(fields.keySet());
    this.gens = new ArrayList<>(fields.values());
  }

  @Override
  public BasicGenerator<Map<String, Object>> asBasic() {
    List<BasicGenerator<?>> basics = new ArrayList<>(gens.size());
    CBORObject schemas = CBORObject.NewArray();
    for (Generator<?> g : gens) {
      BasicGenerator<?> b = Gen.asBasic(g);
      if (b == null) {
        return null;
      }
      basics.add(b);
      schemas.Add(b.schema);
    }
    CBORObject schema = CBORObject.NewMap().Add("type", "tuple").Add("elements", schemas);
    return new BasicGenerator<>(
        schema,
        raw -> {
          List<Object> rawList = Cbor.asList(raw);
          Map<String, Object> out = new LinkedHashMap<>();
          for (int i = 0; i < keys.size(); i++) {
            out.put(keys.get(i), basics.get(i).parseRaw(rawList.get(i)));
          }
          return out;
        });
  }

  @Override
  public Map<String, Object> generate(TestCase tc) {
    BasicGenerator<Map<String, Object>> basic = asBasic();
    if (basic != null) {
      return basic.generate(tc);
    }
    tc.startSpan(Abi.LABEL_FIXED_DICT);
    try {
      Map<String, Object> out = new LinkedHashMap<>();
      for (int i = 0; i < keys.size(); i++) {
        out.put(keys.get(i), gens.get(i).generate(tc));
      }
      return out;
    } finally {
      tc.stopSpan(false);
    }
  }
}
