RELEASE_TYPE: patch

This patch upgrades the bundled libhegel engine from 0.44.0 to 0.45.0.

`Generators.fromRegex` now generates patterns that contain the NUL character the way Python's `re`
reads them: `fromRegex("a\0b")` generates `"a\0b"`. Before, the pattern crossed into the engine as a
NUL-terminated string, so the NUL was replaced by U+FFFD on the way and the generated strings did not
match the pattern. The engine now takes the pattern as a length-delimited buffer, and the bindings
pass it that way.

The engine also fixes several ways `fromRegex` could generate a string that does not match its
pattern as Python's `re` reads it, or reject a string it could have generated: under `(?i)`,
characters are compared by Python's case-folding rules, so `(?i)[^k]` no longer generates the Kelvin
sign; under `(?a)`, case-insensitivity folds only ASCII letters and explicit characters outside ASCII
are kept rather than dropped; and a repetition or alternation whose body the alphabet cannot supply
no longer trips the filter-too-much health check.

A reproduce blob that the engine cannot decode (corrupt, or from an incompatible engine version) is
now rejected before any run starts: `Settings.reproduceFailure` and `@HegelTest(reproduceFailure =
...)` throw a `HegelException` whose message says the blob "could not be decoded", instead of starting
a run that ends in an error. Likewise, a run started while `ANTITHESIS_OUTPUT_DIR` names a directory
that does not exist fails up front with the engine's diagnostic.
