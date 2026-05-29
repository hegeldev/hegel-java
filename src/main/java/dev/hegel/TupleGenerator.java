package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates fixed-length heterogeneous tuples as {@code List<Object>}. Basic when every element
 * generator is basic; otherwise generates each element in order inside a TUPLE span.
 */
final class TupleGenerator implements Generator<List<Object>>, MaybeBasic<List<Object>> {
  private final List<Generator<?>> elements;

  TupleGenerator(List<Generator<?>> elements) {
    this.elements = List.copyOf(elements);
  }

  @Override
  public BasicGenerator<List<Object>> asBasic() {
    List<BasicGenerator<?>> basics = new ArrayList<>(elements.size());
    CBORObject schemas = CBORObject.NewArray();
    for (Generator<?> g : elements) {
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
          List<Object> out = new ArrayList<>(basics.size());
          for (int i = 0; i < basics.size(); i++) {
            out.add(basics.get(i).parseRaw(rawList.get(i)));
          }
          return out;
        });
  }

  @Override
  public List<Object> generate(TestCase tc) {
    BasicGenerator<List<Object>> basic = asBasic();
    if (basic != null) {
      return basic.generate(tc);
    }
    tc.startSpan(Abi.LABEL_TUPLE);
    try {
      List<Object> out = new ArrayList<>(elements.size());
      for (Generator<?> g : elements) {
        out.add(g.generate(tc));
      }
      return out;
    } finally {
      tc.stopSpan(false);
    }
  }
}
