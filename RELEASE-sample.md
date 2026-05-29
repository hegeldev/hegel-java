RELEASE_TYPE: patch

A short, user-facing description of the change, written for the CHANGELOG.

Copy this file to `RELEASE.md` in any pull request that changes source. The first line must be
`RELEASE_TYPE: major`, `RELEASE_TYPE: minor`, or `RELEASE_TYPE: patch`:

- `patch` — bug fixes
- `minor` — new, backwards-compatible features
- `major` — breaking changes (maintainers only)

Label a PR `skip release` to bypass this requirement (e.g. for docs/CI-only changes).
