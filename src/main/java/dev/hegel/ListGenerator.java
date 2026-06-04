package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates lists. Basic (single engine call) when the element generator is basic; otherwise drives
 * the engine's collection API element by element. The element generator opens its own spans, so no
 * per-element span is needed here (matching the engine's canonical client).
 *
 * <p>The length range defaults to any size; narrow it with the fluent {@link #minSize(int)} /
 * {@link #maxSize(int)} methods. {@code lists(e, a, b)} is exactly {@code
 * lists(e).minSize(a).maxSize(b)}.
 */
public final class ListGenerator<T> implements Generator<List<T>>, MaybeBasic<List<T>> {
  private final Generator<T> element;
  private final long minSize;
  private final long maxSize;

  ListGenerator(Generator<T> element, long minSize, long maxSize) {
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

  @Override
  public BasicGenerator<List<T>> asBasic() {
    BasicGenerator<T> e = Gen.asBasic(element);
    if (e == null) {
      return null;
    }
    CBORObject schema =
        CBORObject.NewMap()
            .Add("type", "list")
            .Add("unique", false)
            .Add("elements", e.schema)
            .Add("min_size", minSize);
    if (maxSize != Abi.UNBOUNDED) {
      schema.Add("max_size", maxSize);
    }
    return new BasicGenerator<>(
        schema,
        raw -> {
          List<Object> rawList = Cbor.asList(raw);
          List<T> out = new ArrayList<>(rawList.size());
          for (Object o : rawList) {
            out.add(e.parseRaw(o));
          }
          return out;
        });
  }

  @Override
  public List<T> generate(TestCase tc) {
    BasicGenerator<List<T>> basic = asBasic();
    if (basic != null) {
      return basic.generate(tc);
    }
    tc.startSpan(Abi.LABEL_LIST);
    try {
      long id = tc.newCollection(minSize, maxSize);
      List<T> out = new ArrayList<>();
      while (tc.collectionMore(id)) {
        out.add(element.generate(tc));
      }
      return out;
    } finally {
      tc.stopSpan(false);
    }
  }
}
