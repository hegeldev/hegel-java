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

Add the dependency with Maven:

```xml
<dependency>
  <groupId>dev.hegel</groupId>
  <artifactId>hegel</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

or with Gradle:

```kotlin
testImplementation("dev.hegel:hegel:0.1.0")
```

Hegel for Java requires **Java 22+** and uses the [Foreign Function & Memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html). The native engine is bundled in the jar for Linux (x86-64 and arm64) and macOS (Apple Silicon).

Because Hegel calls native code, pass `--enable-native-access=ALL-UNNAMED` to silence the JVM's native-access warning. With Maven Surefire:

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
