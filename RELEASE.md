RELEASE_TYPE: minor

Improve Java Platform Module System support:

- Define `Automatic-Module-Name: dev.hegel` in the jar manifest, giving the artifact a stable
  module name on the module path.
- `@HegelTest` now invokes the test method through the JUnit platform's reflection support, so
  modular consumers no longer have to open their test package to `dev.hegel`.
