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
/// You can also drive a property from any plain `@Test` with {@link dev.hegel.Hegel#check}:
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
///     Hegel.check(tc -> {
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
/// `just`, plus format generators (`emails`, `urls`, `ipv4`, `dates`, `fromRegex`, …).
///
/// The bound- and size-bearing generators are fluent builders that *are* the generator:
///
/// ```java
/// tc.draw(integers(0, 100));                 // bounded ints
/// tc.draw(integers().min(0).max(100));       // the same, written fluently
/// tc.draw(text().minSize(1).maxSize(10));    // short strings
/// tc.draw(doubles().min(0).max(1));          // a probability (64-bit)
/// tc.draw(floats().min(0).max(1));           // a 32-bit float in [0, 1]
/// tc.draw(lists(integers(), 1, 5));          // 1–5 element lists
/// ```
///
/// ## Combinators
///
/// Build new generators from existing ones (see {@link dev.hegel.Generator}):
///
/// - {@link dev.hegel.Generator#map map} transforms each value (and keeps the efficient
///   single-draw path when possible):
///   ```java
///   Generator<Integer> evens = integers(0, 50).map(x -> x * 2);
///   ```
/// - {@link dev.hegel.Generator#filter filter} keeps values matching a predicate (prefer
///   constraining over filtering when you can):
///   ```java
///   Generator<Integer> big = integers().filter(x -> x > 1000);
///   ```
/// - {@link dev.hegel.Generator#flatMap flatMap} makes one draw depend on another:
///   ```java
///   Generator<List<Boolean>> sized = integers(0, 10).flatMap(n -> lists(booleans(), n, n));
///   ```
/// - {@link dev.hegel.Generators#compose compose} builds a value imperatively from several draws:
///   ```java
///   Generator<int[]> pair = Generators.compose(tc -> new int[] {
///       tc.draw(integers()), tc.draw(integers())
///   });
///   ```
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
///   int x = tc.draw(integers(1, 1000));
///   int y = tc.draw(integers(1, 1000));
///   tc.assume(y != 0);
///   tc.note("testing " + x + " * " + y + " / " + y);
///   assertEquals(x, (x * y) / y);
/// }
/// ```
///
/// ## Settings
///
/// Configure a run with {@link dev.hegel.Hegel#with()} and the fluent setters on
/// {@link dev.hegel.Settings}, or with attributes on {@link dev.hegel.HegelTest @HegelTest}:
///
/// ```java
/// Hegel.with()
///     .testCases(500)        // run more inputs
///     .seed(42)              // reproducible run
///     .check(tc -> { /* ... */ });
///
/// @HegelTest(testCases = 1000, seed = 42)
/// void thorough(TestCase tc) { /* ... */ }
/// ```
///
/// Other settings include `derandomize`, `database`/`noDatabase`, `suppressHealthCheck`,
/// `verbosity`, `singleTestCase`, and {@link dev.hegel.Settings#phases(dev.hegel.Phase...) phases}.
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
///   Point bounded = tc.draw(Generators.records(Point.class).with("x", integers(0, 9)));
/// }
/// ```
package dev.hegel;
