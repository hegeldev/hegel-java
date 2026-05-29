# Review of hegel-go's libhegel FFI port (PR #78)

Notes from reading the native FFI port of hegel-go while building hegel-java, for comparison.

- **Repo:** `github.com/hegeldev/hegel-go`
- **PR:** #78 "Replace Python-server backend with libhegel FFI" (draft)
- **Branch / commit reviewed:** `DRMacIver/libhegel` @ `0cc4cae`
- **Files read in full:** `libhegel.go`, `run_orchestrator.go`, `runner.go`. Not reviewed:
  `cbor.go`, the generator files, `libhegel_download.go`, the test suite.

This is a **draft** PR, so several of the items below are expected work-in-progress rather than
shipped defects. The design is otherwise very close to the hegel-rust reference and is a clean,
idiomatic binding.

## Correctness / robustness

### 1. `hegel_next_test_case` returning NULL is not distinguished from a mid-run error
`runProperty` treats any NULL from `hegel_next_test_case` as "run finished":

```go
for {
    tcH := libh.nextTestCase(runH)
    if tcH == 0 {
        break
    }
    driveOneCase(...)
}
```
(`run_orchestrator.go:39-45`)

The C ABI header is explicit that this is two different cases:

> Returns NULL when the run is finished... **A NULL with `hegel_last_error_message` set means
> something went wrong (engine crash, caller misuse) rather than normal completion.**

The port does not read `hegel_last_error_message` here. In practice a genuine mid-run engine
failure is *probably* still surfaced one step later (`hegel_run_result` then returns NULL and that
path does return an error), so this is more a robustness/diagnostic gap than a silent-pass bug: the
error is reported as a vague `hegel_run_result: ...` rather than pointing at the actual failing
`next_test_case` call. Checking the thread-local error on the NULL from `next_test_case` would make
engine crashes diagnosable at their source. (hegel-java checks it there.)

### 2. `collection_reject` always passes a NULL reason
```go
rc := s.libh.collectionReject(s.tc, id, nil)
```
(`runner.go:199`)

The ABI accepts an optional human-readable reason, and hegel-rust supplies one
(`collection.reject(Some("duplicate element"))`). Passing `nil` is functionally correct but discards
diagnostic information the engine could use/log for rejected elements (duplicates in sets/maps).
Minor. (hegel-java passes `"duplicate element"` / `"duplicate key"`.)

## Coverage quality ("the coverage issue")

hegel-go's stated standard is 100% coverage with fake FFI bindings for error paths (it ships
`runner_libhegel_test.go` / `generator_error_paths_test.go` precisely for this). Yet this port marks
a number of branches `// coverage-ignore` that the project's own testing guidance says are
*reachable with fakes*:

- `runStart == 0` (`run_orchestrator.go:25`) and `runResult == 0` (`:48`) — the engine
  setup/teardown backend-error paths. The hegel-testing guidance lists these as fake-coverable
  ("Cleanup paths... run a test to completion", "each `HEGEL_E_*` return-code branch: use a fake
  binding that returns that code").
- The `default` arm of `translateRC` (`runner.go:228`) is the sole handler for
  `HEGEL_E_BACKEND` / `_INVALID_ARG` / `_INVALID_HANDLE` / `_ALREADY_COMPLETE` / `_NOT_COMPLETE` /
  `_INTERNAL`. The "real error, not INVALID" mapping is the single highest-stakes rule in the FFI
  guidance, but those return-code constants are themselves annotated `coverage-ignore`
  (`libhegel.go:60-64`) and there is no fake-binding test forcing each through `translateRC`.
- `isCI`'s `if v.matchAny || val == v.expected` (`runner.go:415`) — trivially testable by setting an
  env var.
- The entire `Test(...)` entry-point body (`runner.go:447-451`), including the `t.Fatal(err)`
  failure path, is `coverage-ignore`.
- Defensive `coverage-ignore`s that are reasonable but could be tested: `registerSymbols`' `recover`
  (`:297`, needs a stub `.so`), `goStrFromPtr` nil (`:334`), `cStrPtr` empty (`:324`), the darwin
  filename branch (`:241`).

None of these are *wrong* to exclude in a draft, but collectively the port reaches green by ignoring
more error-handling surface than the project's "fakes cover every `HEGEL_E_*`" philosophy intends —
exactly the surface where FFI bindings hide silent bugs. (For comparison, hegel-java covers all of
these with `FakeLibhegel`-driven tests and keeps only two `@Generated` exclusions: a SHA-256
`NoSuchAlgorithmException` and a reflective-dispatch catch.)

## Dead code / documentation

### 3. `cStrPtr` doc does not match its signature
```go
// cStrPtr returns a pointer suitable for passing to a libhegel function that
// takes a const char*. Returns nil if s is empty AND want_null is true; this
// is needed because some libhegel APIs treat nil and "" differently (notably
// hegel_settings_database).
func cStrPtr(buf []byte) *byte {
    if len(buf) == 0 { // coverage-ignore (we always pass cStr() output)
        return nil
    }
    return &buf[0]
}
```
(`libhegel.go:319-328`)

The comment describes a parameter `s` and a `want_null` flag that the function does not have. As
written, `cStrPtr` only returns nil for a zero-length slice, which never happens for `cStr()` output
(`cStr("")` is one NUL byte), so the nil branch is dead. The actual nil-vs-`""` distinction the
comment talks about is implemented elsewhere, by `cStrOrNull` (`run_orchestrator.go:228`). The doc
should be corrected (and the dead branch removed, or `cStrPtr` folded into `cStrOrNull`).

## Things the port gets right (and that caught a bug in hegel-java)

Worth recording because it's the reason this review happened:

- **Settings C-string lifetime is handled correctly.** `buildSettings` returns the `[][]byte`
  buffers and `runProperty` holds them with `defer runtime.KeepAlive(keepAlives)` until after
  `hegel_run_start` consumes the settings, because libhegel *borrows* the `database` /
  `database_key` pointers. hegel-java originally allocated those in a GC-managed `Arena.ofAuto()`
  that could be freed before `run_start` — a latent use-after-free. Reading this code is what
  surfaced and fixed that bug (hegel-java now uses a per-settings-handle confined arena).
- Per-call buffers (`generate` schema, `target` label, `mark_complete` origin) are each kept alive
  across exactly their own call with `runtime.KeepAlive`, and the `hegel_generate` output is copied
  out immediately before the next call — both correct.
- The thread-local-error discipline, the `origin`-as-shrink-key handling with a separate
  `panicByOrigin` capture map, and `buildSettings`' option ordering all match hegel-rust.
