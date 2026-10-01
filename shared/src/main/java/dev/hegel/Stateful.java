package dev.hegel;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.LibhegelException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Stateful (model-based) testing: the engine picks which action runs next and this driver applies
 * it, so failing action sequences shrink like any other generated value.
 *
 * <p>Define a state-machine class whose {@link Rule @Rule} methods are the actions and whose
 * {@link Invariant @Invariant} methods are properties checked before the first action and after
 * each successful one. Both take a single {@link TestCase} parameter. Then run it inside a property
 * test:
 *
 * <pre>{@code
 * class IntegerStack {
 *   private final Deque<Integer> stack = new ArrayDeque<>();
 *
 *   @Rule
 *   void push(TestCase tc) {
 *     stack.push(tc.draw(integers()));
 *   }
 *
 *   @Rule
 *   void pop(TestCase tc) {
 *     tc.assume(!stack.isEmpty());
 *     stack.pop();
 *   }
 *
 *   @Invariant
 *   void sizeIsNonNegative(TestCase tc) {
 *     assertTrue(stack.size() >= 0);
 *   }
 * }
 *
 * @HegelTest
 * void stackBehaves(TestCase tc) {
 *   Stateful.run(new IntegerStack(), tc);
 * }
 * }</pre>
 *
 * <p>Each test case enables a random subset of rules (swarm testing) and runs an engine-chosen
 * number of steps. A rule that fails an assumption is skipped without counting as a step. Invariants
 * are checked in full on the machine's initial and final state and sampled in between: after any
 * given rule each invariant runs with probability {@code 1 / stepCount}, so its expected cost per
 * test case stays constant as the step count grows. The step count is the target number of rules
 * per test case, {@link #DEFAULT_STEP_COUNT} unless {@link Options#stepCount} sets another. Mark
 * an invariant {@code @Invariant(alwaysRun = true)} to check it after every rule instead. Give a
 * rule that should run more often than the others a {@link Rule#weight() weight}: {@code
 * @Rule(weight = 5)} is offered about five times as often as a plain {@code @Rule}. Use a {@link
 * Pool} to act on previously generated values.
 *
 * <h2>Concurrent machines</h2>
 *
 * <p>To look for concurrency bugs, run the machine with {@link Options#maxConcurrency} above 1:
 *
 * <pre>{@code
 * class Counter {
 *   private final Map<String, Integer> store = new ConcurrentHashMap<>();
 *   private final AtomicInteger increments = new AtomicInteger();
 *   private final ConcurrentPool<String> keys;
 *
 *   Counter(TestCase tc) {
 *     keys = new ConcurrentPool<>(tc);
 *   }
 *
 *   @Rule(group = "ops")
 *   void register(TestCase tc) {
 *     String key = tc.draw(text().minSize(1).maxSize(3));
 *     store.putIfAbsent(key, 0);
 *     keys.add(tc, key);
 *   }
 *
 *   @Rule(group = "ops", weight = 3)
 *   void increment(TestCase tc) {
 *     String key = tc.draw(keys.reusable());
 *     store.put(key, store.get(key) + 1); // racy: a lost update
 *     increments.incrementAndGet();
 *   }
 *
 *   @Rule(group = "audit")
 *   void audit(TestCase tc) {
 *     tc.note("store holds " + store.size() + " keys");
 *   }
 *
 *   @Invariant
 *   void noLostUpdates(TestCase tc) {
 *     int total = store.values().stream().mapToInt(Integer::intValue).sum();
 *     assertEquals(increments.get(), total);
 *   }
 * }
 *
 * @HegelTest
 * void counterUnderContention(TestCase tc) {
 *   Stateful.run(new Counter(tc), tc, Stateful.options().maxConcurrency(4));
 * }
 * }</pre>
 *
 * <p>For each test case the engine draws a concurrency level between {@link
 * Options#minConcurrency} and {@link Options#maxConcurrency} (weighted toward the maximum) and the
 * driver runs that many worker threads. Execution proceeds in <em>rounds</em>: the engine picks one
 * {@linkplain Rule#group() concurrency group} per round, every worker pulls and applies a few of
 * that group's rules concurrently, and once every worker has finished the round the sampled
 * invariants run on the driving thread. Rules in the same group may therefore overlap in time;
 * rules in different groups never do, and invariants never overlap a rule. The step count bounds
 * the number of rounds.
 *
 * <p>Rules run concurrently on the same machine object, so any state a rule reads or writes must
 * be safe for concurrent access (atomics, {@code synchronized}, concurrent collections). Each rule
 * receives its worker's own {@link TestCase}: draw only through it, never retain it past the rule
 * or share it with another worker, and use a {@link ConcurrentPool} rather than a {@link Pool} to
 * pass generated values between rules. Invariants receive the driving thread's test case. Rules
 * must not depend on their relative ordering within a round.
 *
 * <p>When a rule fails in some worker, the other workers finish their round before the failure is
 * reported, so a rule that blocks forever hangs the test case as it would sequentially. When
 * several workers fail in the same round the lowest-numbered worker's exception is reported and the
 * others are noted as dropped. The report groups each round's output by worker under the round's
 * header, each line stamped {@code [worker N +X.XXXms]} with the time since the machine started, so
 * it can be read across workers. A concurrency bug that depends on thread scheduling may not
 * reproduce on replay; the engine then confirms it by repeated replay and reports it with a
 * caveat (see {@link Failure#caveat()}).
 */
public final class Stateful {
    private Stateful() {}

    /**
     * The step count {@link #run(Object, TestCase)} uses: the conventional choice across Hegel
     * frontends.
     */
    public static final int DEFAULT_STEP_COUNT = 50;

    /** The concurrency group of every {@link Rule @Rule} that names none. */
    public static final String ANONYMOUS_GROUP = "<anonymous>";

    /**
     * How to run a machine: the step count and the concurrency bounds. Immutable; each setter
     * returns a new instance. Start from {@link Stateful#options()}.
     */
    public static final class Options {
        static final Options DEFAULT = new Options(DEFAULT_STEP_COUNT, 1, 1);

        final int stepCount;
        final int minConcurrency;
        final int maxConcurrency;

        private Options(int stepCount, int minConcurrency, int maxConcurrency) {
            this.stepCount = stepCount;
            this.minConcurrency = minConcurrency;
            this.maxConcurrency = maxConcurrency;
        }

        /**
         * The target number of steps per test case: rules for a sequential machine, rounds for a
         * concurrent one. Every test case runs at least one step and at most {@code stepCount};
         * the engine decides when to stop within that budget. After each step a sampled invariant
         * is checked with probability {@code 1 / stepCount}.
         *
         * @param stepCount the step budget, at least 1
         * @return options with the step count set
         * @throws IllegalArgumentException if {@code stepCount} is less than 1
         */
        public Options stepCount(int stepCount) {
            if (stepCount < 1) {
                throw new IllegalArgumentException("stepCount must be at least 1, got " + stepCount);
            }
            return new Options(stepCount, minConcurrency, maxConcurrency);
        }

        /**
         * The fewest worker threads a test case runs. Defaults to 1.
         *
         * @param minConcurrency the lower concurrency bound, at least 1
         * @return options with the bound set
         * @throws IllegalArgumentException if {@code minConcurrency} is less than 1
         */
        public Options minConcurrency(int minConcurrency) {
            if (minConcurrency < 1) {
                throw new IllegalArgumentException("minConcurrency must be at least 1, got " + minConcurrency);
            }
            return new Options(stepCount, minConcurrency, maxConcurrency);
        }

        /**
         * The most worker threads a test case runs. Defaults to 1, which runs the machine
         * sequentially on the calling thread; any higher value runs it concurrently (see {@link
         * Stateful}), with the engine drawing each test case's level between the bounds.
         *
         * @param maxConcurrency the upper concurrency bound, at least 1
         * @return options with the bound set
         * @throws IllegalArgumentException if {@code maxConcurrency} is less than 1
         */
        public Options maxConcurrency(int maxConcurrency) {
            if (maxConcurrency < 1) {
                throw new IllegalArgumentException("maxConcurrency must be at least 1, got " + maxConcurrency);
            }
            return new Options(stepCount, minConcurrency, maxConcurrency);
        }

        /**
         * @return the step budget per test case
         */
        public int stepCount() {
            return stepCount;
        }

        /**
         * @return the lower concurrency bound
         */
        public int minConcurrency() {
            return minConcurrency;
        }

        /**
         * @return the upper concurrency bound
         */
        public int maxConcurrency() {
            return maxConcurrency;
        }
    }

    /**
     * The default options: {@link #DEFAULT_STEP_COUNT} steps, sequential.
     *
     * @return the defaults, to refine with the {@link Options} setters
     */
    public static Options options() {
        return Options.DEFAULT;
    }

    /**
     * Run {@code machine}'s rules and invariants under {@code tc} for up to {@link
     * #DEFAULT_STEP_COUNT} steps, sequentially.
     *
     * <p>The machine's {@code @Rule}/{@code @Invariant} methods are discovered reflectively from
     * its class (superclass methods are not considered) and ordered by name, so rule numbering is
     * stable across JVMs.
     *
     * @param machine the state machine to drive
     * @param tc the current test case
     */
    public static void run(Object machine, TestCase tc) {
        run(machine, tc, Options.DEFAULT);
    }

    /**
     * Run {@code machine}'s rules and invariants under {@code tc} for up to {@code stepCount}
     * steps, sequentially. Equivalent to {@code run(machine, tc, options().stepCount(stepCount))}.
     *
     * @param machine the state machine to drive
     * @param tc the current test case
     * @param stepCount the target number of steps per test case, at least 1
     * @throws IllegalArgumentException if {@code stepCount} is less than 1
     */
    public static void run(Object machine, TestCase tc, int stepCount) {
        run(machine, tc, Options.DEFAULT.stepCount(stepCount));
    }

    /**
     * Run {@code machine}'s rules and invariants under {@code tc} as {@code options} say:
     * sequentially on the calling thread when {@link Options#maxConcurrency} is 1, on worker
     * threads otherwise (see {@link Stateful}).
     *
     * @param machine the state machine to drive
     * @param tc the current test case
     * @param options the step budget and concurrency bounds
     * @throws IllegalArgumentException if the machine has no rules, a rule or invariant has the
     *     wrong signature or an invalid weight, or {@code minConcurrency} exceeds {@code
     *     maxConcurrency}
     */
    public static void run(Object machine, TestCase tc, Options options) {
        if (options.minConcurrency > options.maxConcurrency) {
            throw new IllegalArgumentException("minConcurrency (" + options.minConcurrency
                    + ") must not exceed maxConcurrency (" + options.maxConcurrency + ")");
        }
        List<Method> rules = annotated(machine, Rule.class);
        List<Method> invariants = annotated(machine, Invariant.class);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    machine.getClass().getName() + " has no @Rule methods; a state machine needs at least one");
        }
        List<String> groupNames = new ArrayList<>();
        long[] groups = groups(rules, groupNames);
        DataSource.StateMachine sm = tc.newStateMachine(
                names(rules),
                groups,
                weights(rules),
                names(invariants),
                alwaysRun(invariants),
                options.minConcurrency,
                options.maxConcurrency,
                options.stepCount);
        try {
            if (options.maxConcurrency == 1) {
                driveSequential(machine, rules, invariants, tc, sm.id());
            } else {
                driveConcurrent(machine, rules, invariants, groupNames, tc, sm.id(), sm.concurrency());
            }
        } finally {
            tc.stateMachineFree(sm.id());
        }
    }

    /**
     * The engine's round-based protocol, driven sequentially: each round the engine picks the
     * current group ({@code next_group}), hands out that round's rules one at a time ({@code
     * next_rule} until the join point), and then samples which invariants to check. The start and
     * end of the machine are unconditional join points where every invariant runs.
     */
    private static void driveSequential(
            Object machine, List<Method> rules, List<Method> invariants, TestCase tc, long machineId) {
        if (!invariants.isEmpty()) {
            tc.note("Checking invariants on the initial state.");
        }
        checkInvariants(machine, invariants, tc, machineId, false);

        int step = 0;
        while (true) {
            tc.startSpan(Label.STATEFUL_RULE);
            if (tc.stateMachineNextGroup(machineId) == Abi.STATE_MACHINE_DONE) {
                tc.stopSpan(false);
                break;
            }
            // At concurrency 1 the engine hands out one rule per round, but that is engine policy,
            // not protocol: pull rules until the join point.
            boolean roundRejected = false;
            while (true) {
                long index = tc.stateMachineNextRule(machineId, 0);
                if (index == Abi.STATE_MACHINE_DONE) {
                    break;
                }
                Method rule = rules.get(ruleIndex(index, rules));
                step++;
                tc.note("Step " + step + ": " + rule.getName());
                try {
                    invokeMachineMethod(machine, rule, tc);
                } catch (AssumeRejected e) {
                    if (tc.isAborted()) {
                        // The engine itself concluded the case invalid (e.g. a draw inside the
                        // rule was rejected): the whole body unwinds, as for any failed assumption.
                        throw e;
                    }
                    // The rule's own precondition failed: tell the engine not to count it as a
                    // step, discard the round's span so it retries from before the step, and pull
                    // the next rule.
                    tc.stateMachineRuleRejected(machineId, 0);
                    roundRejected = true;
                    tc.note("Rule stopped early due to violated assumption.");
                } catch (RuntimeException | Error e) {
                    // Everything else — including StopTest, so an out-of-data case is reported as
                    // an overrun instead of returning normally with a half-applied rule — unwinds
                    // through the caller. stopSpan is a no-op when the case is already being torn
                    // down.
                    tc.stopSpan(false);
                    throw e;
                }
            }
            tc.stopSpan(roundRejected);
            checkInvariants(machine, invariants, tc, machineId, true);
        }

        if (!invariants.isEmpty()) {
            tc.note("Checking invariants on the final state.");
        }
        checkInvariants(machine, invariants, tc, machineId, false);
    }

    /**
     * The same protocol driven by worker threads, mirroring hegel-rust's concurrent runner. The
     * calling thread owns the root handle: it advances rounds, clones a fresh per-round handle for
     * each worker right after the round's header (so the round's lines land under it), waits for
     * every worker to report, absorbs their output, resolves the round's outcome and samples the
     * invariants. Workers persist for the whole test case and only ever touch their own handle.
     * No spans are opened: the engine owns rule structure through {@code next_rule} / {@code
     * rule_rejected}, and a round's draws are spread over several streams.
     */
    private static void driveConcurrent(
            Object machine,
            List<Method> rules,
            List<Method> invariants,
            List<String> groupNames,
            TestCase tc,
            long machineId,
            int concurrency) {
        long startNanos = System.nanoTime();
        tc.note("Concurrency level: " + concurrency);
        if (!invariants.isEmpty()) {
            tc.note("Checking invariants on the initial state.");
        }
        checkInvariants(machine, invariants, tc, machineId, false);

        Worker[] workers = new Worker[concurrency];
        for (int w = 0; w < concurrency; w++) {
            workers[w] = new Worker(w, machine, rules, machineId);
        }
        List<TestCase> handles = new ArrayList<>();
        try {
            for (Worker worker : workers) {
                worker.thread.start();
            }
            int round = 0;
            while (true) {
                long group = tc.stateMachineNextGroup(machineId);
                if (group == Abi.STATE_MACHINE_DONE) {
                    break;
                }
                round++;
                tc.note("---------------- Round " + round + ": group \"" + groupNames.get((int) group)
                        + "\" ----------------");
                for (Worker worker : workers) {
                    handles.add(tc.forWorker(worker.index, startNanos));
                }
                for (int w = 0; w < concurrency; w++) {
                    workers[w].inbox.add(handles.get(w));
                }
                WorkerEvent[] events = new WorkerEvent[concurrency];
                for (int w = 0; w < concurrency; w++) {
                    events[w] = workers[w].await();
                }
                // Every worker has reported, so no handle is in use any more: fold the round's
                // output into the report and release the clones before judging the round.
                for (TestCase handle : handles) {
                    tc.absorb(handle);
                    handle.release();
                }
                handles.clear();
                resolveRound(events, tc);
                checkInvariants(machine, invariants, tc, machineId, true);
            }
            if (!invariants.isEmpty()) {
                tc.note("Checking invariants on the final state.");
            }
            checkInvariants(machine, invariants, tc, machineId, false);
        } finally {
            // Stop the workers first: a handle may only be released once its worker is done.
            for (Worker worker : workers) {
                worker.stop();
            }
            for (TestCase handle : handles) {
                handle.release();
            }
        }
    }

    /**
     * Judge a finished round from its workers' events, in hegel-rust's precedence: a binding or
     * usage error (or a worker that died without reporting) is rethrown first; then an overrun or
     * an engine-level rejection concludes the whole case, dropping any failures found alongside
     * (an abandoned rule can leave the machine's locks and state inconsistent, so those are not
     * trustworthy); otherwise the lowest-numbered failing worker's exception is rethrown as-is and
     * the rest are noted as dropped.
     */
    private static void resolveRound(WorkerEvent[] events, TestCase tc) {
        for (WorkerEvent event : events) {
            if (event.kind == WorkerEvent.Kind.CONTROL) {
                throw event.error;
            }
        }
        boolean overrun = false;
        boolean invalid = false;
        for (WorkerEvent event : events) {
            overrun |= event.kind == WorkerEvent.Kind.OVERRUN;
            invalid |= event.kind == WorkerEvent.Kind.INVALID;
        }
        Throwable winner = null;
        for (int w = 0; w < events.length; w++) {
            WorkerEvent event = events[w];
            if (event.kind != WorkerEvent.Kind.FAILED) {
                continue;
            }
            if (overrun || invalid || winner != null) {
                tc.note("Dropped concurrent failure from worker " + w + ": " + event.failure);
            } else {
                winner = event.failure;
            }
        }
        if (overrun) {
            throw new StopTest();
        }
        if (invalid) {
            throw new AssumeRejected();
        }
        if (winner instanceof Error error) {
            throw error;
        }
        if (winner != null) {
            throw (RuntimeException) winner;
        }
    }

    /** What a worker reports at the end of a round. */
    private static final class WorkerEvent {
        enum Kind {
            /** The worker's rule stream is exhausted. */
            DONE,
            /** A binding or usage error, or the worker died: {@link #error} is rethrown verbatim. */
            CONTROL,
            /** The engine's choice budget ran out ({@link StopTest}). */
            OVERRUN,
            /** The engine concluded the case invalid ({@link AssumeRejected} outside a rule's own precondition). */
            INVALID,
            /** A rule threw: {@link #failure} is the property's failure. */
            FAILED
        }

        static final WorkerEvent DONE = new WorkerEvent(Kind.DONE, null, null);
        static final WorkerEvent OVERRUN = new WorkerEvent(Kind.OVERRUN, null, null);
        static final WorkerEvent INVALID = new WorkerEvent(Kind.INVALID, null, null);

        final Kind kind;
        final RuntimeException error;
        /** A {@link RuntimeException} or an {@link Error}: {@link #invokeMachineMethod} wraps everything else. */
        final Throwable failure;

        WorkerEvent(Kind kind, RuntimeException error, Throwable failure) {
            this.kind = kind;
            this.error = error;
            this.failure = failure;
        }

        static WorkerEvent control(RuntimeException error) {
            return new WorkerEvent(Kind.CONTROL, error, null);
        }

        static WorkerEvent failed(Throwable failure) {
            return new WorkerEvent(Kind.FAILED, null, failure);
        }
    }

    /**
     * A worker thread: per round it receives a handle through its inbox, pulls and applies rules
     * on it until the engine signals its join point, and reports one event through its outbox.
     * Whatever happens, it reports — a worker that exits its loop leaves a control error behind —
     * so the driving thread never waits forever on a live queue.
     */
    private static final class Worker {
        /** Inbox sentinel: the test case is over, exit. */
        private static final Object STOP = new Object();

        final int index;
        final Thread thread;
        final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
        final BlockingQueue<WorkerEvent> outbox = new LinkedBlockingQueue<>();
        private final Object machine;
        private final List<Method> rules;
        private final long machineId;

        Worker(int index, Object machine, List<Method> rules, long machineId) {
            this.index = index;
            this.machine = machine;
            this.rules = rules;
            this.machineId = machineId;
            this.thread = new Thread(this::loop, "hegel-worker-" + index);
            this.thread.setDaemon(true);
        }

        private void loop() {
            try {
                while (true) {
                    Object command = inbox.take();
                    if (command == STOP) {
                        return;
                    }
                    outbox.add(runRound((TestCase) command));
                }
            } catch (InterruptedException e) {
                // Interrupted between rounds (a rule set the flag): fall through and report it.
                Thread.currentThread().interrupt();
            } finally {
                // Whatever ended the loop, leave an event behind so the driver never waits forever.
                outbox.add(WorkerEvent.control(new HegelException(
                        "internal error: concurrent worker " + index + " exited without reporting its round")));
            }
        }

        /** Pull and apply rules on {@code wtc} until the engine ends this worker's round. */
        private WorkerEvent runRound(TestCase wtc) {
            try {
                while (true) {
                    long index = wtc.stateMachineNextRule(machineId, this.index);
                    if (index == Abi.STATE_MACHINE_DONE) {
                        return WorkerEvent.DONE;
                    }
                    Method rule = rules.get(ruleIndex(index, rules));
                    wtc.note("Rule: " + rule.getName());
                    try {
                        invokeMachineMethod(machine, rule, wtc);
                    } catch (AssumeRejected e) {
                        if (wtc.isAborted()) {
                            // The engine concluded the case invalid inside the rule, not the
                            // rule's own precondition: classified below.
                            throw e;
                        }
                        // The engine does not count the slot; the next next_rule retries it.
                        wtc.stateMachineRuleRejected(machineId, this.index);
                        wtc.note("Rule stopped early due to violated assumption.");
                    }
                }
            } catch (AssumeRejected e) {
                return WorkerEvent.INVALID;
            } catch (StopTest e) {
                return WorkerEvent.OVERRUN;
            } catch (LibhegelException | IllegalArgumentException e) {
                return WorkerEvent.control(e);
            } catch (Throwable e) {
                return WorkerEvent.failed(e);
            }
        }

        /** Block until this worker reports its round. */
        WorkerEvent await() {
            try {
                return outbox.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HegelException("interrupted while waiting for concurrent worker " + index, e);
            }
        }

        /** Tell the worker the test case is over and wait for it to exit. */
        void stop() {
            inbox.add(STOP);
            boolean interrupted = false;
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Run the invariants at a join point: all of them, or only those the engine samples in. */
    private static void checkInvariants(
            Object machine, List<Method> invariants, TestCase tc, long machineId, boolean sampled) {
        for (int i = 0; i < invariants.size(); i++) {
            if (sampled && !tc.stateMachineShouldCheckInvariant(machineId, i)) {
                continue;
            }
            invokeMachineMethod(machine, invariants.get(i), tc);
        }
    }

    private static int ruleIndex(long index, List<Method> rules) {
        if (index < 0 || index >= rules.size()) {
            throw new HegelException("internal error: state machine chose out-of-range rule index " + index);
        }
        return (int) index;
    }

    /**
     * Invoke a rule/invariant method, unwrapping reflection's exception wrapper so the method's own
     * exception propagates. A checked exception (which a rule may declare but this driver cannot
     * rethrow as-is) fails the property wrapped in a {@link RuntimeException} carrying it as cause.
     */
    private static void invokeMachineMethod(Object machine, Method method, TestCase tc) {
        try {
            invokeAccessible(machine, method, tc);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(cause);
        }
    }

    @Generated // IllegalAccessException is unreachable: annotated() made every machine method accessible.
    private static void invokeAccessible(Object machine, Method method, TestCase tc) throws InvocationTargetException {
        try {
            method.invoke(machine, tc);
        } catch (IllegalAccessException e) {
            throw new HegelException("failed to invoke " + method + "; is it accessible?", e);
        }
    }

    private static List<Method> annotated(Object machine, Class<? extends java.lang.annotation.Annotation> kind) {
        List<Method> methods = new ArrayList<>();
        for (Method m : machine.getClass().getDeclaredMethods()) {
            if (!m.isAnnotationPresent(kind)) {
                continue;
            }
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != TestCase.class) {
                throw new IllegalArgumentException(
                        "@" + kind.getSimpleName() + " method " + m + " must take a single TestCase parameter");
            }
            m.setAccessible(true);
            methods.add(m);
        }
        // getDeclaredMethods order is unspecified; sort so rule numbering is deterministic.
        methods.sort(Comparator.comparing(Method::getName));
        return methods;
    }

    private static List<String> names(List<Method> methods) {
        return methods.stream().map(Method::getName).toList();
    }

    /**
     * The rules' concurrency-group ids, parallel to {@code rules}: distinct group names numbered
     * from 0 in first-appearance order, appended to {@code groupNames} so the id indexes it. A
     * sequential machine's all-anonymous rules land in one group, id 0.
     */
    private static long[] groups(List<Method> rules, List<String> groupNames) {
        long[] ids = new long[rules.size()];
        for (int i = 0; i < ids.length; i++) {
            String group = rules.get(i).getAnnotation(Rule.class).group();
            if (group.isEmpty()) {
                group = ANONYMOUS_GROUP;
            }
            int id = groupNames.indexOf(group);
            if (id < 0) {
                id = groupNames.size();
                groupNames.add(group);
            }
            ids[i] = id;
        }
        return ids;
    }

    /**
     * The rules' selection weights, parallel to {@code rules}, or {@code null} — the engine's
     * all-equal default — when no rule asks for anything but weight 1.
     */
    private static double[] weights(List<Method> rules) {
        double[] weights = new double[rules.size()];
        boolean weighted = false;
        for (int i = 0; i < weights.length; i++) {
            Method rule = rules.get(i);
            double w = rule.getAnnotation(Rule.class).weight();
            if (!(Double.isFinite(w) && w > 0)) {
                throw new IllegalArgumentException(
                        "@Rule weight of " + rule.getName() + " must be finite and positive, got " + w);
            }
            weights[i] = w;
            weighted |= w != 1.0;
        }
        return weighted ? weights : null;
    }

    private static boolean[] alwaysRun(List<Method> invariants) {
        boolean[] flags = new boolean[invariants.size()];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = invariants.get(i).getAnnotation(Invariant.class).alwaysRun();
        }
        return flags;
    }
}
