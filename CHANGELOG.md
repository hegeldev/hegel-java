# Changelog











## 0.6.1 - 2026-09-16

Hegel can now be used as a library by other JVM frontends (hegeldev/hegel-java#12).

- New `Hegel.run(body, settings, reporter)` returns a `RunReport` instead of throwing: the verdict
  (`RunStatus`), per-outcome case counts (`RunStatistics`), the engine's message for an errored
  run, and one `Failure` per distinct counterexample carrying the body's exception, the labelled
  top-level draws of the minimal example as Java values, the notes, the engine's origin string, and
  the reproduce blob. `RunReport.throwIfFailed()` reproduces `Hegel.test`'s throwing behaviour.
- New `Reporter` interface receives everything a run prints as callbacks (engine output, case
  start/finish, final-replay draws and notes, each failure, the final report). Hegel no longer
  writes to `System.err` directly: `Hegel.test` uses `Reporter.printing(System.err)`, which prints
  exactly what it printed before, and `Reporter.silent()` turns output off. Both `Hegel.test` and
  `Hegel.run` accept a reporter as an optional third argument.
- `Hegel.test` now returns the `RunReport` of a passed run. This is source-compatible (callers that
  ignored the `void` result compile unchanged) but code compiled against an earlier release must be
  recompiled.
- New `TestCase.isFinal()` tells a test body whether it is running the final replay of a
  counterexample, where its own diagnostics will be seen.
- New `TestCase.span(label, body)` and public `Label` constants (plus `Label.of(name)` for minting
  stable custom labels) let custom composite generators enclose their draws in a labelled span, so
  the engine shrinks the structure as a unit. `TestCase.startSpan`/`stopSpan` are now documented.
- New `Settings.infrastructurePackages(prefixes...)` lists a frontend's own class-name prefixes so
  they are skipped, like Hegel's and JUnit's, when locating the user frame a failure was thrown from.
- Reported draw names now follow the other Hegel frontends: a label drawn more than once in a case
  is numbered from its second use (`x`, `x_2`, `x_3`) so no value is lost, and unlabelled draws are
  numbered among themselves (`draw_1`, `draw_2`, ... — previously a labelled draw also advanced the
  counter). A note made inside a composite generator is now reported after the enclosing draw's
  value rather than before it.
- `Settings.verbosity` now governs the frontend's own output as documented: `QUIET` prints no
  draws or notes, and `VERBOSE`/`DEBUG` report every case's draws and notes, not only the final
  replay's.
- A checked exception thrown from a test body (possible from Kotlin, Clojure, and other frontends)
  is now rethrown as-is instead of failing with a `ClassCastException`.

New artifact `dev.hegel:hegel-lowlevel` (Java 17+), for people binding Hegel or building a frontend
from scratch. It holds only the binding contract: the `Libhegel` interface (one method per
`hegel_*` function, raw handles and return codes), the `Abi` constants, `LibraryLoader`,
`LibhegelException`, and the `LibhegelBackend` service provider interface. `Libhegel.load()` finds
whichever binding is on the classpath. Both `dev.hegel:hegel` and `dev.hegel:hegel-jna` now depend
on it and register their bindings as service providers; nothing changes for their users. The
package is marked experimental: implementors should expect new methods as the engine grows.
`HegelException` now extends `LibhegelException`.
## 0.6.0 - 2026-09-10

This release upgrades the bundled libhegel engine from 0.32.5 to 0.37.6. Along with engine-side
improvements to shrinking (stateful shrinking no longer keeps redundant steps and runs about 40%
faster; collections of expensive elements shrink further), the failure database (interrupting a
run mid-shrink can no longer lose a failure), and the health checks (`TooSlow` is suppressed by
default in CI, and every health check and the database are disabled inside Antithesis), it carries
three changes that may require updating your tests.

**Single-test-case mode is gone.** The engine removed it, so `Mode`, `Settings.mode(Mode)`, and
`@HegelTest(mode = ...)` no longer exist. Every run drives the full property-test loop. To run
exactly one test case per invocation, set the test-case budget to 1 instead: the engine then skips
the simplest-example probe and generates one random case.

Before:

```java
@HegelTest(mode = Mode.SINGLE_TEST_CASE)
void probe(TestCase tc) { ... }
```

After:

```java
@HegelTest(testCases = 1)
void probe(TestCase tc) { ... }
```

Unlike the old mode, a failing one-case run is still shrunk and replayed.

**Stateful invariants are now sampled.** `@Invariant` methods previously ran after every rule. They
now run in full on the machine's initial and final state and are sampled in between: after any given
rule, each invariant runs with probability `1 / stepCount`, which keeps an invariant's expected cost
per test case constant as the step count grows. An invariant that must observe every intermediate
state — including one that mutates state when checked — can opt out of sampling with the new
`alwaysRun` attribute:

```java
@Invariant(alwaysRun = true)
void noUnobservedWrites(TestCase tc) {
    assertTrue(writesSinceLastCheck <= 1);
    writesSinceLastCheck = 0;
}
```

The failure report's `Initial invariant check.` line is reworded to `Checking invariants on the
initial state.` (with a matching line for the final check), and is only printed for machines that
have invariants.

**Times are nanosecond-precise.** `Generators.times()` and `Generators.datetimes()` now generate
and honour bounds at nanosecond rather than microsecond resolution. Bounds are no longer snapped to
whole microseconds, the default upper bound is `23:59:59.999999999`, and a lower bound of
`LocalTime.MAX`, which used to be rejected, is now a valid one-value range.
## 0.5.1 - 2026-08-28

This patch adds a second published artifact, `dev.hegel:hegel-jna`, which binds the native engine
over [JNA](https://github.com/java-native-access/jna) and runs on Java 17+. The existing
`dev.hegel:hegel` artifact is unchanged: it binds over the Foreign Function and Memory API and
requires Java 22+.

Both artifacts expose the identical `dev.hegel` API and behave the same, so tests written against
one run unchanged against the other. Depend on exactly one of them — `hegel` on Java 22+, or
`hegel-jna` on older JVMs:

```xml
<dependency>
  <groupId>dev.hegel</groupId>
  <artifactId>hegel-jna</artifactId>
  <version>0.5.1</version>
</dependency>
```

`hegel-jna` pulls in `net.java.dev.jna:jna` as its only dependency. On JDK 24+ pass
`--enable-native-access=ALL-UNNAMED` to silence the JVM's native-access warning (the flag is
accepted on every supported JDK).
## 0.5.0 - 2026-08-28

This release upgrades the bundled libhegel engine from 0.14.14 to 0.32.5, rewriting the FFM
binding layer against the engine's modern typed-draw C ABI. It brings roughly two months of engine
correctness, shrinking, and performance improvements, plus several new features.

New features:

- **Stateful (model-based) testing.** Annotate methods of a state-machine class with `@Rule` and
  `@Invariant` and drive it with `Stateful.run(machine, tc)`; the engine picks which action runs
  next, and failing action sequences shrink like any other generated value. A `Pool` tracks
  previously generated values so rules can reuse or consume them.

  ```java
  class StackMachine {
    private final Deque<Integer> stack = new ArrayDeque<>();

    @Rule
    void push(TestCase tc) {
      stack.push(tc.draw(integers()));
    }

    @Rule
    void pop(TestCase tc) {
      tc.assume(!stack.isEmpty());
      stack.pop();
    }

    @Invariant
    void neverNegative(TestCase tc) {
      assertTrue(stack.size() >= 0);
    }
  }

  @HegelTest
  void stackBehaves(TestCase tc) {
    Stateful.run(new StackMachine(), tc);
  }
  ```

- **Failure reproduction blobs.** `new Settings().printBlob(true)` (or
  `@HegelTest(printBlob = true)`) prints a copy-pasteable base64 blob with each reported failure;
  `reproduceFailure("<blob>")` replays exactly that test case, bypassing generation and shrinking.

- **Antithesis support.** `new Settings().backend(Backend.URANDOM)` sources every choice from
  `/dev/urandom`, handing the [Antithesis](https://antithesis.com/) fuzzer control over the entire
  test case. The default (`Backend.AUTO`) selects it automatically when running inside Antithesis.

- **`allowSubnormal` on `floats()` and `doubles()`**, for testing code that may run with
  flush-to-zero floating point (e.g. compiled with `-ffast-math`).

- **Bounded temporal generators.** `dates()`, `times()`, and `datetimes()` accept inclusive
  `min`/`max` bounds, and bounded dates shrink toward 2000-01-01. `domains()` gains
  `maxLength(int)`.

- **Engine output routing.** Engine-emitted output (verbose progress, warnings) now flows through
  the same stream as the failing-example report instead of always going to stderr.

Engine fixes picked up by the upgrade include: unbounded `doubles()` no longer returning
`Double.MAX_VALUE` most of the time, integer and string draws no longer being dominated by the
"interesting constants" pool, bounded values actually shrinking toward their target instead of 0,
regex anchors (`\b`, `\B`, `$`) respected in non-final positions, Unicode category filters covering
astral planes, several shrinker crashes and runaway-execution bugs, flaky tests reported as flaky
instead of under a wrong origin, and substantially more effective shrink passes.

This release also fixes derandomized seeds in CI. A derandomized run derives its seed from the
test's database key, which hegel-java previously sent only when the example database was enabled —
and CI disables the database. Every named test in a CI run therefore derandomized from the same
fallback key and saw the same inputs. Each named test now derives its seed from its own name, so
repeated runs of one test stay deterministic while different tests still see different inputs.

Breaking changes:

- Custom `Generator` implementations must now implement `doDraw(TestCase)`; the CBOR schema
  protocol (`asBasic()`/`BasicGenerator`) no longer exists, and `TestCase` exposes typed draw
  bridges instead of `generateFromSchema`. Generators built purely from `Generators` factories and
  combinators are unaffected.
- `text()` and `binary()` now default to a maximum size of 100 (or `minSize + 100` for larger
  minimums) instead of unbounded, matching the other Hegel frontends; set an explicit `maxSize` for
  longer values.
- A generator configuration the engine rejects (an empty text alphabet, an invalid regex) now
  throws `IllegalArgumentException` carrying the engine's diagnostic instead of `HegelException`,
  and conflicting float bound/special-value combinations are rejected at construction time.
- `reportMultipleFailures(true)` aggregates only when several distinct bugs are found; a run that
  finds a single bug now rethrows it directly (preserving its type and stack trace) instead of
  wrapping it in a one-entry "Hegel found 1 failing example" report, matching the other Hegel
  frontends.
- The `com.upokecenter:cbor` dependency is gone.
## 0.4.2 - 2026-08-13

This patch adds Windows support (x86-64 and arm64). The jar now bundles the Windows engine alongside the Linux and macOS ones, so Hegel tests run on Windows with no extra setup.

On Windows, a `libhegel.dll` placed on `PATH` takes precedence over the bundled engine (matching `LD_LIBRARY_PATH` on Linux and `DYLD_LIBRARY_PATH` on macOS), and the bundled engine is unpacked to a per-user cache under `%LOCALAPPDATA%`. `HEGEL_LIBHEGEL_PATH` overrides both, as on every OS.
## 0.4.1 - 2026-07-31

Fix error when cache directory for libhegel could not be written to, for example inside of a sandbox.
## 0.4.0 - 2026-07-09

This release changes the default value of `fullmatch` in `fromRegex` from `false` to `true`.
## 0.3.0 - 2026-06-29

Change the `Generators.uuids()` return type from `String` to `java.util.UUID`, and expose version configuration as a `uuids().version(v)` method.
## 0.2.0 - 2026-06-26

Improve Java Platform Module System support:

- Define `Automatic-Module-Name: dev.hegel` in the jar manifest, giving the artifact a stable
  module name on the module path.
- `@HegelTest` now invokes the test method through the JUnit platform's reflection support, so
  modular consumers no longer have to open their test package to `dev.hegel`.
## 0.1.1 - 2026-06-12

This release fixes the display of our published javadocs to include package info, and has no other functional changes.
## 0.1.0 - 2026-06-10

Initial release.
