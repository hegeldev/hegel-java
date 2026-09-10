RELEASE_TYPE: minor

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
