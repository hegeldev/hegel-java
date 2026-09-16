RELEASE_TYPE: minor

This release upgrades the bundled libhegel engine from 0.37.6 to 0.42.4. The engine's shrinker now
reaches minimal examples in several situations where it previously stopped short (pairs of draws a
test pins together, `oneOf` alternatives that must switch to a shorter branch, failures that only
occur at multiples of a round number, bounded floats whose failing range excludes zero), a panic
during span reordering that could lose the shrunk counterexample is fixed, a memory leak in string
draws is fixed, and the limit on the number of choices a single test case may make rises from
8,192 to 1,048,576 (suppressing the `TEST_CASES_TOO_LARGE` health check removes it entirely).

The only breaking changes are in `dev.hegel:hegel-lowlevel`, and only code that implements or calls
`Libhegel` directly is affected; users of `dev.hegel:hegel` and `dev.hegel:hegel-jna` need not
change anything. `Libhegel.newStateMachine` takes a new `stepCount` argument after the concurrency
bounds (the engine no longer has a default; 50 is the conventional choice), the `Abi.LABEL_*` and
`Abi.BACKEND_AUTO` constants are removed along with their engine counterparts, and
`Abi.VERBOSITY_NORMAL` and `Abi.VERBOSITY_QUIET` swap values (`NORMAL` is now 0). The verbosity
constants are inlined at compile time, so code built against 0.6.1 must be recompiled to send the
right value.

Settings defaults are now resolved by the engine from named profiles, so hegel-java reads the same
`hegel.toml` as every other Hegel frontend: a `hegel.toml` in the working directory or one of its
parents (or the file named by `HEGEL_CONFIG`) applies to every run, and `HEGEL_DEFAULT_PROFILE`
selects the profile in effect. Settings given explicitly in Java, whether through `Settings` or
`@HegelTest`, still take precedence. `Backend.AUTO` now means exactly that the engine's profile
chooses: the shipped `workload` profile, selected inside Antithesis, uses `URANDOM`, and every other
profile uses `DEFAULT`.

Span labels no longer carry meaning beyond identity: the engine treats two spans with the same
label as coming from the same generator and does nothing else with them. The `Label` constants are
now derived from `dev.hegel.<kind>` names with the same FNV-1a hash as `Label.of`, and the new
`Label.combine(long...)` derives the label of a generator built from other generators from its own
label and its components', matching the engine's `hegel_label_combine`, so that a list of integers
and a list of strings get different labels while every list of integers gets the same one:

```java
long myList = Label.of("mylib.list");
long myListOfIntegers = Label.combine(myList, Label.of("mylib.integers"));
```

The stateful step count is now a per-machine parameter rather than an engine default. `Stateful.run`
keeps using 50 steps per test case (`Stateful.DEFAULT_STEP_COUNT`), and a new
`Stateful.run(machine, tc, stepCount)` overload sets a different budget; each sampled invariant runs
with probability `1 / stepCount` after a step.
