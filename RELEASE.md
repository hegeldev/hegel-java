RELEASE_TYPE: patch

This patch upgrades the bundled libhegel engine from 0.45.0 to 0.47.1. The public Java API is
unchanged; the two bindings now pass every string across the C ABI as the engine's length-delimited
`hegel_string_t`, which is how the engine takes and returns strings since 0.46.

A run that never generates a valid test case now ends in the engine's `Unsatisfiable` error (a
`HegelException` from `Hegel.test`) however the health checks are configured. Before, a test whose
`assume` or `filter` rejected every input passed vacuously once the filter-too-much health check was
suppressed. The engine also no longer cuts a run short after a handful of consecutive repeated
rejections, which could make a test over a small input space fail the filter-too-much check, or
pass without a valid input, roughly one time in twenty.

`Generators.fromRegex` now records each drawn string as a single choice constrained by the pattern,
so a failure shrinks within the pattern instead of re-running the generator: a `\d` shrinks to `0`,
and `(?:bbbb|a)` shrinks to `a` rather than stopping at `bbbb`. Failures that earlier engine versions
saved for a regex generator no longer replay and are dropped from the database like any stale entry.
A pattern that no string over its alphabet can match (`abc` over an alphabet of `def`, `\d` over
letters, or `(?!)`) now fails up front with an `IllegalArgumentException` naming the pattern and the
alphabet, where it used to be built and then reject every draw. Regex and text generators use the
Unicode 17.0 tables (they were 15.1).

`Failure.nondeterministic()` is now true only for a failure the run actually saw fail to reproduce.
Before, every failure of a run that had switched to nondeterministic handling carried a caveat, so a
failure that reproduced on every replay could be reported as "confirmed: failed 20 of 20 replays".

Replaying a reproduce blob with `Settings.reproduceFailure` against a test whose draws have changed
no longer continues past the point where the test's draws leave the recording: the replay counts as
a miss, and a blob none of whose replays reproduced is reported as stale, with an output line naming
where the draws diverged. Before, a failure the changed test happened to reach on fresh draws could
be reported as the blob's failure.

Generation and shrinking improved along the way: targeting with `TestCase.target` starts after a
short warm-up in runs of 1,000 or more test cases instead of halfway through; bounded integers whose
failing values are sparse (such as multiples of six) shrink to the minimum instead of stalling;
integer ranges wider than 128 bits draw ordinary values spread over the range's orders of magnitude
rather than almost always near the top; and span mutation no longer skews small finite spaces toward
the all-zero sequence. Seeded runs that draw such values generate different inputs from this version
on. Health check and `Unsatisfiable` messages drop their Rust syntax and name the check and setting
plainly, and verbose output announces every executed test case with `Running test case`.

Under Antithesis, `Backend.URANDOM` (the shipped `workload` profile) now takes its entropy from the
Antithesis fuzzer directly through `libvoidstar`, so the fuzzer sees every draw as a request of its
own; elsewhere it reads `/dev/urandom` as before.

The engine also gained a `stop_on_first_failure` setting (on by default; turned off, a run spends its
whole `testCases` budget before reporting and replays every stored failure), grammar-based string
generation from Lark grammars, and a structured run-error kind. None of these have a Java API yet;
the `HEGEL_STOP_ON_FIRST_FAILURE` environment variable and the `stop_on_first_failure` key in
`hegel.toml` already reach the engine, since it reads them itself.
