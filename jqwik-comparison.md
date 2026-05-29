# hegel-java vs. jqwik: a porting-oriented comparison

A feature comparison between [jqwik](https://jqwik.net/) and hegel-java, written to answer one
question: **if someone has an existing jqwik test suite, what does it take to move it to
hegel-java, and where will they get stuck?**

The lens throughout is *common-case coverage* — what shows up in real jqwik suites, not an
exhaustive checklist of every annotation jqwik ships. Features are tagged:

- **Deliberate difference** — hegel-java does the same job a different way. Not a gap to close in
  the library; instead, the **porting skill** needs to know the translation. These are the bulk of
  the work in any port, but they're mechanical.
- **Genuine gap** — a capability hegel-java doesn't have and arguably should. Tracked for later.
- **Not needed** — jqwik exposes a knob that exists only because jqwik (not an engine) owns
  generation/shrinking. hegel-java's engine owns those decisions, so the knob has no analogue and
  shouldn't grow one.

The single genuine gap that matters for real suites is **statistics & coverage checking**
(§4). Everything else is either a deliberate API-shape difference with a clean translation, or a
small convenience.

---

## 1. The core API-shape difference: declarative `@ForAll` vs. imperative `draw`

**Deliberate difference.** This is the one structural change every ported test undergoes, so the
porting skill should treat it as the default transformation and apply it before worrying about
anything else.

jqwik injects generated values as method parameters, configured by annotations:

```java
@Property
void concatLength(@ForAll String a, @ForAll @Size(max = 10) List<@IntRange(min = 1) Integer> xs) {
    assertThat(a).isNotNull();
    assertThat(xs.size()).isLessThanOrEqualTo(10);
}
```

hegel-java draws values imperatively from a `TestCase`:

```java
@HegelTest
void concatLength(TestCase tc) {
    String a = tc.draw(Generators.text());
    List<Integer> xs = tc.draw(Generators.lists(Generators.integers(1, Integer.MAX_VALUE), 0, 10));
    assertThat(a).isNotNull();
    assertThat(xs.size()).isLessThanOrEqualTo(10);
}
```

### Translation rules for the porting skill

Each `@ForAll`-annotated parameter becomes a `tc.draw(...)` statement at the top of the body, in
declaration order. Build the generator from the parameter's type and its constraint annotations:

| jqwik parameter / annotation | hegel-java draw |
|---|---|
| `@ForAll int x` / `Integer` | `tc.draw(Generators.integers())` |
| `@ForAll @IntRange(min=a, max=b) int x` | `tc.draw(Generators.integers(a, b))` |
| `@ForAll long`, `@LongRange` | `Generators.longs(...)` |
| `@ForAll short` / `byte`, `@ShortRange` / `@ByteRange` | `Generators.shorts(...)` / `Generators.bytes(...)` |
| `@ForAll @Positive int` | `Generators.integers(1, Integer.MAX_VALUE)` |
| `@ForAll @Negative int` | `Generators.integers(Integer.MIN_VALUE, -1)` |
| `@ForAll double`, `@DoubleRange(min,max)` | `Generators.floats().min(a).max(b)` (`.excludeMin/.excludeMax` for exclusive bounds) |
| `@ForAll float` | `Generators.floats32()` |
| `@ForAll boolean` | `Generators.booleans()` |
| `@ForAll BigInteger`, `@BigRange` | `Generators.bigIntegers(min, max)` |
| `@ForAll String` | `Generators.text()` |
| `@ForAll @StringLength(min,max) String` | `Generators.text().minSize(min).maxSize(max)` |
| `@ForAll @AlphaChars String` | `Generators.text().categories("Lu", "Ll")` (or an explicit codepoint/char set) |
| `@ForAll @NumericChars String` | `Generators.text().categories("Nd")` |
| `@ForAll @CharRange(from,to) String` | `Generators.text().codepoints(from, to)` |
| `@ForAll @Chars({...}) String` | `Generators.text().includeCharacters("...")` with category exclusions, or build from `sampledFrom` |
| `@ForAll char` | `Generators.characters().map(s -> s.charAt(0))` |
| `@ForAll List<T>`, `@Size(min,max)` | `Generators.lists(elem, min, max)` |
| `@ForAll Set<T>` | `Generators.sets(elem, min, max)` |
| `@ForAll Map<K,V>` | `Generators.maps(keys, values, min, max)` |
| `@ForAll T[]` | `Generators.arrays(T.class, elem, length)` (see §5: fixed length only) |
| `@ForAll Optional<T>` | `Generators.optional(elem)` |
| `@ForAll @From("method") T` | inline the generator the `@Provide` method returns (see §3) |
| `@ForAll SomeRecord` / enum / sealed type | `Generators.forType(SomeRecord.class)` |

Notes:

- **`@WithNull(p)`** has no direct analogue (hegel generators don't inject `null`). Port to
  `Generators.oneOf(Generators.just(null), gen)` when the test genuinely needs nulls, or drop it if
  it was incidental.
- **`@NotBlank` / `@NotEmpty`** → add `.minSize(1)` (and a `.filter` for non-whitespace if the test
  relies on it).
- **`@UniqueElements`** → `Generators.sets(...)` when element identity is the uniqueness key;
  otherwise `.filter(...)` on a list. There's no feature-extractor form.
- **`@Scale`** applies to `BigDecimal`, which hegel-java doesn't generate — see §5.

---

## 2. `@Property` settings → `@HegelTest` / `Settings`

**Deliberate difference**, mostly a 1:1 mapping with a couple of "doesn't apply" cases.

| jqwik `@Property(...)` | hegel-java | Notes |
|---|---|---|
| `tries = N` | `@HegelTest(testCases = N)` or `Settings.testCases(N)` | jqwik default 1000; hegel default 100 — bump on port if the suite relied on volume. |
| `seed = "..."` | `@HegelTest(seed = ...)` or `Settings.seed(...)` | |
| `maxDiscardRatio = N` | `Settings.suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH)` to disable the guard | No exact ratio knob; the health check is the closest control. |
| `shrinking = OFF/BOUNDED/FULL` | `Settings.phases(...)` without `Phase.SHRINK` to disable | **Not needed** otherwise — engine owns shrinking. |
| `generation = RANDOMIZED` | default | |
| `generation = EXHAUSTIVE` | — | **Genuine gap** for the few tests that depend on exhaustiveness; re-express as ordinary randomized properties (see §6). |
| `generation = DATA_DRIVEN` | `Settings.example(...)` + `Phase.EXPLICIT` | See §3 (data-driven). |
| `edgeCases = MIXIN/FIRST/NONE` | — | **Not needed** — engine decides edge-case mixing. |
| `afterFailure = ...` | database replay (`Settings.database` / `noDatabase`) | hegel replays via its example DB / `Phase.REUSE`; no per-mode selector. |

`@Example` (jqwik's `tries = 1` single run) ports to an ordinary JUnit test that calls
`Hegel.check(...)` once, or to a registered explicit example (§3).

Organisational annotations — `@Label`, `@Tag`, `@Group`, `@Disabled` — are plain JUnit 5 features;
keep `@Tag`/`@Disabled` as-is and use JUnit's `@Nested` for `@Group`, `@DisplayName` for `@Label`.

---

## 3. Generators / arbitraries and combinators

hegel-java's `Generators` facade covers the common jqwik `Arbitraries` surface well. The combinator
set (`map`, `filter`, `flatMap`) matches jqwik's core, and `deferred(...)` covers
`Arbitraries.lazy`/`recursive`. Mapping the rest:

| jqwik | hegel-java | Status |
|---|---|---|
| `Arbitraries.of(...)` | `Generators.sampledFrom(...)` | ✅ |
| `Arbitraries.just(v)` | `Generators.just(v)` | ✅ |
| `Arbitraries.oneOf(a, b, ...)` | `Generators.oneOf(a, b, ...)` | ✅ |
| `Arbitraries.frequency((w,v)...)` / `frequencyOf` | — | **Genuine gap** — no weighted choice (see §5). |
| `Arbitraries.integers().between(a,b)` etc. | `Generators.integers(a, b)` | ✅ |
| `.shrinkTowards(t)` | — | **Not needed** — engine owns shrink targets. |
| `.withDistribution(...)` / `withSizeDistribution` / `fixGenSize` | — | **Not needed** — engine owns distribution/size. |
| `Arbitraries.strings()....` | `Generators.text()...` | ✅ (rich Unicode category/codepoint control) |
| `Arbitraries.chars()` | `Generators.characters()` | ✅ (returns 1-char strings) |
| `Arbitraries.bigIntegers()` | `Generators.bigIntegers(min, max)` | ✅ (bounds required) |
| `Arbitraries.bigDecimals()` | — | **Genuine gap** (see §5). |
| `Arbitraries.maps(k, v)` | `Generators.maps(k, v[, min, max])` | ✅ |
| `Arbitrary.list()/set()/array()` | `Generators.lists/sets/arrays` | ✅ (array is fixed-length — §5) |
| `Arbitrary.stream()/iterator()` | — | minor gap; map a list to a stream/iterator. |
| `Arbitraries.combine(...).as(...)` | `Generators.compose(tc -> ...)` or nested `flatMap` | ✅ (imperative form) |
| `Builders.withBuilder(...)` | `Generators.compose(...)` | ✅ via imperative build |
| `Arbitraries.shuffle(...)` | — | minor gap; `compose` + manual shuffle, or `flatMap`. |
| `Arbitraries.randoms()` | — | minor gap; rarely used in properties. |
| `.injectNull(p)` / `.injectDuplicates(p)` | — | minor; `oneOf(just(null), gen)` for the former. |
| `.optional(p)` | `Generators.optional(gen)` | ✅ (no probability knob) |
| `Functions.function(...)` | — | **Genuine gap** (uncommon; see §5). |
| `Arbitraries.forType(...)` / `@UseType` | `Generators.forType(Class)` | ✅ (records, enums, sealed, java.time, collections) |

### `@Provide` methods

**Deliberate difference.** jqwik resolves `@ForAll("name")` to a `@Provide Arbitrary<T> name()`
method by string. hegel-java has no such wiring: turn each `@Provide` method into a `Generator<T>`
returned by an ordinary helper method or held in a field, and reference it directly in the
`tc.draw(...)` call. `@From("name")` and `@ForAll(supplier = X.class)` translate the same way —
inline the generator the provider/supplier produced.

### Data-driven properties (`@FromData` + `Table.of`)

**Deliberate difference**, but a clunky one. jqwik runs the body once per row of an explicit table.
hegel-java's analogue is `Settings.example(Map<String,Object>)` (replayed in `Phase.EXPLICIT`),
which is **label-keyed and one example per call** and requires the body to use *labelled* draws
(`tc.draw(gen, "label")`). Porting an N-row table means N `example(...)` registrations plus labelled
draws. For pure table-driven tests with no generation, a plain JUnit `@ParameterizedTest` is often
the cleaner destination — the porting skill should prefer that when the jqwik "property" was really
just a data table.

---

## 4. Statistics & coverage — the one genuine feature gap that matters

**Genuine gap. Flagged as a major feature for later.**

jqwik's statistics API is widely used in real suites, and crucially it can carry *assertions*: a
property fails if a distribution isn't met.

```java
@Property
void shapeDistribution(@ForAll("rectangles") Rectangle r) {
    Statistics.label("orientation")
              .collect(r.width() > r.height() ? "landscape" : "portrait");
    Statistics.coverage(c -> {
        c.check("landscape").percentage(p -> p > 30.0);
        c.check("portrait").percentage(p -> p > 30.0);
    });
}
```

hegel-java has **no equivalent** to:

- `Statistics.collect(...)` / `Statistics.label(...)` — sample classification and tallying.
- `@StatisticsReport` / histogram output.
- `Statistics.coverage(...)` / `checkCoverage` — coverage as a *pass/fail condition*.

What exists today is adjacent but not a substitute: `TestCase.note(...)` (a debug string on the
final replay only) and `TestCase.target(...)` (a hill-climbing score for the search, not a tally or
an assertion). Tests that *assert* coverage cannot be ported at all; tests that merely *report*
distributions lose their reporting.

This is the recommended item to build for real feature parity. A minimal useful version is a
`TestCase.collect(label, value)` (or a `Statistics`-style facade) that tallies across the run plus a
post-run coverage assertion. Until then, the porting skill should flag any
`Statistics.collect`/`coverage` usage as **needs manual attention** rather than attempting a
mechanical translation.

---

## 5. Smaller genuine gaps (low effort, occasionally hit)

These come up often enough to note, but each is small:

- **Weighted choice.** No `Arbitraries.frequency` / `frequencyOf`. `oneOf` is uniform only. Common
  in jqwik suites that bias toward certain shapes. Worth adding a `Generators.frequency(...)`.
- **`BigDecimal`.** `bigIntegers` exists; `bigDecimals` does not, so `@Scale` and
  `Arbitraries.bigDecimals()` have nothing to map to.
- **Variable-length arrays.** `Generators.arrays(Class, elem, length)` is **fixed-length only**.
  jqwik's `Arbitrary.array(...)` is variable-length. (Derivation via `forType` does handle
  variable-length arrays, so the capability exists in the engine — only the explicit factory is
  fixed.) Port variable arrays via `lists(...).map(List::toArray)` for now.
- **`Functions.function(...)`** — generating deterministic functional-interface implementations.
  Uncommon, but unportable when present.
- **`Stream` / `Iterator` element generation** — trivial to derive from a list; minor.
- **Direct sampling outside a property** (`Arbitrary.sample()` / `sampleStream()` / `JqwikSession`).
  hegel-java's `Settings.singleTestCase(...)` probe is close but not a clean "give me one value / a
  stream of values" API. Occasionally used for fixtures/exploration.

---

## 6. Deliberately not needed (don't build these)

These jqwik knobs exist because jqwik itself owns generation and shrinking. hegel-java delegates
those to the engine, so there's no place to hang them and adding them would fight the design:

- `ShrinkingMode.OFF/BOUNDED/FULL`, `shrinkTowards` — engine owns shrinking. (`Phase.SHRINK` can be
  toggled off wholesale if a test truly needs raw failures.)
- `EdgeCasesMode.MIXIN/FIRST/NONE` and custom edge-case configuration — engine owns edge cases.
- `withDistribution` / `withSizeDistribution` / `RandomDistribution` (gaussian/biased/uniform) /
  `fixGenSize` — engine owns the distribution and adaptive sizing.
- `generation = EXHAUSTIVE` — the engine is randomized + coverage-guided, not exhaustive. The rare
  test that depends on exhaustiveness (e.g. "check every value in a small enum") should be ported as
  an ordinary property over `sampledFrom`/`forType` with enough `testCases` to cover the space, or
  as a plain JUnit loop. This is the one "not needed" item with real porting consequences, so the
  skill should call it out.

---

## 7. Lifecycle

**Mostly deliberate difference.** jqwik's container/property-level hooks map to standard JUnit 5:

| jqwik | hegel-java (via JUnit 5) |
|---|---|
| `@BeforeContainer` / `@AfterContainer` | `@BeforeAll` / `@AfterAll` |
| `@BeforeProperty` / `@AfterProperty` | `@BeforeEach` / `@AfterEach` |
| `@BeforeTry` / `@AfterTry` | **no analogue** |

The one real gap is **per-try** setup/teardown: jqwik runs `@BeforeTry`/`@AfterTry` around every
generated case, whereas hegel-java's `@BeforeEach`/`@AfterEach` run once per `@HegelTest` (the whole
property), not per case. Port per-try logic into the test body itself (run it at the top/bottom of
the `@HegelTest` method, which executes per case) — usually a clean translation, occasionally
awkward when the jqwik test reset field state via `@BeforeTry`.

`PerProperty` / `AddLifecycleHook` / custom lifecycle hooks have no analogue and are rare in
ordinary suites; treat as needs-manual-attention if encountered.

---

## 8. Stateful testing

**Close match.** jqwik's `Action` / `ActionSequence` / invariant model maps cleanly onto
hegel-java's `StateMachine` (`rules()` + `invariants()`), `Rule.of(name, action)`, and
`Stateful.run(tc, machine)`. The one missing piece is jqwik-style typed **value pools** — hegel
defers `Variables` until libhegel exposes a pool ABI — so hold model state in the `StateMachine`
instance instead of in a pool. For the common case (a model object plus rules that mutate it), the
port is direct.

---

## Summary

For a typical jqwik suite, a port is **mostly mechanical**: rewrite `@ForAll` parameters as
`tc.draw(...)` (§1), map `@Property` settings (§2), inline `@Provide` generators (§3), and translate
lifecycle to JUnit (§7). The friction points that need human judgement, in rough order of how often
they bite:

1. **Statistics / coverage assertions (§4)** — the only genuine feature gap of consequence; build
   later, flag on port until then.
2. **Per-try lifecycle (§7)** — fold into the body.
3. **Data-driven tables (§3)** — often better as JUnit `@ParameterizedTest`.
4. **Weighted choice, `BigDecimal`, variable arrays (§5)** — small library additions.
5. **Exhaustive generation (§6)** — re-express as randomized.

Everything else is a deliberate, mechanical translation that belongs in the porting skill.
