# Changelog



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
