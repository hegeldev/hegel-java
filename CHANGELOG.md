# Changelog







## 0.4.2 - 2026-08-13

This patch adds Windows support (x86-64 and arm64). The jar now bundles the Windows engine alongside the Linux and macOS ones, so Hegel tests run on Windows with no extra setup.

On Windows, a `libhegel.dll` placed on `PATH` takes precedence over the bundled engine (matching `LD_LIBRARY_PATH` on Linux and `DYLD_LIBRARY_PATH` on macOS), and the bundled engine is unpacked to a per-user cache under `%LOCALAPPDATA%`. `HEGEL_LIBHEGEL_PATH` overrides both, as on every OS.
## 0.4.1 - 2026-07-31

Fix error when cache directory for libhegel could not be written to, for example inside of a sandbox.
## 0.4.0 - 2026-07-09

This release changes the default value of `fullmatch` in `fromRegex` from `false` to `true`.
## 0.3.0 - 2026-06-29

Change the `Generators.uuids()` return type from `String` to `java.util.UUID`, and expose version configuration as a `uuids().version(v)` method.
## 0.2.0 - 2026-06-26

Improve Java Platform Module System support:

- Define `Automatic-Module-Name: dev.hegel` in the jar manifest, giving the artifact a stable
  module name on the module path.
- `@HegelTest` now invokes the test method through the JUnit platform's reflection support, so
  modular consumers no longer have to open their test package to `dev.hegel`.
## 0.1.1 - 2026-06-12

This release fixes the display of our published javadocs to include package info, and has no other functional changes.
## 0.1.0 - 2026-06-10

Initial release.
