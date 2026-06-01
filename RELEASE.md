RELEASE_TYPE: minor

The native engine, `libhegel`, is now bundled inside the jar for every supported platform
(Linux and macOS, x86-64 and ARM64) and unpacked automatically on first use. Nothing is
downloaded at runtime — this replaces the previous auto-downloader, which failed against
GitHub's redirecting release URLs. The bundled libraries are fetched at build time for whatever
shared objects the pinned engine release publishes, so the shipped jar is self-contained.

`$HEGEL_LIBHEGEL_PATH` and a sibling `hegel-rust` build still take precedence for local engine
development.
