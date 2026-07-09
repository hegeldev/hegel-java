package dev.hegel;

import dev.hegel.generators.BinaryGenerator;
import dev.hegel.generators.BooleanGenerator;
import dev.hegel.generators.CompositeGenerator;
import dev.hegel.generators.ConstantGenerator;
import dev.hegel.generators.DateGenerator;
import dev.hegel.generators.DateTimeGenerator;
import dev.hegel.generators.Deferred;
import dev.hegel.generators.Derive;
import dev.hegel.generators.DomainGenerator;
import dev.hegel.generators.DoubleGenerator;
import dev.hegel.generators.DurationGenerator;
import dev.hegel.generators.EmailGenerator;
import dev.hegel.generators.FloatGenerator;
import dev.hegel.generators.IntegerGenerator;
import dev.hegel.generators.IpAddressGenerator;
import dev.hegel.generators.ListGenerator;
import dev.hegel.generators.LongGenerator;
import dev.hegel.generators.MapGenerator;
import dev.hegel.generators.OneOfGenerator;
import dev.hegel.generators.RecordGenerator;
import dev.hegel.generators.RegexGenerator;
import dev.hegel.generators.SampledFromGenerator;
import dev.hegel.generators.SetGenerator;
import dev.hegel.generators.TextGenerator;
import dev.hegel.generators.TimeGenerator;
import dev.hegel.generators.TupleGenerator;
import dev.hegel.generators.UrlGenerator;
import dev.hegel.generators.UuidGenerator;
import dev.hegel.generators.ZoneOffsetGenerator;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
     * Generates {@code long} values across the full {@code long} range.
     *
     * @return a long generator
     */
    public static LongGenerator longs() {
        return new LongGenerator(Long.MIN_VALUE, Long.MAX_VALUE);
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
        return new BooleanGenerator();
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
     * Generates strings of exactly one Unicode <em>codepoint</em>. A codepoint outside the Basic
     * Multilingual Plane occupies two UTF-16 {@code char}s, so the generated string can have {@code
     * String.length() == 2}; read the value with {@link String#codePointAt(int) codePointAt(0)}
     * rather than {@code charAt(0)}. (Surrogates are excluded by default, as for {@link #text()}.)
     *
     * <p>The result is an ordinary {@link TextGenerator} with {@code minSize(1).maxSize(1)} applied,
     * so calling further size methods (e.g. {@code minSize(0)}) replaces the one-codepoint contract.
     *
     * @return a one-codepoint text generator
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

    // --- selection ---

    /**
     * Always generates the same value, ignoring the engine's choice.
     *
     * @param value the constant value
     * @param <T> the value type
     * @return a constant generator
     */
    public static <T> Generator<T> just(T value) {
        return new ConstantGenerator<>(value);
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
        return new SampledFromGenerator<>(values);
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
     * Generates a typed pair, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @return a generator of {@link Tuple2}
     */
    @SuppressWarnings("unchecked")
    public static <A, B> Generator<Tuple2<A, B>> tuples(Generator<A> a, Generator<B> b) {
        return new TupleGenerator<>(List.of(a, b), parts -> new Tuple2<>((A) parts.get(0), (B) parts.get(1)));
    }

    /**
     * Generates a typed triple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @return a generator of {@link Tuple3}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C> Generator<Tuple3<A, B, C>> tuples(Generator<A> a, Generator<B> b, Generator<C> c) {
        return new TupleGenerator<>(
                List.of(a, b, c), parts -> new Tuple3<>((A) parts.get(0), (B) parts.get(1), (C) parts.get(2)));
    }

    /**
     * Generates a typed 4-tuple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param d the fourth-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @param <D> the fourth-element type
     * @return a generator of {@link Tuple4}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C, D> Generator<Tuple4<A, B, C, D>> tuples(
            Generator<A> a, Generator<B> b, Generator<C> c, Generator<D> d) {
        return new TupleGenerator<>(
                List.of(a, b, c, d),
                parts -> new Tuple4<>((A) parts.get(0), (B) parts.get(1), (C) parts.get(2), (D) parts.get(3)));
    }

    /**
     * Generates a typed 5-tuple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param d the fourth-element generator
     * @param e the fifth-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @param <D> the fourth-element type
     * @param <E> the fifth-element type
     * @return a generator of {@link Tuple5}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C, D, E> Generator<Tuple5<A, B, C, D, E>> tuples(
            Generator<A> a, Generator<B> b, Generator<C> c, Generator<D> d, Generator<E> e) {
        return new TupleGenerator<>(
                List.of(a, b, c, d, e),
                parts -> new Tuple5<>(
                        (A) parts.get(0), (B) parts.get(1), (C) parts.get(2), (D) parts.get(3), (E) parts.get(4)));
    }

    /**
     * Generates a typed 6-tuple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param d the fourth-element generator
     * @param e the fifth-element generator
     * @param f the sixth-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @param <D> the fourth-element type
     * @param <E> the fifth-element type
     * @param <F> the sixth-element type
     * @return a generator of {@link Tuple6}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C, D, E, F> Generator<Tuple6<A, B, C, D, E, F>> tuples(
            Generator<A> a, Generator<B> b, Generator<C> c, Generator<D> d, Generator<E> e, Generator<F> f) {
        return new TupleGenerator<>(
                List.of(a, b, c, d, e, f),
                parts -> new Tuple6<>(
                        (A) parts.get(0), (B) parts.get(1), (C) parts.get(2), (D) parts.get(3), (E) parts.get(4), (F)
                                parts.get(5)));
    }

    /**
     * Generates a typed 7-tuple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param d the fourth-element generator
     * @param e the fifth-element generator
     * @param f the sixth-element generator
     * @param g the seventh-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @param <D> the fourth-element type
     * @param <E> the fifth-element type
     * @param <F> the sixth-element type
     * @param <G> the seventh-element type
     * @return a generator of {@link Tuple7}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C, D, E, F, G> Generator<Tuple7<A, B, C, D, E, F, G>> tuples(
            Generator<A> a,
            Generator<B> b,
            Generator<C> c,
            Generator<D> d,
            Generator<E> e,
            Generator<F> f,
            Generator<G> g) {
        return new TupleGenerator<>(
                List.of(a, b, c, d, e, f, g),
                parts -> new Tuple7<>(
                        (A) parts.get(0),
                        (B) parts.get(1),
                        (C) parts.get(2),
                        (D) parts.get(3),
                        (E) parts.get(4),
                        (F) parts.get(5),
                        (G) parts.get(6)));
    }

    /**
     * Generates a typed 8-tuple, drawing each element from its generator.
     *
     * @param a the first-element generator
     * @param b the second-element generator
     * @param c the third-element generator
     * @param d the fourth-element generator
     * @param e the fifth-element generator
     * @param f the sixth-element generator
     * @param g the seventh-element generator
     * @param h the eighth-element generator
     * @param <A> the first-element type
     * @param <B> the second-element type
     * @param <C> the third-element type
     * @param <D> the fourth-element type
     * @param <E> the fifth-element type
     * @param <F> the sixth-element type
     * @param <G> the seventh-element type
     * @param <H> the eighth-element type
     * @return a generator of {@link Tuple8}
     */
    @SuppressWarnings("unchecked")
    public static <A, B, C, D, E, F, G, H> Generator<Tuple8<A, B, C, D, E, F, G, H>> tuples(
            Generator<A> a,
            Generator<B> b,
            Generator<C> c,
            Generator<D> d,
            Generator<E> e,
            Generator<F> f,
            Generator<G> g,
            Generator<H> h) {
        return new TupleGenerator<>(
                List.of(a, b, c, d, e, f, g, h),
                parts -> new Tuple8<>(
                        (A) parts.get(0),
                        (B) parts.get(1),
                        (C) parts.get(2),
                        (D) parts.get(3),
                        (E) parts.get(4),
                        (F) parts.get(5),
                        (G) parts.get(6),
                        (H) parts.get(7)));
    }

    /**
     * Generates fixed-length heterogeneous tuples as {@code List<Object>}. This variadic form is the
     * fallback for arities above {@link Tuple8 eight}; for two to eight elements prefer the typed
     * {@code tuples} overloads, which return a {@code TupleN} record with statically-typed accessors.
     *
     * @param generators one generator per position
     * @return a tuple generator
     */
    public static Generator<List<Object>> tuples(Generator<?>... generators) {
        return new TupleGenerator<>(List.of(generators), parts -> parts);
    }

    // --- imperative composition ---

    /**
     * Builds a value imperatively from multiple draws on the test case.
     *
     * @param body draws from the test case and returns a value
     * @param <T> the value type
     * @return a composite generator
     */
    public static <T> Generator<T> composite(Function<TestCase, T> body) {
        return new CompositeGenerator<>(body);
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
     * double}, {@code String}, {@code byte[]}, {@link UUID}, and their wrappers), enums, records
     * (recursively), and {@code List}, {@code Set}, {@code Optional} and {@code Map} of supported
     * element types.
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

    // --- string-format and date/time generators ---

    /**
     * @return a generator of syntactically valid email addresses
     */
    public static Generator<String> emails() {
        return new EmailGenerator();
    }

    /**
     * @return a generator of syntactically valid URLs
     */
    public static Generator<String> urls() {
        return new UrlGenerator();
    }

    /**
     * @return a generator of syntactically valid domain names
     */
    public static Generator<String> domains() {
        return new DomainGenerator();
    }

    /**
     * @return a generator of IP address strings (a mix of IPv4 and IPv6 by default; restrict with
     *     {@link IpAddressGenerator#v4()} / {@link IpAddressGenerator#v6()})
     */
    public static IpAddressGenerator ipAddresses() {
        return new IpAddressGenerator(null);
    }

    /**
     * Generates {@link UUID} values; see {@link UuidGenerator} for configuration capabilities.
     *
     * @return a UUID generator
     */
    public static UuidGenerator uuids() {
        return new UuidGenerator();
    }

    /**
     * @return a generator of {@link LocalDate} values (the engine's {@code YYYY-MM-DD} output)
     */
    public static Generator<LocalDate> dates() {
        return new DateGenerator();
    }

    /**
     * @return a generator of {@link LocalTime} values (the engine's {@code HH:MM:SS[.ffffff]} output)
     */
    public static Generator<LocalTime> times() {
        return new TimeGenerator();
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
     * {@link ZoneOffsetGenerator}. Pair with {@link DateTimeGenerator#offsets} to build offset-aware
     * datetimes.
     *
     * @return a zone-offset generator
     */
    public static ZoneOffsetGenerator zoneOffsets() {
        return new ZoneOffsetGenerator(ZoneOffset.MIN.getTotalSeconds(), ZoneOffset.MAX.getTotalSeconds());
    }

    /**
     * Generates {@link ZoneId} values spanning the full range of region zones the JVM supports
     * ({@link ZoneId#getAvailableZoneIds()}). Pair with {@link DateTimeGenerator#timezones} to build
     * DST-aware datetimes.
     *
     * @return a zone-id generator
     */
    public static Generator<ZoneId> zoneIds() {
        return sampledFrom(AVAILABLE_ZONE_IDS);
    }

    private static final List<ZoneId> AVAILABLE_ZONE_IDS =
            ZoneId.getAvailableZoneIds().stream().sorted().map(ZoneId::of).toList();

    /**
     * Generates {@link Duration} values across the full signed nanosecond range — {@code
     * [Long.MIN_VALUE, Long.MAX_VALUE]} nanoseconds, about ±292 years — including negative
     * durations. Configure with the fluent methods on {@link DurationGenerator}.
     *
     * @return a duration generator
     */
    public static DurationGenerator durations() {
        return new DurationGenerator(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * Generates strings matching a (Python-compatible) regular expression. By default the entire
     * string matches the pattern; use {@link RegexGenerator#fullmatch(boolean) fullmatch(false)}
     * to generate strings that merely <em>contain</em> a match, with arbitrary padding on either
     * side (the unanchored semantics of Hypothesis's {@code from_regex}).
     *
     * <p>The pattern uses the engine's Python {@code re} dialect, which differs from {@link
     * java.util.regex.Pattern} in some constructs.
     *
     * @param pattern the regex pattern (Python {@code re} dialect)
     * @return a regex generator
     */
    public static RegexGenerator fromRegex(String pattern) {
        return new RegexGenerator(pattern, true);
    }
}
