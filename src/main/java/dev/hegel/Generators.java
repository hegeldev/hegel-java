package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  public static IntegerGenerator integers() {
    return new IntegerGenerator(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Generates {@code int} values in {@code [min, max]} (inclusive). Equivalent to {@code
   * integers().min(min).max(max)}.
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (inclusive)
   * @return an integer generator
   */
  public static IntegerGenerator integers(int min, int max) {
    return new IntegerGenerator(min, max);
  }

  /**
   * Generates {@code long} values across the full {@code long} range.
   *
   * @return a long generator
   */
  public static LongGenerator longs() {
    return new LongGenerator(Long.MIN_VALUE, Long.MAX_VALUE);
  }

  /**
   * Generates {@code long} values in {@code [min, max]} (inclusive). Equivalent to {@code
   * longs().min(min).max(max)}.
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (inclusive)
   * @return a long generator
   */
  public static LongGenerator longs(long min, long max) {
    return new LongGenerator(min, max);
  }

  // --- floats & doubles ---

  /**
   * Generates 32-bit {@code float} values. Configure with the fluent methods on {@link
   * FloatGenerator}.
   *
   * @return a float generator
   */
  public static FloatGenerator floats() {
    return new FloatGenerator(null, null, null, null, false, false);
  }

  /**
   * Generates 64-bit {@code double} values. Configure with the fluent methods on {@link
   * DoubleGenerator}.
   *
   * @return a double generator
   */
  public static DoubleGenerator doubles() {
    return new DoubleGenerator(null, null, null, null, false, false);
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
  public static BinaryGenerator binary() {
    return new BinaryGenerator(0, Abi.UNBOUNDED);
  }

  /**
   * Generates byte arrays with length in {@code [minSize, maxSize]} ({@code maxSize < 0} =
   * unbounded). Equivalent to {@code binary().minSize(minSize).maxSize(maxSize)} (with a negative
   * {@code maxSize} meaning unbounded).
   *
   * @param minSize minimum length (inclusive)
   * @param maxSize maximum length (inclusive), or negative for unbounded
   * @return a binary generator
   */
  public static BinaryGenerator binary(int minSize, int maxSize) {
    return new BinaryGenerator(minSize, maxSize < 0 ? Abi.UNBOUNDED : maxSize);
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
  public static <T> ListGenerator<T> lists(Generator<T> element) {
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
  public static <T> ListGenerator<T> lists(Generator<T> element, int minSize, int maxSize) {
    return new ListGenerator<>(element, minSize, maxSize);
  }

  /**
   * Generates sets of distinct elements.
   *
   * @param element the element generator
   * @param <T> the element type
   * @return a set generator
   */
  public static <T> SetGenerator<T> sets(Generator<T> element) {
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
  public static <T> SetGenerator<T> sets(Generator<T> element, int minSize, int maxSize) {
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
  public static <K, V> MapGenerator<K, V> maps(Generator<K> keys, Generator<V> values) {
    return new MapGenerator<>(keys, values, 0, Abi.UNBOUNDED);
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
  public static <K, V> MapGenerator<K, V> maps(
      Generator<K> keys, Generator<V> values, int minSize, int maxSize) {
    return new MapGenerator<>(keys, values, minSize, maxSize);
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

  /**
   * Creates a forward reference for building self-recursive or mutually recursive generators. Pass
   * the returned {@link Deferred} into other generators, then call {@link Deferred#set} once with
   * the real implementation.
   *
   * @param <T> the value type
   * @return a deferred generator reference
   */
  public static <T> Deferred<T> deferred() {
    return new Deferred<>();
  }

  // --- type-directed derivation ---

  /**
   * Derive a generator for {@code type} by reflection.
   *
   * <p>Supports scalar types ({@code int}, {@code long}, {@code boolean}, {@code float}, {@code
   * double}, {@code String}, {@code byte[]} and their wrappers), enums, records (recursively), and
   * {@code List}, {@code Set}, {@code Optional} and {@code Map} of supported element types.
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
   * @return a generator of {@link LocalDate} values (the engine's {@code YYYY-MM-DD} output)
   */
  public static Generator<LocalDate> dates() {
    return new BasicGenerator<>(
        CBORObject.NewMap().Add("type", "date"), raw -> LocalDate.parse(Cbor.asString(raw)));
  }

  /**
   * @return a generator of {@link LocalTime} values (the engine's {@code HH:MM:SS[.ffffff]} output)
   */
  public static Generator<LocalTime> times() {
    return new BasicGenerator<>(
        CBORObject.NewMap().Add("type", "time"), raw -> LocalTime.parse(Cbor.asString(raw)));
  }

  /**
   * Generates {@link java.time.LocalDateTime} values (the engine's offset-free {@code
   * YYYY-MM-DDTHH:MM:SS[.ffffff]} output). Call {@link DateTimeGenerator#timezones} to produce
   * offset-aware {@link java.time.OffsetDateTime} values instead.
   *
   * @return a datetime generator
   */
  public static DateTimeGenerator datetimes() {
    return new DateTimeGenerator();
  }

  /**
   * Generates {@link ZoneOffset} values (fixed UTC offsets). Configure with the fluent methods on
   * {@link ZoneOffsetGenerator}. Pair with {@link DateTimeGenerator#timezones} to build
   * offset-aware datetimes.
   *
   * @return a zone-offset generator
   */
  public static ZoneOffsetGenerator zoneOffsets() {
    return new ZoneOffsetGenerator(
        ZoneOffset.MIN.getTotalSeconds(), ZoneOffset.MAX.getTotalSeconds());
  }

  /**
   * Generates {@link Duration} values across the representable nanosecond range. Configure with the
   * fluent methods on {@link DurationGenerator}.
   *
   * @return a duration generator
   */
  public static DurationGenerator durations() {
    return new DurationGenerator(0, Long.MAX_VALUE);
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
