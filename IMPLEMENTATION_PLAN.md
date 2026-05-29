# hegel-java feature-parity plan

Bring hegel-java up to feature parity with hegel-rust / hegel-go, **except** the stateful
`Variables`/value-pool feature, which is blocked on a new engine C ABI (the pool API is being added
to libhegel separately).

Baseline before this work: 130 tests, 100% instruction+branch coverage, conformance + lint + docs
all green.

Each item must keep `just check` (lint + 100% coverage + docs) and `just conformance` green, and is
committed independently.

## Self-contained generator additions

- [ ] `fromRegex(pattern, fullmatch)` overload (schema `fullmatch` bool, default false)
- [ ] Wider numerics: `bytes()`/`bytes(min,max)` → `Byte`, `shorts()`/`shorts(min,max)` → `Short`
      (maps over `integers`), `bigIntegers(min,max)` → `BigInteger` (new basic gen; engine requires
      both bounds)
- [ ] `float32`: `FloatGenerator.asFloat()` → `Generator<Float>` (schema `width:32`), plus
      `Generators.floats32()` convenience
- [ ] `durations()` / `durations(min,max)` → `java.time.Duration` (client-side integer nanos)
- [ ] Native `java.time`: `localDates()`, `localTimes()`, `localDateTimes()`, `instants()` (map the
      engine's ISO format strings; verify exact layout against hegel-go) + derivation for
      `LocalDate`/`LocalTime`/`LocalDateTime`/`Instant`/`Duration`
- [ ] `arrays(componentType, element, length)` → `Generator<T[]>` (fixed length over list schema);
      derivation for array-typed components (variable length)
- [ ] `fixedDict(Map<String,Generator<?>>)` → `Generator<Map<String,Object>>` (basic via tuple
      schema when all fields basic; composite otherwise)
- [ ] `deferred(Supplier<Generator<T>>)` forward-reference / recursive combinator

## Derivation

- [ ] Sealed-interface derivation: `oneOf` over `getPermittedSubclasses()`

## Larger features

- [ ] Tier-1 stateful testing: `StateMachine` interface (`rules()`/`invariants()`), `Rule`,
      `Stateful.run(tc, machine)` — draw step count + rule index, apply in a STATEFUL span, recover
      `assume` rejections, check invariants after each step (mirrors hegel-go `RunStateful`).
      **Variables/value-pool deliberately out of scope** (blocked on engine pool API).
- [ ] Explicit examples: wire up the inert `EXPLICIT` phase. `Settings.example(Map<String,Object>)`
      replays the body with preset label→value draws (no engine), gated on the EXPLICIT phase, run
      before the generation loop (mirrors Rust's `ExplicitTestCase`).

## Cross-cutting

- [ ] README / docs updated for new generators and stateful/explicit features
- [ ] CLAUDE.md architecture notes updated
