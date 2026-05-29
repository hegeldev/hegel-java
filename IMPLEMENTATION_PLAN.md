# hegel-java feature-parity plan

Bring hegel-java up to feature parity with hegel-rust / hegel-go, **except** the stateful
`Variables`/value-pool feature, which is blocked on a new engine C ABI (the pool API is being added
to libhegel separately).

Baseline before this work: 130 tests, 100% instruction+branch coverage, conformance + lint + docs
all green. After: 151 tests, still 100% coverage and green.

## Self-contained generator additions

- [x] `fromRegex(pattern, fullmatch)` overload (schema `fullmatch` bool, default false)
- [x] Wider numerics: `bytes()`/`bytes(min,max)`, `shorts()`/`shorts(min,max)`,
      `bigIntegers(min,max)` (+ CBOR tag 2/3 bignum decoding)
- [x] `float32`: `FloatGenerator.asFloat()` + `Generators.floats32()`
- [x] `durations()` / `durations(min,max)`
- [x] Native `java.time`: `localDates`/`localTimes`/`localDateTimes`/`instants` + derivation
- [x] `arrays(componentType, element, length)` + array-component derivation (variable length)
- [x] `fixedDict(Map<String,Generator<?>>)`
- [x] `deferred(Supplier<Generator<T>>)` forward-reference / recursive combinator

## Derivation

- [x] Sealed-interface derivation: `oneOf` over `getPermittedSubclasses()`

## Larger features

- [x] Tier-1 stateful testing: `StateMachine`/`Rule`/`Stateful.run`
      (Variables/value-pool deliberately out of scope — blocked on engine pool API)
- [x] Explicit examples: `Settings.example(Map)` wired to the `EXPLICIT` phase

## Cross-cutting

- [x] README / GETTING_STARTED updated for new generators and stateful/explicit features
- [x] CLAUDE.md architecture notes updated

## Deferred — blocked on the engine (not in scope for this branch)

Stateful `Variables` value pools are **intentionally not implemented**: they require
`hegel_new_pool` / `hegel_pool_add` / `hegel_pool_generate` to be exported over the libhegel C ABI
(the engine's i128 pool/variable ids are being narrowed to `usize` first). The Tier-1 stateful
driver is built so this can be layered on once those hooks exist. This is the only known gap and it
is external to hegel-java.
