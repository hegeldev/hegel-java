/// Property-based testing for Java, powered by the Hegel engine.
///
/// Instead of writing tests with hand-picked example inputs, you describe a *property* that should
/// hold for all inputs and let Hegel generate inputs to try to falsify it. When it finds a failing
/// input it automatically **shrinks** it to a minimal counterexample.
///
/// Hegel requires **Java 22+** (it uses the Foreign Function & Memory API) and
/// `--enable-native-access=ALL-UNNAMED` on the test JVM. The native engine, `libhegel`, is bundled
/// inside the jar for every supported platform and loaded automatically — nothing else to install.
///
/// ## Your first test
///
/// Use JUnit 5 as the runner. Annotate a method with {@link dev.hegel.HegelTest @HegelTest} and
/// give it a {@link dev.hegel.TestCase} parameter:
///
/// ```java
/// import static dev.hegel.Generators.integers;
/// import static org.junit.jupiter.api.Assertions.assertEquals;
///
/// import dev.hegel.HegelTest;
/// import dev.hegel.TestCase;
///
/// class FirstTest {
///   @HegelTest
///   void integerSelfEquality(TestCase tc) {
///     int n = tc.draw(integers());
///     assertEquals(n, n); // an integer always equals itself
///   }
/// }
/// ```
///
/// `@HegelTest` runs the method many times (100 by default). Each run receives a
/// {@link dev.hegel.TestCase}, whose {@link dev.hegel.TestCase#draw(dev.hegel.Generator) draw}
/// method produces a value from a generator.
///
/// When you need a setting that can't be a compile-time constant, or want to run a property outside
/// a JUnit method, drive it programmatically with
/// {@link dev.hegel.Hegel#test(java.util.function.Consumer)} — the body comes first, with optional
/// {@link dev.hegel.Settings}:
///
/// ```java
/// import static dev.hegel.Generators.integers;
/// import static org.junit.jupiter.api.Assertions.assertEquals;
///
/// import dev.hegel.Hegel;
/// import org.junit.jupiter.api.Test;
///
/// class CommutativityTest {
///   @Test
///   void additionCommutes() {
///     Hegel.test(tc -> {
///       int x = tc.draw(integers());
///       int y = tc.draw(integers());
///       assertEquals(x + y, y + x);
///     });
///   }
/// }
/// ```
///
/// ## Understanding test output
///
/// When a property fails, Hegel replays the minimal counterexample and prints each top-level
/// `draw` as an assignment:
///
/// ```text
/// draw_1 = 50;
/// ```
///
/// Pass a label — `tc.draw(integers(), "n")` — to name the variable instead:
///
/// ```text
/// n = 50;
/// ```
///
/// ## Generators
///
/// {@link dev.hegel.Generators} provides a rich set of generators. Primitives include `integers`,
/// `longs`, `floats` (32-bit) and `doubles` (64-bit), `booleans`, `text`, and `binary`; collections
/// include `lists`, `sets`, and `maps`; and there are `tuples`, `oneOf`, `optional`, `sampledFrom`,
/// `just`, `durations` (`java.time.Duration`), and the temporal generators `dates`, `times`, and
/// `datetimes` (which produce `java.time.LocalDate`/`LocalTime`/`LocalDateTime`), plus format
/// generators (`emails`, `urls`, `ipAddresses`, `uuids`, `fromRegex`, …).
///
/// For zone-aware datetimes, attach a timezone to a `datetimes()` generator:
///
/// - {@link dev.hegel.generators.DateTimeGenerator#timezones timezones}: `datetimes().timezones(zoneIds())`
///   produces DST-aware `java.time.ZonedDateTime` values over the full range of zones the JVM
///   supports (see {@link dev.hegel.Generators#zoneIds()}); pin one with
///   `datetimes().timezones(just(ZoneId.of("Europe/London")))`.
/// - {@link dev.hegel.generators.DateTimeGenerator#offsets offsets}: `datetimes().offsets(zoneOffsets())`
///   produces fixed-offset `java.time.OffsetDateTime` values (see
///   {@link dev.hegel.Generators#zoneOffsets()}).
///
/// The bound- and size-bearing generators are fluent builders that *are* the generator:
///
/// ```java
/// tc.draw(integers().min(0).max(100));       // bounded ints
/// tc.draw(text().minSize(1).maxSize(10));    // short strings
/// tc.draw(doubles().min(0).max(1));          // a probability (64-bit)
/// tc.draw(floats().min(0).max(1));           // a 32-bit float in [0, 1]
/// tc.draw(lists(integers()).minSize(1).maxSize(5));          // 1–5 element lists
/// ```
///
/// ## Combinators
///
/// Build new generators from existing ones (see {@link dev.hegel.Generator}):
///
/// - {@link dev.hegel.Generator#map map} transforms each value (and keeps the efficient
///   single-draw path when possible):
///   ```java
///   Generator<Integer> evens = integers().min(0).max(50).map(x -> x * 2);
///   ```
/// - {@link dev.hegel.Generator#filter filter} keeps values matching a predicate (prefer
///   constraining over filtering when you can):
///   ```java
///   Generator<Integer> big = integers().filter(x -> x > 1000);
///   ```
/// - {@link dev.hegel.Generator#flatMap flatMap} makes one draw depend on another:
///   ```java
///   Generator<List<Boolean>> sized = integers().min(0).max(10).flatMap(n -> lists(booleans()).minSize(n).maxSize(n));
///   ```
/// - {@link dev.hegel.Generators#composite composite} builds a value imperatively from several
///   draws:
///   ```java
///   Generator<int[]> pair = Generators.composite(tc -> new int[] {
///       tc.draw(integers()), tc.draw(integers())
///   });
///   ```
///
/// ## Recursive generators
///
/// {@link dev.hegel.Generators#deferred() deferred} creates a forward reference so a generator can
/// refer to itself, enabling self-recursive (and mutually recursive) data such as trees:
///
/// ```java
/// record Tree(Integer leaf, Tree left, Tree right) {} // leaf != null XOR children != null
///
/// Deferred<Tree> tree = Generators.deferred();
/// Generator<Tree> leaf = integers().map(n -> new Tree(n, null, null));
/// Generator<Tree> branch =
///     tuples(tree, tree).map(t -> new Tree(null, t.value1(), t.value2()));
/// tree.set(oneOf(leaf, branch)); // wire up the self-reference
/// Tree t = tc.draw(tree);
/// ```
///
/// The engine's size control keeps generated structures finite. Drawing before
/// {@link dev.hegel.generators.Deferred#set set} is called fails.
///
/// ## Control functions
///
/// Inside a test body you can steer the engine via {@link dev.hegel.TestCase}:
///
/// - {@link dev.hegel.TestCase#assume(boolean) assume} discards the current input if a precondition
///   does not hold.
/// - {@link dev.hegel.TestCase#note(String) note} records a message shown only on the final replay
///   of a failing case.
/// - {@link dev.hegel.TestCase#target(double, String) target} reports a score so the search can
///   hill-climb toward interesting inputs.
///
/// ```java
/// @HegelTest
/// void divisionRoundTrips(TestCase tc) {
///   int x = tc.draw(integers().min(1).max(1000));
///   int y = tc.draw(integers().min(1).max(1000));
///   tc.assume(y != 0);
///   tc.note("testing " + x + " * " + y + " / " + y);
///   assertEquals(x, (x * y) / y);
/// }
/// ```
///
/// ## Settings
///
/// Configure a run by passing a {@link dev.hegel.Settings} value (built with {@code new Settings()}
/// and the fluent setters) to
/// {@link dev.hegel.Hegel#test(java.util.function.Consumer, dev.hegel.Settings)}, or with attributes
/// on {@link dev.hegel.HegelTest @HegelTest}:
///
/// ```java
/// Hegel.test(
///     tc -> { /* ... */ },
///     new Settings()
///         .testCases(500)    // run more inputs
///         .seed(42));        // reproducible run
///
/// @HegelTest(testCases = 1000, seed = 42)
/// void thorough(TestCase tc) { /* ... */ }
/// ```
///
/// Other settings include `derandomize`, {@link dev.hegel.Settings#database(dev.hegel.Database)
/// database}, `suppressHealthCheck`,
/// `verbosity`, `mode`, and {@link dev.hegel.Settings#phases(dev.hegel.Phase...) phases}.
/// In CI (detected automatically) runs default to deterministic and the example database is
/// disabled. If a health check fires — for example, your generators reject almost every input —
/// Hegel aborts the run and throws {@link dev.hegel.HealthCheckFailure} (distinct from a property's
/// own failure); pass the relevant {@link dev.hegel.HealthCheck} to `suppressHealthCheck` if the
/// behaviour is intentional.
///
/// ## Deriving generators from types
///
/// Hegel can build a generator for a record, enum, or supported scalar/collection type by
/// reflection via {@link dev.hegel.Generators#forType(Class) forType} and
/// {@link dev.hegel.Generators#records(Class) records}:
///
/// ```java
/// record Point(int x, int y) {}
/// enum Color { RED, GREEN, BLUE }
///
/// @HegelTest
/// void derived(TestCase tc) {
///   Point p = tc.draw(Generators.forType(Point.class));
///   Color c = tc.draw(Generators.forType(Color.class));
///   // Override a single component:
///   Point bounded = tc.draw(Generators.records(Point.class).with("x", integers().min(0).max(9)));
/// }
/// ```
package dev.hegel;
