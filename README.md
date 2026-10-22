# This repository has moved

Hegel for Java now lives in the Hegel monorepo at **https://github.com/hegeldev/hegel**.

| What you're looking for | Where it is now |
|---|---|
| The `dev.hegel:hegel`, `dev.hegel:hegel-jna` and `dev.hegel:hegel-lowlevel` artifacts (Java frontend) | [`frontends/java/`](https://github.com/hegeldev/hegel/tree/main/frontends/java) |
| libhegel, the engine behind every Hegel frontend | [`libhegel/`](https://github.com/hegeldev/hegel/tree/main/libhegel) |
| Changelog | [`frontends/java/CHANGELOG.md`](https://github.com/hegeldev/hegel/blob/main/frontends/java/CHANGELOG.md) |
| Bug reports and feature requests | [hegeldev/hegel issues](https://github.com/hegeldev/hegel/issues) |

Nothing changes for users of the artifacts: `dev.hegel:hegel` (Java 22+), `dev.hegel:hegel-jna` (Java 17+) and `dev.hegel:hegel-lowlevel` keep their coordinates on [Maven Central](https://central.sonatype.com/artifact/dev.hegel/hegel), and the API documentation is still at [javadoc.io](https://javadoc.io/doc/dev.hegel/hegel).

## Tags and history

The full history of this repository was merged into the monorepo unchanged, so every commit here exists there too. Releases are tagged by path and version in the new repository:

| Here | In hegeldev/hegel |
|---|---|
| `v0.10.2` | `frontends/java/0.10.2` |

Releases made before the move remain available under this repository's [releases](https://github.com/hegeldev/hegel-java/releases). New releases, starting with 0.10.3, are published only from hegeldev/hegel.

For general information about Hegel, see https://hegel.dev.
