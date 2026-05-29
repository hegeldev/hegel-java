# Changelog

All notable changes to hegel-java are documented here. The format follows the hegeldev
convention: a short, user-facing entry per release.

## 0.1.0

Initial release.

- Property-based testing for Java powered by the Hegel engine, loaded in-process over the Foreign
  Function & Memory API (no JNI, no manual install).
- JUnit 5 integration via the `@HegelTest` annotation, plus a framework-agnostic `Hegel.check`
  callback runner.
- Generators: `integers`, `longs`, `floats`, `booleans`, `text`, `characters`, `binary`, `just`,
  `sampledFrom`, `oneOf`, `optional`, `lists`, `sets`, `maps`, `tuples`, and the format generators
  `emails`, `urls`, `domains`, `ipv4`, `ipv6`, `uuids`, `dates`, `times`, `datetimes`, `fromRegex`.
- Combinators `map`, `filter`, `flatMap`, and `compose`, with the basic/composite dual path so
  `map` preserves single-call generation and shrink quality.
- Control functions `assume`, `note`, and `target`; minimal-counterexample reporting with labelled
  draws.
- Settings: test-case count, seed, derandomize, verbosity, single-test-case mode, health-check
  suppression, run phases (`phases(Phase...)` — e.g. disable shrinking), and the persistent example
  database (with CI-aware defaults).
- Type-directed derivation via `Generators.forType` and `Generators.records` for records, enums,
  scalars, and `List`/`Set`/`Optional`/`Map`.
