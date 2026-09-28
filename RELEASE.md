RELEASE_TYPE: minor

This release upgrades the bundled libhegel engine from 0.43.4 to 0.44.0, which handles
nondeterministic tests instead of refusing them, and changes the `Failure` API accordingly.

A test whose outcome changes when the same generated data is replayed — because it depends on hidden
global state, time, an outside service, or thread scheduling — previously aborted the run with a
`HegelException` reading "Flaky test detected". The engine now switches such a run to
nondeterministic handling: a discovered failure is confirmed by repeated replay before it is shrunk
or saved to the database, shrunk under a guard on how much reproduction reliability a shrink step may
trade away, and reported with a *caveat* quoting the run's own replay evidence. `Hegel.test` then
throws the test's own exception as for any other failure, and the printed report carries the caveat:

```
x = 11;
note: nondeterministic failure, confirmed: failed 7 of 20 replays at confirmation and 1 of 2 at report time

To reproduce this failure, replay it with:
    @HegelTest(reproduceFailure = "...")
```

A failure that never reproduces after its discovery still fails the run, reported as
`unconfirmed failure: failed 0 of 10 replays after the observed failure` and without a reproduce
blob. A reproduce blob from a nondeterministic failure records the failing runs the engine saw, and
`Settings.reproduceFailure` / `@HegelTest(reproduceFailure = ...)` now replays a blob until one of
its replays fails, under a bounded budget, instead of judging it stale after a single attempt; a
blob none of whose replays fail reports that "the supplied failure blob did not reproduce a failure".

The new `Settings.nondeterminismStrictness` (and `@HegelTest(nondeterminismStrictness = ...)`)
controls the reaction: `NondeterminismStrictness.QUIET` (the engine's default) switches silently,
`WARN` prints a one-line notice once per run, and `ERROR` aborts the run with the previous
flaky-test error, for suites that use determinism as a lint. It is a profile setting like any other
(`nondeterminism_strictness = "error"` in `hegel.toml`), the `HEGEL_NONDETERMINISM_STRICTNESS`
environment variable (`quiet`, `warn` or `error`) overrides it for one run, and a value set in Java
wins over both.

The engine now also runs every failure it is about to report one final time itself, so hegel-java no
longer replays reproduce blobs after a run: the reported draws, notes and exception come from the
engine's own final replay, and a failing test's body runs once less per reported failure. Two
visible consequences. `TestCase.isFinal()` is now true on every execution the engine stamps for the
failure report — the final replay of a counterexample, but also the short replays that confirm a
discovered failure — so a body that uses it to gate expensive diagnostics may run them a few times
per failure rather than once. And a `Reporter` receives the counterexample's `draw` and `note`
callbacks (flagged `finalReplay = true`) when the failure is reported, after the run loop, rather
than live while a replay executes; the order of callbacks is unchanged.

`Failure` changes shape to carry the caveat and to admit failures without a blob:

```java
// before
String blob = f.reproduceBlob();
Optional<Throwable> e = f.exception();
if (f.flaky()) { ... }

// after
Optional<String> blob = f.reproduceBlob();   // empty for an unconfirmed failure or a blob replay
Throwable e = f.exception();                 // always present
Optional<String> caveat = f.caveat();        // present for a nondeterministic failure
if (f.nondeterministic()) { ... }
```

`Failure.flaky()` is gone: the engine no longer hands back a failure whose replay passed. A
`Failure.reproduceBlob()` is also empty for a failure reproduced from a `reproduceFailure` blob, since
the caller already holds it.

In `dev.hegel:hegel-lowlevel`, `Libhegel` gains `runStartBlob` (`hegel_run_start_blob`),
`testCaseShouldCapture` (`hegel_test_case_should_capture`), `failureCaveat` (`hegel_failure_caveat`)
and the `settingsNondeterminismStrictness` / `settingsGetNondeterminismStrictness` pair, and
`Abi.RUN_STATUS_FAILED_NONDETERMINISTIC` is removed along with the ABI value it mirrored; a failing
nondeterministic run reports plain `RUN_STATUS_FAILED`. Bindings implementing `Libhegel` must add
the new methods.

Between 0.43.4 and 0.44.0 the engine also improved float generation, so tests on floats reach
overflow, underflow, cancellation and special-value bugs far more often (each test case draws its own
mixture of float categories, and bounded ranges cover every power-of-two scale they touch); roughly
halved the per-case cost of text generators built inside the test body by sharing built alphabets
between generators with the same constraints; shrinks a size drawn twice — the rows and columns of a
square matrix — while the values it governs still vary, reaching the smallest failing square; and
picked up correctness fixes in its arbitrary-precision integer dependency.
