RELEASE_TYPE: minor

This release upgrades the bundled libhegel engine from 0.42.4 to 0.43.4, adds rule weights to
stateful testing, and hands the `HEGEL_*` settings environment variables to the engine.

A `@Rule` may now carry a weight, a hint about how often the engine should pick it relative to the
machine's other rules:

```java
@Rule(weight = 5)
void get(TestCase tc) { ... }

@Rule
void evictEverything(TestCase tc) { ... }
```

A plain `@Rule` has weight 1. The weight must be finite and strictly positive, and it is not a
distributional guarantee: each test case still enables a random subset of rules, so a rule's
realized frequency depends on which others are enabled alongside it.

The engine now applies the `HEGEL_TEST_CASES`, `HEGEL_DATABASE`, `HEGEL_SEED`, `HEGEL_DERANDOMIZE`,
`HEGEL_PRINT_BLOB` and `HEGEL_STATISTICS` environment variables itself, so hegel-java reads them the
same way as every other Hegel frontend. They sit between the settings profile and the settings
written into a test: a variable wins over `hegel.toml` and the shipped profiles, and a value given
through `Settings` or `@HegelTest` wins over the variable. `HEGEL_TEST_CASES=10000 mvn test` runs
every test that does not set its own budget with 10000 cases; `@HegelTest(testCases = 5)` keeps
its 5. A malformed variable fails the run with an `IllegalArgumentException` naming it.

This also fixes a gap in the previous release: hegel-java sent its own default of 100 test cases to
every run, so `test_cases` in a `hegel.toml` profile never took effect. Values left unset in Java
now genuinely fall through to the engine, and the `Settings` a `Reporter` receives in `runStarted`
carries the values the engine resolved. As a consequence the default for printing a reproduce blob
per failure now comes from the engine, whose shipped profiles turn it on: failing tests print a
`@HegelTest(reproduceFailure = "...")` line unless `Settings.printBlob(false)`,
`@HegelTest(printBlob = OptBoolean.FALSE)`, `HEGEL_PRINT_BLOB=false`, or a profile turns it off.
For the same reason, `@HegelTest.printBlob` is now an `OptBoolean` rather than a `boolean`:
replace `printBlob = true` with `printBlob = OptBoolean.TRUE`. `@HegelTest.testCases` defaults to
`0`, meaning the engine's value; any positive value behaves as before.

hegel-java's own CI detection is gone. The engine selects its `ci` profile on the same servers and
does the same things (deterministic runs, database disabled), and additionally suppresses the
`TOO_SLOW` health check there; unlike the Java logic it replaces, it can be overridden by
`HEGEL_DERANDOMIZE`, `HEGEL_DATABASE`, or a `[profiles.ci]` section in `hegel.toml`.

In `dev.hegel:hegel-lowlevel`, only code that implements or calls `Libhegel` directly is affected;
users of `dev.hegel:hegel` and `dev.hegel:hegel-jna` need not change anything beyond the annotation
attribute above. `Libhegel.newStateMachine` takes a new `double[] ruleWeights` argument after
`ruleGroups` (`null` for all-equal weights, the previous behaviour). `Libhegel.settingsNew` now
returns the raw result code and writes the handle to a `long[]` out-parameter, because constructing
the handle is where the engine applies the environment variables and can fail with
`Abi.E_INVALID_ARG`:

```java
// before
long s = lib.settingsNew();

// after
long[] out = new long[1];
int rc = lib.settingsNew(out);
```

`Libhegel` gains `settingsPrintBlob`, `settingsGetTestCases` and `settingsGetPrintBlob`, bound to
the engine's setter and getters of the same names.

The engine's shrinker also improves in four situations (values that must stay equal to each other,
list elements whose deletion has to be paid for by a later draw, pairs of numbers bound by their
product, and integers whose failing values are sparse multiples), and opening a span is cheaper,
which lowers the per-draw overhead of every generator.
