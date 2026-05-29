RELEASE_TYPE: minor

Brings hegel-java to feature parity with hegel-rust and hegel-go, adding:

- **Stateful (model-based) testing** — `StateMachine`, `Rule`, and `Stateful.run` drive a sequence
  of rules with invariants checked after each step, shrinking failing sequences. (Engine-managed
  value pools are not yet available.)
- **Explicit examples** — `Settings.example(Map)` replays the body with chosen values for its
  labelled draws before generation, wired to the `EXPLICIT` phase.
- **New generators** — `bytes`, `shorts`, `bigIntegers`, single-precision `floats32` /
  `FloatGenerator.asFloat()`, `durations`, native `java.time` (`localDates`, `localTimes`,
  `localDateTimes`, `instants`), fixed-length `arrays`, `fixedDict`, and `deferred` for recursive
  definitions.
- **`fromRegex(pattern, fullmatch)`** for whole-string matches.
- **Wider derivation** — `forType` now derives arrays, sealed interfaces (a choice over permitted
  subclasses), and `java.time` types in addition to records, enums, scalars, and collections.

Also fixes the CBOR decoder to handle arbitrary-precision integers beyond the `long` range.
