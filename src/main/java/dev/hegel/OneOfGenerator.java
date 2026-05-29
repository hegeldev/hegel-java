package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses among alternative generators of the same type.
 *
 * <p>Basic (one engine call) when every alternative is basic: the engine returns {@code [index,
 * value]} and the chosen alternative's parse is applied. Otherwise composite: an index is drawn and
 * the selected alternative is generated inside a ONE_OF span.
 */
final class OneOfGenerator<T> implements Generator<T>, MaybeBasic<T> {
  private final List<Generator<T>> options;

  OneOfGenerator(List<Generator<T>> options) {
    if (options.isEmpty()) {
      throw new IllegalArgumentException("oneOf requires at least one generator");
    }
    this.options = List.copyOf(options);
  }

  @Override
  public BasicGenerator<T> asBasic() {
    List<BasicGenerator<T>> basics = new ArrayList<>(options.size());
    CBORObject schemas = CBORObject.NewArray();
    for (Generator<T> g : options) {
      BasicGenerator<T> b = Gen.asBasic(g);
      if (b == null) {
        return null;
      }
      basics.add(b);
      schemas.Add(b.schema);
    }
    CBORObject schema = CBORObject.NewMap().Add("type", "one_of").Add("generators", schemas);
    return new BasicGenerator<>(
        schema,
        raw -> {
          List<Object> arr = Cbor.asList(raw);
          int index = Cbor.asIndex(arr.get(0));
          return basics.get(index).parseRaw(arr.get(1));
        });
  }

  @Override
  public T generate(TestCase tc) {
    BasicGenerator<T> basic = asBasic();
    if (basic != null) {
      return basic.generate(tc);
    }
    tc.startSpan(Abi.LABEL_ONE_OF);
    try {
      int index = Generators.integers(0, options.size() - 1).generate(tc);
      return options.get(index).generate(tc);
    } finally {
      tc.stopSpan(false);
    }
  }
}
