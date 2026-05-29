# Review of libhegel (the native engine)

Observations on the underlying native engine, `libhegel`, gathered while writing the hegel-java
binding against it. The library is built from hegel-rust's `hegel-c` crate.

- **Repo:** `github.com/hegeldev/hegel-rust`
- **Version exercised:** `0.14.14` (release build of `libhegel.so`, linux/amd64)
- **Scope reviewed:** the C ABI surface (`hegel-c/include/hegel.h`, `hegel-c/src/lib.rs`) and the
  CBOR **schema interpreter** (`src/native/schema/`). I did **not** review the core engine
  (generation, shrinker, database) beyond observing its externally visible behaviour.

The engine is solid where it counts — the handle lifecycle, in-process worker model, thread-local
error reporting, and shrink quality all worked exactly as documented. The items below are real
sharp edges I hit, in rough order of severity.

## 1. (Major) Invalid schemas abort the host process instead of returning an error

`hegel_generate` carefully validates the *framing* — null pointers and malformed CBOR both return
`HEGEL_E_INVALID_ARG`:

```rust
// hegel-c/src/lib.rs:865  hegel_generate
let schema: Value = match ciborium::de::from_reader(schema_bytes) {
    Ok(v) => v,
    Err(e) => { set_last_error(...); return HEGEL_E_INVALID_ARG; }
};
let value = match tc.ds.generate(&schema) {   // <-- not wrapped in catch_unwind
    Ok(v) => v,
    Err(e) => return translate_ds_error(e),
};
```

But the schema *interpretation* (`tc.ds.generate`) runs on the caller's thread with **no
`catch_unwind`** (the only `catch_unwind` is around the worker engine loop, `lib.rs:672`, not the
`generate` hot path). The interpreter `panic!`s on a broad class of semantically-invalid schemas,
and because `hegel_generate` is `extern "C"`, a panic crossing that boundary **aborts the whole
process** (SIGABRT).

Panic sites in `src/native/schema/` that are reachable from caller-supplied schema content include:

| Bad input | Site |
|---|---|
| Unknown `type` string | `mod.rs:93` `panic!("Unknown schema type: {}", other)` |
| Invalid `codec` | `text.rs:72` `panic!("Invalid codec: {}", other)` |
| Invalid Unicode category | `text.rs:98` `panic!("... is not a valid Unicode category.")` |
| Invalid / missing regex `pattern` | `regex.rs:40`, `regex.rs:32` |
| Unsupported `ip_address` `version` | `special.rs` `panic!("ip_address: unsupported version ...")` |

I hit this directly: sending `{"type":"ipv4"}` (a plausible-looking but wrong type name) killed the
JVM with:

```
thread '<unnamed>' panicked at src/native/schema/mod.rs:93:18:
Unknown schema type: ipv4
fatal runtime error: failed to initiate panic, error 5, aborting
Aborted (core dumped)
```

**Why this matters:** the C header documents the opposite contract —

> `hegel_generate` ... Returns `HEGEL_E_INVALID_ARG` on malformed schema, NULL outputs, or other
> argument errors; the diagnostic is in `hegel_last_error_message`.

For an **in-process** engine driven over FFI, a client mistake (a typo in a schema `type`, an
unsupported parameter, a user-supplied regex) should be a catchable error code, not an abort of the
entire host test process. A binding author cannot defend against this except by exhaustively
re-validating every schema client-side before it reaches the engine — and even then is one engine
change away from a new panic path.

**Suggested fix:** wrap the `tc.ds.generate(&schema)` call (and the other per-case primitives that
interpret schema-derived data) in `catch_unwind`, and translate a caught panic into
`HEGEL_E_INVALID_ARG` / `HEGEL_E_BACKEND` with the panic message in `hegel_last_error_message`.
Better still, make the schema interpreter return `Result` for input-derived errors and reserve
`panic!` for genuine engine invariants.

## 2. The CBOR schema language is undocumented in the public ABI

`hegel.h` documents every C function, return code, and enum precisely, but says essentially nothing
about the **schema map format** that `hegel_generate` consumes — which is the actual contract for
the most important call. The header only notes that `schema_cbor` describes "type + bounds +
optional category filters." The set of valid `type` strings, their fields, defaults, and value
encodings must be reverse-engineered from `src/native/schema/` and `src/generators/`.

This compounds with #1: the format is both undocumented *and* fatal to get wrong. Several names are
non-obvious and easy to guess incorrectly — e.g. `one_of` uses the key `generators` (not
`options`); there is no `ipv4`/`ipv6` type, only `ip_address` with a `version` field; `just` is
`{"type":"constant","value":null}`; sets are a `list` with `"unique": true`; a `dict` value comes
back as an array of `[key, value]` pairs, not a CBOR map. Each wrong guess is a process abort.

**Suggested fix:** publish a schema reference alongside `hegel.h` (even a generated JSON-schema or a
doc comment block), so binding authors have an authoritative, versioned contract.

## 3. Default text generation can emit values strict-string clients can't represent

The engine can return string values wrapped in CBOR **Tag 91 (WTF-8)** containing unpaired
surrogate codepoints (Unicode category `Cs`). Languages whose string type is strict UTF-8 (Go,
Rust, and in practice Java, where round-tripping WTF-8 with surrogates is awkward) cannot decode
these. The only workaround is for every such binding to defensively inject
`"exclude_categories": ["Cs"]` into the `string` schema by default (hegel-rust itself does this).

This pushes a portability concern onto every binding rather than offering it as a central
codec/option. It's defensible (Hegel deliberately supports WTF-8), but it's a sharp edge: a naive
`text()` binding will intermittently crash on surrogate output until its author discovers the `Cs`
exclusion.

## 4. ABI doc overstates which primitives return `STOP_TEST`

The header says `HEGEL_E_STOP_TEST` "can come back from any per-case primitive," listing
`hegel_collection_more` and `hegel_new_collection` among them. Empirically I could not get either
to return `STOP_TEST`, even with deliberately oversized non-basic collections: the engine instead
ends the collection gracefully (`more` → false) or defers budget exhaustion to the *element*
`hegel_generate`. Not a bug, but the doc led me to write tests chasing a `collection_more`
`STOP_TEST` branch that appears unreachable in practice. Tightening the doc to say which primitives
realistically signal exhaustion would help binding authors reason about coverage.

## 5. (Minor) No ABI version negotiation beyond `hegel_version()`

A binding pins a version string (e.g. `0.14.14`) and can call `hegel_version()`, but there is no
structured ABI/version handshake. A version-mismatched `.so` surfaces only as a symbol-resolution
failure (if a symbol was added/removed) or as silent behavioural drift (if a schema field changed).
An explicit ABI-version symbol or a documented compatibility guarantee would make mismatches
diagnosable.

## What works well

For balance — these all behaved exactly as documented while binding:

- The handle lifecycle (`settings_new` → `run_start` → `next_test_case` loop → `run_result` →
  `run_free`) is clean and the in-process worker-thread model is transparent to the client.
- `hegel_run_free` correctly drains an abandoned run (in-flight case auto-completed, worker joined).
- The thread-local `hegel_last_error_message` is reliable when read immediately after a non-OK code.
- Health-check and flaky-test results arrive through the failure list with clear diagnostics, as
  documented.
- Shrink quality is excellent: failing examples consistently shrank to the true minimal
  counterexample (e.g. a length-preservation bug shrank to `[0, 0]`; an `x <= 10` property to `11`).
