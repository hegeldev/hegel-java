# Getting started with Hegel for Java

This guide walks you through installing Hegel and writing your first tests. It mirrors the
getting-started guide for [hegel-rust](https://github.com/hegeldev/hegel-rust), adapted to Java.

## Install Hegel

Add `hegel` to your build as a test dependency (Maven):

```xml
<dependency>
  <groupId>dev.hegel</groupId>
  <artifactId>hegel</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Hegel needs Java 22+ and `--enable-native-access=ALL-UNNAMED` on the test JVM (see the
[README](README.md)). Nothing else is required: the native engine is loaded automatically.

## Write your first test

We'll use JUnit 5 as the test runner. Annotate a method with `@HegelTest` and give it a
`TestCase` parameter:

```java
import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hegel.HegelTest;
import dev.hegel.TestCase;

class FirstTest {
  @HegelTest
  void integerSelfEquality(TestCase tc) {
    int n = tc.draw(integers());
    assertEquals(n, n); // an integer always equals itself
  }
}
```

Run it with `mvn test`; it passes.

The `@HegelTest` annotation runs your method many times (100 by default). The method receives a
`TestCase`, whose `draw` method produces a value from a generator. This test draws an arbitrary
integer and checks that it equals itself.

Next, try a test that fails:

```java
@HegelTest
void integersAlwaysBelow50(TestCase tc) {
  int n = tc.draw(integers());
  assertTrue(n < 50); // this will fail!
}
```

Hegel finds an input that makes the assertion fail, then **shrinks** it to the smallest
counterexample — here, `n = 50`. To fix the test, constrain the generator instead:

```java
@HegelTest
void boundedIntegersAlwaysBelow50(TestCase tc) {
  int n = tc.draw(integers(0, 49));
  assertTrue(n < 50);
}
```

## Understanding test output

When a property fails, Hegel replays the minimal counterexample and prints each top-level `draw`
as an assignment:

```
draw_1 = 50;
```

If you pass a label — `tc.draw(integers(), "n")` — it is used as the variable name:

```
n = 50;
```

## Use generators

`dev.hegel.Generators` provides a rich set of generators. Primitives include `integers`,
`longs`, `bytes`, `shorts`, `bigIntegers`, `floats`, `floats32`, `booleans`, `text`, and `binary`;
collections include `lists`, `sets`, `maps`, `arrays`, and `fixedDict`; time values include
`durations`, `localDates`, `localTimes`, `localDateTimes`, and `instants`; and there are `tuples`,
`oneOf`, `optional`, `sampledFrom`, `just`, plus format generators (`emails`, `urls`, `ipv4`,
`dates`, `uuids`, `fromRegex`, …).

For example, generate a list of integers:

```java
@HegelTest
void appendIncreasesLength(TestCase tc) {
  List<Integer> xs = new ArrayList<>(tc.draw(lists(integers())));
  int before = xs.size();
  xs.add(tc.draw(integers()));
  assertTrue(xs.size() > before);
}
```

Generators with several options use fluent builders that *are* the generator:

```java
tc.draw(integers(0, 100));                 // bounded ints
tc.draw(text().minSize(1).maxSize(10));    // short strings
tc.draw(floats().min(0).max(1));           // a probability
tc.draw(lists(integers(), 1, 5));          // 1–5 element lists
```

## Combinators

Build new generators from existing ones:

- `map` transforms each value (and keeps the efficient single-draw path when possible):
  ```java
  Generator<Integer> evens = integers(0, 50).map(x -> x * 2);
  ```
- `filter` keeps values matching a predicate (prefer constraining over filtering when you can):
  ```java
  Generator<Integer> big = integers().filter(x -> x > 1000);
  ```
- `flatMap` makes one draw depend on another:
  ```java
  Generator<List<Boolean>> sized =
      integers(0, 10).flatMap(n -> lists(booleans(), n, n));
  ```
- `compose` builds a value imperatively from several draws:
  ```java
  Generator<int[]> pair = Generators.compose(tc -> new int[] {
      tc.draw(integers()), tc.draw(integers())
  });
  ```
- `deferred` lets a generator refer to itself, for recursive data:
  ```java
  Generator<Node>[] ref = new Generator[1];
  Generator<Node> node = Generators.deferred(() -> ref[0]);
  ref[0] = Generators.compose(tc ->
      new Node(tc.draw(integers()), tc.draw(optional(node)).orElse(null)));
  ```

## Control functions

Inside a test body you can steer the engine:

- `tc.assume(condition)` discards the current input if the precondition does not hold.
- `tc.note(message)` records a message shown only on the final replay of a failing case.
- `tc.target(value, label)` reports a score so the search can hill-climb toward interesting inputs.

```java
@HegelTest
void divisionRoundTrips(TestCase tc) {
  int x = tc.draw(integers(1, 1000));
  int y = tc.draw(integers(1, 1000));
  tc.assume(y != 0);
  tc.note("testing " + x + " * " + y + " / " + y);
  assertEquals(x, (x * y) / y);
}
```

## Settings

Configure a run with `Hegel.with()` and the fluent setters, or with attributes on `@HegelTest`:

```java
Hegel.with()
    .testCases(500)        // run more inputs
    .seed(42)              // reproducible run
    .check(tc -> { /* ... */ });

@HegelTest(testCases = 1000, seed = 42)
void thorough(TestCase tc) { /* ... */ }
```

Other settings include `derandomize`, `database`/`noDatabase`, `suppressHealthCheck`, `verbosity`,
`singleTestCase`, and `phases`. In CI (detected automatically) runs default to deterministic and the
example database is disabled.

`phases(Phase...)` enables only the listed phases (the default is all of `EXPLICIT`, `REUSE`,
`GENERATE`, `TARGET`, `SHRINK`). For example, to see an unshrunk failure quickly:

```java
Hegel.with()
    .phases(Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE, Phase.TARGET) // everything but SHRINK
    .check(tc -> { /* ... */ });
```

## Explicit examples

Sometimes you want to make sure a specific input is always tried (a past regression, a known edge
case). Register it with `example(Map.of(label, value))`; the body is replayed once per example with
those values substituted for its labelled draws, before the generation phase. Examples require
labelled draws:

```java
Hegel.with()
    .example(Map.of("x", 0))
    .example(Map.of("x", Integer.MAX_VALUE))
    .check(tc -> {
      int x = tc.draw(integers(), "x");
      assertTrue(timesTwo(x) % 2 == 0);
    });
```

Explicit examples run as part of the `EXPLICIT` phase (enabled by default); disable them by leaving
`EXPLICIT` out of `phases(...)`.

## Stateful (model-based) testing

For testing a stateful system, describe it as a `StateMachine`: a list of `Rule`s (actions) and
optional `invariants` that must hold after every step. Hegel draws a sequence of rules, applies
them, and checks the invariants, shrinking any failing sequence to a minimal one. Hold the system's
state in the machine instance, created fresh per test case:

```java
final class CounterModel implements StateMachine {
  private int n = 0;

  @Override public List<Rule> rules() {
    return List.of(
        Rule.of("increment", tc -> n++),
        Rule.of("decrement", tc -> n--));
  }

  @Override public List<Rule> invariants() {
    return List.of(Rule.of("inRange", tc -> assertTrue(Math.abs(n) < 1_000_000)));
  }
}

@HegelTest
void counter(TestCase tc) {
  Stateful.run(tc, new CounterModel());
}
```

A rule that calls `tc.assume(...)` to reject the current state is skipped and another is drawn.
(Engine-managed value pools — `Variables` — are not yet supported.)

## Deriving generators from types

Hegel can build a generator for many types by reflection: scalars and their wrappers, `String`,
`byte[]`, enums, records (recursively), arrays, `List`/`Set`/`Optional`/`Map`, `java.time` types
(`LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `Duration`), and sealed interfaces (a choice
over their permitted subclasses):

```java
record Point(int x, int y) {}
enum Color { RED, GREEN, BLUE }
sealed interface Shape permits Circle, Square {}
record Circle(double radius) implements Shape {}
record Square(int side) implements Shape {}

@HegelTest
void derived(TestCase tc) {
  Point p = tc.draw(Generators.forType(Point.class));
  Color c = tc.draw(Generators.forType(Color.class));
  Shape s = tc.draw(Generators.forType(Shape.class)); // a Circle or a Square
  // Override a single component:
  Point bounded = tc.draw(Generators.records(Point.class).with("x", integers(0, 9)));
}
```
