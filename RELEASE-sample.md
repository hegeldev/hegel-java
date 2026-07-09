RELEASE_TYPE: patch

A short, user-facing description of the change, written for the CHANGELOG.

Copy this file to `RELEASE.md` in any pull request that changes source. The first line must be
`RELEASE_TYPE: major`, `RELEASE_TYPE: minor`, or `RELEASE_TYPE: patch`:

- `patch` — bug fixes, internal changes, and new non-breaking features (the default)
- `minor` — breaking changes only
- `major` — not used while we are pre-1.0 (zerover); reserved for the eventual 1.0

Label a PR `skip release` to bypass this requirement (e.g. for docs/CI-only changes).
