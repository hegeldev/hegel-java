RELEASE_TYPE: minor

This release adds concurrent stateful testing. Running a state machine with
`Stateful.Options.maxConcurrency` above 1 makes the engine draw a concurrency level for each test
case and the driver run that many worker threads, which apply the machine's rules at the same time
on the same machine object, so races and lost updates surface as invariant failures like any other
bug:

```java
class Counter {
  private final Map<String, Integer> store = new ConcurrentHashMap<>();
  private final AtomicInteger increments = new AtomicInteger();
  private final ConcurrentPool<String> keys;

  Counter(TestCase tc) {
    keys = new ConcurrentPool<>(tc);
  }

  @Rule(group = "ops")
  void register(TestCase tc) {
    String key = tc.draw(text().minSize(1).maxSize(3));
    store.putIfAbsent(key, 0);
    keys.add(tc, key);
  }

  @Rule(group = "ops", weight = 3)
  void increment(TestCase tc) {
    String key = tc.draw(keys.reusable());
    store.put(key, store.get(key) + 1); // racy: a lost update
    increments.incrementAndGet();
  }

  @Rule(group = "audit")
  void audit(TestCase tc) {
    tc.note("store holds " + store.size() + " keys");
  }

  @Invariant
  void noLostUpdates(TestCase tc) {
    assertEquals(increments.get(), store.values().stream().mapToInt(Integer::intValue).sum());
  }
}

@HegelTest
void counterUnderContention(TestCase tc) {
  Stateful.run(new Counter(tc), tc, Stateful.options().maxConcurrency(4));
}
```

Execution proceeds in rounds. Each round the engine picks one concurrency group (`@Rule(group =
"...")`; rules that name none share the anonymous group), every worker applies a few of that group's
rules concurrently, and once all workers have finished the round the sampled invariants run on the
driving thread. Rules in the same group may therefore overlap in time, rules in different groups
never do, and invariants never overlap a rule. Rules run on the same machine object from several
threads, so its state must be safe for concurrent access; each rule receives its worker's own
`TestCase` and must draw only through it. The new `ConcurrentPool` is the thread-safe counterpart of
`Pool` for passing generated values between concurrent rules; its `add` takes the calling rule's test
case. A failure report groups each round's draws and notes by worker under the round's header, every
line stamped `[worker N +X.XXXms]` with the time since the machine started:

```
Concurrency level: 2
---------------- Round 1: group "ops" ----------------
[worker 0 +0.412ms] Rule: increment
[worker 0 +0.437ms] draw_1 = "a";
[worker 1 +0.415ms] Rule: increment
[worker 1 +0.440ms] draw_2 = "a";

java.lang.AssertionError: expected: <2> but was: <1>
```

When a rule fails in one worker the other workers finish their round before the failure is
reported, and when several workers fail in the same round the lowest-numbered worker's exception is
reported with the others noted as dropped. A concurrency bug that depends on thread scheduling may
not reproduce on every replay; the engine then confirms it by repeated replay and reports it with a
caveat, as for any nondeterministic failure.

`Stateful.options()` is the new way to configure a run:
`Stateful.run(machine, tc, Stateful.options().stepCount(30).minConcurrency(2).maxConcurrency(4))`.
The existing `run(machine, tc)` and `run(machine, tc, stepCount)` overloads are unchanged and keep
running the machine sequentially on the calling thread, with the same output and choice sequences
as before, so stored failures for existing stateful tests remain valid.

In `dev.hegel:hegel-lowlevel`, `Libhegel` gains `testCaseClone` (`hegel_test_case_clone`; a draw,
so it returns the raw return code and reports `E_STOP_TEST` on a replay of a shorter sequence) and
`testCaseSetWorker` (`hegel_test_case_set_worker`). Bindings implementing `Libhegel` must add the
two methods, which is what makes this a minor release; the `dev.hegel:hegel` and
`dev.hegel:hegel-jna` frontends only gain API.
