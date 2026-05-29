package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Factory methods for all built-in generators.
 *
 * <p>Static-import this class in test code: {@code import static dev.hegel.Generators.*;}
 */
public final class Generators {
  private Generators() {}

  // --- integers ---

  /**
   * Generates {@code int} values across the full {@code int} range.
   *
   * @return an integer generator
   */
  public static Generator<Integer> integers() {
    return integers(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Generates {@code int} values in {@code [min, max]} (inclusive).
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (inclusive)
   * @return an integer generator
   */
  public static Generator<Integer> integers(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("integers: min (" + min + ") > max (" + max + ")");
    }
    CBORObject schema =
        CBORObject.NewMap().Add("type", "integer").Add("min_value", min).Add("max_value", max);
    return new BasicGenerator<>(schema, Cbor::asIndex);
  }

  /**
   * Generates {@code long} values across the full {@code long} range.
   *
   * @return a long generator
   */
  public static Generator<Long> longs() {
    return longs(Long.MIN_VALUE, Long.MAX_VALUE);
  }

  /**
   * Generates {@code long} values in {@code [min, max]} (inclusive).
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (inclusive)
   * @return a long generator
   */
  public static Generator<Long> longs(long min, long max) {
    if (min > max) {
      throw new IllegalArgumentException("longs: min (" + min + ") > max (" + max + ")");
    }
    CBORObject schema =
        CBORObject.NewMap().Add("type", "integer").Add("min_value", min).Add("max_value", max);
    return new BasicGenerator<>(schema, Cbor::asLong);
  }

  // --- floats ---

  /**
   * Generates {@code double} values. Configure with the fluent methods on {@link FloatGenerator}.
   *
   * @return a float generator
   */
  public static FloatGenerator floats() {
    return new FloatGenerator(null, null, null, null, false, false);
  }

  // --- booleans ---

  /**
   * Generates {@code true} or {@code false}.
   *
   * @return a boolean generator
   */
  public static Generator<Boolean> booleans() {
    return new BasicGenerator<>(CBORObject.NewMap().Add("type", "boolean"), Cbor::asBoolean);
  }

  // --- strings & bytes ---

  /**
   * Generates strings. Configure with the fluent methods on {@link TextGenerator}.
   *
   * @return a text generator
   */
  public static TextGenerator text() {
    return new TextGenerator(0, Abi.UNBOUNDED, null, null, null, null, null, null);
  }

  /**
   * Generates single-character strings.
   *
   * @return a one-character text generator
   */
  public static TextGenerator characters() {
    return text().minSize(1).maxSize(1);
  }

  /**
   * Generates byte arrays of any length.
   *
   * @return a binary generator
   */
  public static Generator<byte[]> binary() {
    return binary(0, -1);
  }

  /**
   * Generates byte arrays with length in {@code [minSize, maxSize]} ({@code maxSize < 0} =
   * unbounded).
   *
   * @param minSize minimum length (inclusive)
   * @param maxSize maximum length (inclusive), or negative for unbounded
   * @return a binary generator
   */
  public static Generator<byte[]> binary(int minSize, int maxSize) {
    long max = maxSize < 0 ? Abi.UNBOUNDED : maxSize;
    Sizes.validate(minSize, max, "binary");
    CBORObject schema = CBORObject.NewMap().Add("type", "binary").Add("min_size", minSize);
    if (max != Abi.UNBOUNDED) {
      schema.Add("max_size", max);
    }
    return new BasicGenerator<>(schema, Cbor::asBytes);
  }

  // --- selection ---

  /**
   * Always generates the same value, ignoring the engine's choice.
   *
   * @param value the constant value
   * @param <T> the value type
   * @return a constant generator
   */
  public static <T> Generator<T> just(T value) {
    CBORObject schema = CBORObject.NewMap().Add("type", "constant").Add("value", CBORObject.Null);
    return new BasicGenerator<>(schema, raw -> value);
  }

  /**
   * Picks one of the given values (the first is the simplest for shrinking).
   *
   * @param values the candidate values
   * @param <T> the value type
   * @return a sampling generator
   */
  @SafeVarargs
  public static <T> Generator<T> sampledFrom(T... values) {
    return sampledFrom(List.of(values));
  }

  /**
   * Picks one of the given values (the first is the simplest for shrinking).
   *
   * @param values the candidate values
   * @param <T> the value type
   * @return a sampling generator
   */
  public static <T> Generator<T> sampledFrom(List<T> values) {
    if (values.isEmpty()) {
      throw new IllegalArgumentException("sampledFrom requires a non-empty collection");
    }
    List<T> copy = List.copyOf(values);
    CBORObject schema =
        CBORObject.NewMap()
            .Add("type", "integer")
            .Add("min_value", 0)
            .Add("max_value", copy.size() - 1);
    return new BasicGenerator<>(schema, raw -> copy.get(Cbor.asIndex(raw)));
  }

  /**
   * Chooses among alternative generators of a common type.
   *
   * @param options the alternative generators
   * @param <T> the value type
   * @return a one-of generator
   */
  @SafeVarargs
  @SuppressWarnings("unchecked")
  public static <T> Generator<T> oneOf(Generator<? extends T>... options) {
    List<Generator<T>> list = new ArrayList<>(options.length);
    for (Generator<? extends T> g : options) {
      list.add((Generator<T>) g);
    }
    return new OneOfGenerator<>(list);
  }

  /**
   * Generates an {@link Optional} that is empty or wraps a value from {@code generator}.
   *
   * @param generator the value generator
   * @param <T> the value type
   * @return an optional generator
   */
  public static <T> Generator<Optional<T>> optional(Generator<T> generator) {
    return oneOf(just(Optional.<T>empty()), generator.map(Optional::of));
  }

  // --- collections ---

  /**
   * Generates lists of any length with elements drawn from {@code element}.
   *
   * @param element the element generator
   * @param <T> the element type
   * @return a list generator
   */
  public static <T> Generator<List<T>> lists(Generator<T> element) {
    return new ListGenerator<>(element, 0, Abi.UNBOUNDED);
  }

  /**
   * Generates lists with length in {@code [minSize, maxSize]}.
   *
   * @param element the element generator
   * @param minSize minimum length (inclusive)
   * @param maxSize maximum length (inclusive)
   * @param <T> the element type
   * @return a list generator
   */
  public static <T> Generator<List<T>> lists(Generator<T> element, int minSize, int maxSize) {
    return new ListGenerator<>(element, minSize, maxSize);
  }

  /**
   * Generates sets of distinct elements.
   *
   * @param element the element generator
   * @param <T> the element type
   * @return a set generator
   */
  public static <T> Generator<Set<T>> sets(Generator<T> element) {
    return new SetGenerator<>(element, 0, Abi.UNBOUNDED);
  }

  /**
   * Generates sets with size in {@code [minSize, maxSize]}.
   *
   * @param element the element generator
   * @param minSize minimum size (inclusive)
   * @param maxSize maximum size (inclusive)
   * @param <T> the element type
   * @return a set generator
   */
  public static <T> Generator<Set<T>> sets(Generator<T> element, int minSize, int maxSize) {
    return new SetGenerator<>(element, minSize, maxSize);
  }

  /**
   * Generates maps from {@code keys} to {@code values}.
   *
   * @param keys the key generator
   * @param values the value generator
   * @param <K> the key type
   * @param <V> the value type
   * @return a map generator
   */
  public static <K, V> Generator<Map<K, V>> maps(Generator<K> keys, Generator<V> values) {
    return new DictGenerator<>(keys, values, 0, Abi.UNBOUNDED);
  }

  /**
   * Generates maps with entry count in {@code [minSize, maxSize]}.
   *
   * @param keys the key generator
   * @param values the value generator
   * @param minSize minimum entries (inclusive)
   * @param maxSize maximum entries (inclusive)
   * @param <K> the key type
   * @param <V> the value type
   * @return a map generator
   */
  public static <K, V> Generator<Map<K, V>> maps(
      Generator<K> keys, Generator<V> values, int minSize, int maxSize) {
    return new DictGenerator<>(keys, values, minSize, maxSize);
  }

  /**
   * Generates fixed-length heterogeneous tuples as {@code List<Object>}.
   *
   * @param generators one generator per position
   * @return a tuple generator
   */
  public static Generator<List<Object>> tuples(Generator<?>... generators) {
    return new TupleGenerator(List.of(generators));
  }

  // --- imperative composition ---

  /**
   * Builds a value imperatively from multiple draws on the test case.
   *
   * @param body draws from the test case and returns a value
   * @param <T> the value type
   * @return a composite generator
   */
  public static <T> Generator<T> compose(Function<TestCase, T> body) {
    return new Gen.Composite<>(Abi.LABEL_COMPOSITE, body);
  }

  // --- type-directed derivation ---

  /**
   * Derive a generator for {@code type} by reflection.
   *
   * <p>Supports scalar types ({@code int}, {@code long}, {@code boolean}, {@code double}, {@code
   * String}, {@code byte[]} and their wrappers), enums, records (recursively), and {@code List},
   * {@code Set}, {@code Optional} and {@code Map} of supported element types.
   *
   * @param type the type to derive a generator for
   * @param <T> the type
   * @return a generator producing instances of {@code type}
   */
  @SuppressWarnings("unchecked")
  public static <T> Generator<T> forType(Class<T> type) {
    return (Generator<T>) Derive.fromType(type);
  }

  /**
   * Begin deriving a generator for a record type, allowing per-component overrides via {@link
   * RecordGenerator#with}.
   *
   * @param type the record type
   * @param <T> the record type
   * @return a record generator
   */
  public static <T> RecordGenerator<T> records(Class<T> type) {
    return new RecordGenerator<>(type, java.util.Map.of());
  }

  // --- format generators (all produce strings) ---

  /**
   * @return a generator of syntactically valid email addresses
   */
  public static Generator<String> emails() {
    return format("email");
  }

  /**
   * @return a generator of syntactically valid URLs
   */
  public static Generator<String> urls() {
    return format("url");
  }

  /**
   * @return a generator of syntactically valid domain names
   */
  public static Generator<String> domains() {
    return format("domain");
  }

  /**
   * @return a generator of IPv4 address strings
   */
  public static Generator<String> ipv4() {
    return ipAddress(4);
  }

  /**
   * @return a generator of IPv6 address strings
   */
  public static Generator<String> ipv6() {
    return ipAddress(6);
  }

  /**
   * @return a generator of UUID strings
   */
  public static Generator<String> uuids() {
    return format("uuid");
  }

  private static Generator<String> ipAddress(int version) {
    CBORObject schema = CBORObject.NewMap().Add("type", "ip_address").Add("version", version);
    return new BasicGenerator<>(schema, Cbor::asString);
  }

  /**
   * @return a generator of ISO-8601 date strings
   */
  public static Generator<String> dates() {
    return format("date");
  }

  /**
   * @return a generator of time-of-day strings
   */
  public static Generator<String> times() {
    return format("time");
  }

  /**
   * @return a generator of ISO-8601 datetime strings
   */
  public static Generator<String> datetimes() {
    return format("datetime");
  }

  /**
   * Generates strings matching a (Python-compatible) regular expression.
   *
   * @param pattern the regex pattern
   * @return a regex generator
   */
  public static Generator<String> fromRegex(String pattern) {
    CBORObject schema = CBORObject.NewMap().Add("type", "regex").Add("pattern", pattern);
    return new BasicGenerator<>(schema, Cbor::asString);
  }

  private static Generator<String> format(String type) {
    return new BasicGenerator<>(CBORObject.NewMap().Add("type", type), Cbor::asString);
  }
}
