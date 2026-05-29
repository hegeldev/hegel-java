package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.function.Function;

/**
 * A generator describable by a CBOR schema plus a client-side parse function.
 *
 * <p>The engine generates a raw value from {@link #schema} in one call; {@link #parse} converts it
 * to {@code T}. {@link #mapBasic} composes a new parse over the same schema, so chains of {@code
 * map} stay on the single-draw path and shrink as well as the original.
 */
final class BasicGenerator<T> implements Generator<T>, MaybeBasic<T> {
  final CBORObject schema;
  final Function<Object, T> parse;

  BasicGenerator(CBORObject schema, Function<Object, T> parse) {
    this.schema = schema;
    this.parse = parse;
  }

  @Override
  public T generate(TestCase tc) {
    return parse.apply(tc.generateFromSchema(schema));
  }

  @Override
  public BasicGenerator<T> asBasic() {
    return this;
  }

  <U> BasicGenerator<U> mapBasic(Function<? super T, ? extends U> f) {
    Function<Object, T> oldParse = parse;
    return new BasicGenerator<>(schema, raw -> f.apply(oldParse.apply(raw)));
  }

  T parseRaw(Object raw) {
    return parse.apply(raw);
  }
}
