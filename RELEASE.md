RELEASE_TYPE: minor

This release upgrades the bundled libhegel engine from 0.14.14 to 0.30.4, rewriting the FFM
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
