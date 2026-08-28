/**
 * Property-based testing for Java, powered by the Hegel engine.
 *
 * <p>Instead of writing tests with hand-picked example inputs, you describe a <em>property</em> that
 * should hold for all inputs and let Hegel generate inputs to try to falsify it. When it finds a
 * failing input it automatically <strong>shrinks</strong> it to a minimal counterexample.
 *
 * <p>Hegel requires <strong>Java 22+</strong> (it uses the Foreign Function &amp; Memory API) and
 * {@code --enable-native-access=ALL-UNNAMED} on the test JVM. The native engine, {@code libhegel},
 * is bundled inside the jar for every supported platform and loaded automatically — nothing else to
 * install.
 *
 * <h2>Your first test</h2>
 *
 * <p>Use JUnit 5 as the runner. Annotate a method with {@link dev.hegel.HegelTest @HegelTest} and
 * give it a {@link dev.hegel.TestCase} parameter:
 *
 * <pre>{@code
 * import static dev.hegel.Generators.integers;
 * import static org.junit.jupiter.api.Assertions.assertEquals;
 *
 * import dev.hegel.HegelTest;
 * import dev.hegel.TestCase;
 *
 * class FirstTest {
 *   @HegelTest
 *   void integerSelfEquality(TestCase tc) {
 *     int n = tc.draw(integers());
 *     assertEquals(n, n); // an integer always equals itself
 *   }
 * }
 * }</pre>
 *
 * <p>{@code @HegelTest} runs the method many times (100 by default). Each run receives a
 * {@link dev.hegel.TestCase}, whose {@link dev.hegel.TestCase#draw(dev.hegel.Generator) draw} method
 * produces a value from a generator.
 *
 * <p>When you need a setting that can't be a compile-time constant, or want to run a property
 * outside a JUnit method, drive it programmatically with
 * {@link dev.hegel.Hegel#test(java.util.function.Consumer)} — the body comes first, with optional
 * {@link dev.hegel.Settings}:
 *
 * <pre>{@code
 * import static dev.hegel.Generators.integers;
 * import static org.junit.jupiter.api.Assertions.assertEquals;
 *
 * import dev.hegel.Hegel;
 * import org.junit.jupiter.api.Test;
 *
 * class CommutativityTest {
 *   @Test
 *   void additionCommutes() {
 *     Hegel.test(tc -> {
 *       int x = tc.draw(integers());
 *       int y = tc.draw(integers());
 *       assertEquals(x + y, y + x);
 *     });
 *   }
 * }
 * }</pre>
 *
 * <h2>Understanding test output</h2>
 *
 * <p>When a property fails, Hegel replays the minimal counterexample and prints each top-level
 * {@code draw} as an assignment:
 *
 * <pre>{@code
 * draw_1 = 50;
 * }</pre>
 *
 * <p>Pass a label — {@code tc.draw(integers(), "n")} — to name the variable instead:
 *
 * <pre>{@code
 * n = 50;
 * }</pre>
 *
 * <h2>Generators</h2>
 *
 * <p>{@link dev.hegel.Generators} provides a rich set of generators. Primitives include
 * {@code integers}, {@code longs}, {@code floats} (32-bit) and {@code doubles} (64-bit),
 * {@code booleans}, {@code text}, and {@code binary}; collections include {@code lists},
 * {@code sets}, and {@code maps}; and there are {@code tuples}, {@code oneOf}, {@code optional},
 * {@code sampledFrom}, {@code just}, {@code durations} ({@code java.time.Duration}), and the temporal
 * generators {@code dates}, {@code times}, and {@code datetimes} (which produce
 * {@code java.time.LocalDate}/{@code LocalTime}/{@code LocalDateTime}), plus format generators
 * ({@code emails}, {@code urls}, {@code ipAddresses}, {@code uuids}, {@code fromRegex}, …).
 *
 * <p>For zone-aware datetimes, attach a timezone to a {@code datetimes()} generator:
 *
 * <ul>
 *   <li>{@link dev.hegel.generators.DateTimeGenerator#timezones timezones}:
 *       {@code datetimes().timezones(zoneIds())} produces DST-aware {@code java.time.ZonedDateTime}
 *       values over the full range of zones the JVM supports (see
 *       {@link dev.hegel.Generators#zoneIds()}); pin one with
 *       {@code datetimes().timezones(just(ZoneId.of("Europe/London")))}.
 *   <li>{@link dev.hegel.generators.DateTimeGenerator#offsets offsets}:
 *       {@code datetimes().offsets(zoneOffsets())} produces fixed-offset
 *       {@code java.time.OffsetDateTime} values (see {@link dev.hegel.Generators#zoneOffsets()}).
 * </ul>
 *
 * <p>The bound- and size-bearing generators are fluent builders that <em>are</em> the generator:
 *
 * <pre>{@code
 * tc.draw(integers().min(0).max(100));       // bounded ints
 * tc.draw(text().minSize(1).maxSize(10));    // short strings
 * tc.draw(doubles().min(0).max(1));          // a probability (64-bit)
 * tc.draw(floats().min(0).max(1));           // a 32-bit float in [0, 1]
 * tc.draw(lists(integers()).minSize(1).maxSize(5));          // 1–5 element lists
 * }</pre>
 *
 * <h2>Combinators</h2>
 *
 * <p>Build new generators from existing ones (see {@link dev.hegel.Generator}):
 *
 * <ul>
 *   <li>{@link dev.hegel.Generator#map map} transforms each value (and keeps the efficient
 *       single-draw path when possible):
 *       <pre>{@code
 * Generator<Integer> evens = integers().min(0).max(50).map(x -> x * 2);
 * }</pre>
 *   <li>{@link dev.hegel.Generator#filter filter} keeps values matching a predicate (prefer
 *       constraining over filtering when you can):
 *       <pre>{@code
 * Generator<Integer> big = integers().filter(x -> x > 1000);
 * }</pre>
 *   <li>{@link dev.hegel.Generator#flatMap flatMap} makes one draw depend on another:
 *       <pre>{@code
 * Generator<List<Boolean>> sized = integers().min(0).max(10).flatMap(n -> lists(booleans()).minSize(n).maxSize(n));
 * }</pre>
 *   <li>{@link dev.hegel.Generators#composite composite} builds a value imperatively from several
 *       draws:
 *       <pre>{@code
 * Generator<int[]> pair = Generators.composite(tc -> new int[] {
 *     tc.draw(integers()), tc.draw(integers())
 * });
 * }</pre>
 * </ul>
 *
 * <h2>Recursive generators</h2>
 *
 * <p>{@link dev.hegel.Generators#deferred() deferred} creates a forward reference so a generator can
 * refer to itself, enabling self-recursive (and mutually recursive) data such as trees:
 *
 * <pre>{@code
 * record Tree(Integer leaf, Tree left, Tree right) {} // leaf != null XOR children != null
 *
 * Deferred<Tree> tree = Generators.deferred();
 * Generator<Tree> leaf = integers().map(n -> new Tree(n, null, null));
 * Generator<Tree> branch =
 *     tuples(tree, tree).map(t -> new Tree(null, t.value1(), t.value2()));
 * tree.set(oneOf(leaf, branch)); // wire up the self-reference
 * Tree t = tc.draw(tree);
 * }</pre>
 *
 * <p>The engine's size control keeps generated structures finite. Drawing before
 * {@link dev.hegel.generators.Deferred#set set} is called fails.
 *
 * <h2>Control functions</h2>
 *
 * <p>Inside a test body you can steer the engine via {@link dev.hegel.TestCase}:
 *
 * <ul>
 *   <li>{@link dev.hegel.TestCase#assume(boolean) assume} discards the current input if a
 *       precondition does not hold.
 *   <li>{@link dev.hegel.TestCase#note(String) note} records a message shown only on the final
 *       replay of a failing case.
 *   <li>{@link dev.hegel.TestCase#target(double, String) target} reports a score so the search can
 *       hill-climb toward interesting inputs.
 * </ul>
 *
 * <pre>{@code
 * @HegelTest
 * void divisionRoundTrips(TestCase tc) {
 *   int x = tc.draw(integers().min(1).max(1000));
 *   int y = tc.draw(integers().min(1).max(1000));
 *   tc.assume(y != 0);
 *   tc.note("testing " + x + " * " + y + " / " + y);
 *   assertEquals(x, (x * y) / y);
 * }
 * }</pre>
 *
 * <h2>Settings</h2>
 *
 * <p>Configure a run by passing a {@link dev.hegel.Settings} value (built with
 * {@code new Settings()} and the fluent setters) to
 * {@link dev.hegel.Hegel#test(java.util.function.Consumer, dev.hegel.Settings)}, or with attributes
 * on {@link dev.hegel.HegelTest @HegelTest}:
 *
 * <pre>{@code
 * Hegel.test(
 *     tc -> {
 *       // your property here
 *     },
 *     new Settings()
 *         .testCases(500)    // run more inputs
 *         .seed(42));        // reproducible run
 *
 * @HegelTest(testCases = 1000, seed = 42)
 * void thorough(TestCase tc) {
 *   // your property here
 * }
 * }</pre>
 *
 * <p>Other settings include {@code derandomize},
 * {@link dev.hegel.Settings#database(dev.hegel.Database) database}, {@code suppressHealthCheck},
 * {@code verbosity}, {@code mode}, and {@link dev.hegel.Settings#phases(dev.hegel.Phase...) phases}.
 * In CI (detected automatically) runs default to deterministic and the example database is disabled.
 * If a health check fires — for example, your generators reject almost every input — Hegel aborts
 * the run and throws {@link dev.hegel.HealthCheckFailure} (distinct from a property's own failure);
 * pass the relevant {@link dev.hegel.HealthCheck} to {@code suppressHealthCheck} if the behaviour is
 * intentional.
 *
 * <h2>Deriving generators from types</h2>
 *
 * <p>Hegel can build a generator for a record, enum, or supported scalar/collection type by
 * reflection via {@link dev.hegel.Generators#forType(Class) forType} and
 * {@link dev.hegel.Generators#records(Class) records}:
 *
 * <pre>{@code
 * record Point(int x, int y) {}
 * enum Color { RED, GREEN, BLUE }
 *
 * @HegelTest
 * void derived(TestCase tc) {
 *   Point p = tc.draw(Generators.forType(Point.class));
 *   Color c = tc.draw(Generators.forType(Color.class));
 *   // Override a single component:
 *   Point bounded = tc.draw(Generators.records(Point.class).with("x", integers().min(0).max(9)));
 * }
 * }</pre>
 */
package dev.hegel;
