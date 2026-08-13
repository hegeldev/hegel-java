RELEASE_TYPE: patch

This patch adds Windows support (x86-64 and arm64). The jar now bundles the Windows engine alongside the Linux and macOS ones, so Hegel tests run on Windows with no extra setup.

On Windows, a `libhegel.dll` placed on `PATH` takes precedence over the bundled engine (matching `LD_LIBRARY_PATH` on Linux and `DYLD_LIBRARY_PATH` on macOS), and the bundled engine is unpacked to a per-user cache under `%LOCALAPPDATA%`. `HEGEL_LIBHEGEL_PATH` overrides both, as on every OS.
