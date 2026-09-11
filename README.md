> [!IMPORTANT]
> We're excited you're checking out Hegel! Hegel is in beta, and we'd love for you to try it and [report any feedback](https://github.com/hegeldev/hegel-java/issues/new).
>
> As part of our beta, we may make breaking changes if it makes Hegel a better property-based testing library. If that instability bothers you, please check back in a few months for a stable release!
>
> See https://hegel.dev/compatibility for more details.

# Hegel for Java

* [Documentation](https://javadoc.io/doc/dev.hegel/hegel)
* [Website](https://hegel.dev)

Hegel is a property-based testing library for Java. Hegel is based on [Hypothesis](https://github.com/hypothesisworks/hypothesis), using the [Hegel protocol](https://hegel.dev/).

Instead of writing tests with hand-picked example inputs, you describe a *property* that should hold for all inputs and let Hegel generate inputs to try to falsify it. When it finds a failing input it automatically **shrinks** it to a minimal counterexample.

## Installation

Hegel for Java ships as two interchangeable artifacts with the same API — pick the one that
matches your JVM:

- **`dev.hegel:hegel`** — requires **Java 22+**; binds the engine over the
  [Foreign Function & Memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html)
  with no extra dependencies.
- **`dev.hegel:hegel-jna`** — requires **Java 17+**; binds the engine over
  [JNA](https://github.com/java-native-access/jna).

Add the dependency with Maven:

```xml
<dependency>
  <groupId>dev.hegel</groupId>
  <artifactId>hegel</artifactId> <!-- or "hegel-jna" for Java 17-21 -->
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

or with Gradle:

```kotlin
testImplementation("dev.hegel:hegel:0.1.0") // or "dev.hegel:hegel-jna:0.1.0"
```

Depend on exactly one of the two — they contain the same classes and differ only in how they call
the native engine. The engine is bundled in both jars for Linux (x86-64 and arm64), macOS (Apple
Silicon), and Windows (x86-64 and arm64). Both pull in a third, small artifact,
`dev.hegel:hegel-lowlevel`, which holds the binding contract; you never need to depend on it
directly unless you are [binding Hegel yourself](#binding-hegel-yourself).

Because Hegel calls native code, pass `--enable-native-access=ALL-UNNAMED` to silence the JVM's
native-access warning — printed by JDK 22+ for `hegel` (FFM) and by JDK 24+ for `hegel-jna` (JNA,
under [JEP 472](https://openjdk.org/jeps/472)). The flag is accepted on every supported JDK
(17+), so it is safe to set unconditionally. With Maven Surefire:

```xml
<argLine>--enable-native-access=ALL-UNNAMED</argLine>
```

## Quickstart

Here's a quick example of how to write a Hegel test:

```java
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hegel.HegelTest;
import dev.hegel.TestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

class SortTest {
  static List<Integer> mySort(List<Integer> xs) {
    return new ArrayList<>(new TreeSet<>(xs)); // oops: a TreeSet removes duplicates
  }

  @HegelTest
  void sortingPreservesLength(TestCase tc) {
    List<Integer> xs = tc.draw(lists(integers()), "xs");
    assertEquals(xs.size(), mySort(xs).size());
  }
}
```

This test will fail when run with `mvn test`! Hegel will produce a minimal failing test case for us:

```
xs = [0, 0];

org.opentest4j.AssertionFailedError: expected: <2> but was: <1>
```

Hegel reports the minimal example showing that our sort is incorrectly dropping duplicates: `[0, 0]`, two equal elements, which `mySort` collapses into one. If we replace the `TreeSet`-based body of `mySort()` with a sort that keeps duplicates, this test will then pass.

The optional `"xs"` label passed to `draw` names the value in the falsifying-example output. See the [API documentation](https://javadoc.io/doc/dev.hegel/hegel) for a full tour of generators, combinators, control functions, and settings.

## Using Hegel as a library

Frontends for other JVM languages (or custom runners) use `Hegel.run` instead of `Hegel.test`. It
returns a `RunReport` rather than throwing, and a `Reporter` lets you own every line of output:

```java
RunReport report = Hegel.run(tc -> { ... }, new Settings().testCases(200), Reporter.silent());
report.status();                        // PASSED, FAILED, or ERROR
report.statistics();                    // valid / invalid / overrun / interesting case counts
for (Failure f : report.failures()) {   // one per distinct counterexample
  f.draws();                            // labelled draws of the minimal example, as Java values
  f.exception();                        // the body's own throwable
  f.reproduceBlob();                    // replay it later with Settings.reproduceFailure
}
```

`TestCase.isFinal()` identifies the final replay of a counterexample, `TestCase.span` and `Label`
let custom composite generators tell the engine about their structure, and
`Settings.infrastructurePackages` keeps a frontend's own stack frames out of failure origins.

## Binding Hegel yourself

`dev.hegel:hegel-lowlevel` (Java 17+, no dependencies) is the binding contract on its own, for two
audiences that do not want the Java frontend:

- **Writing a binding** — implement `dev.hegel.lowlevel.Libhegel` (one method per `hegel_*`
  function in `hegel.h`, with raw handles and return codes) over your FFI mechanism, and register
  it as a `dev.hegel.lowlevel.LibhegelBackend` service provider. The two bundled bindings are
  registered the same way.
- **Building a frontend from scratch** — depend on `hegel-lowlevel` plus one binding, call
  `Libhegel.load()` to get the engine, and drive the run loop and per-case primitives yourself.
  `Abi` holds the constants; `LibraryLoader` resolves the shared library.

The package is experimental: new engine functions become new `Libhegel` methods.
