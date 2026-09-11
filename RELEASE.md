RELEASE_TYPE: patch

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
- A checked exception thrown from a test body (possible from Kotlin, Clojure, and other frontends)
  is now rethrown as-is instead of failing with a `ClassCastException`.
