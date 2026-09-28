package dev.hegel;

import dev.hegel.lowlevel.Abi;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * per test case, {@link #DEFAULT_STEP_COUNT} unless {@link #run(Object, TestCase, int)} is given
 * one. Mark an invariant {@code @Invariant(alwaysRun =
 * true)} to check it after every rule instead. Give a rule that should run more often than the
 * others a {@link Rule#weight() weight}: {@code @Rule(weight = 5)} is offered about five times as
 * often as a plain {@code @Rule}. Use a {@link Pool} to act on previously generated values.
 */
public final class Stateful {
    private Stateful() {}

    /**
     * The step count {@link #run(Object, TestCase)} uses: the conventional choice across Hegel
     * frontends.
     */
    public static final int DEFAULT_STEP_COUNT = 50;

    /**
     * Run {@code machine}'s rules and invariants under {@code tc} for up to {@link
     * #DEFAULT_STEP_COUNT} steps.
     *
     * <p>The machine's {@code @Rule}/{@code @Invariant} methods are discovered reflectively from
     * its class (superclass methods are not considered) and ordered by name, so rule numbering is
     * stable across JVMs.
     *
     * @param machine the state machine to drive
     * @param tc the current test case
     */
    public static void run(Object machine, TestCase tc) {
        run(machine, tc, DEFAULT_STEP_COUNT);
    }

    /**
     * Run {@code machine}'s rules and invariants under {@code tc} for up to {@code stepCount}
     * steps.
     *
     * <p>Every test case runs at least one step and at most {@code stepCount}; the engine decides
     * when to stop within that budget. After each step a sampled invariant is checked with
     * probability {@code 1 / stepCount}.
     *
     * @param machine the state machine to drive
     * @param tc the current test case
     * @param stepCount the target number of steps per test case, at least 1
     * @throws IllegalArgumentException if {@code stepCount} is less than 1
     */
    public static void run(Object machine, TestCase tc, int stepCount) {
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be at least 1, got " + stepCount);
        }
        List<Method> rules = annotated(machine, Rule.class);
        List<Method> invariants = annotated(machine, Invariant.class);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    machine.getClass().getName() + " has no @Rule methods; a state machine needs at least one");
        }
        long machineId =
                tc.newStateMachine(names(rules), weights(rules), names(invariants), alwaysRun(invariants), stepCount);
        try {
            drive(machine, rules, invariants, tc, machineId);
        } finally {
            tc.stateMachineFree(machineId);
        }
    }

    /**
     * The engine's round-based protocol, driven sequentially: each round the engine picks the
     * current group ({@code next_group}), hands out that round's rules one at a time ({@code
     * next_rule} until the join point), and then samples which invariants to check. The start and
     * end of the machine are unconditional join points where every invariant runs.
     */
    private static void drive(
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
                long index = tc.stateMachineNextRule(machineId);
                if (index == Abi.STATE_MACHINE_DONE) {
                    break;
                }
                if (index < 0 || index >= rules.size()) {
                    throw new HegelException("internal error: state machine chose out-of-range rule index " + index);
                }
                Method rule = rules.get((int) index);
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
                    tc.stateMachineRuleRejected(machineId);
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
     * Run the invariants at a join point: all of them for the guaranteed initial/final checks, or
     * only those the engine samples in (always-run invariants unconditionally) when {@code
     * sampled}.
     */
    private static void checkInvariants(
            Object machine, List<Method> invariants, TestCase tc, long machineId, boolean sampled) {
        for (int i = 0; i < invariants.size(); i++) {
            if (sampled && !tc.stateMachineShouldCheckInvariant(machineId, i)) {
                continue;
            }
            invokeMachineMethod(machine, invariants.get(i), tc);
        }
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
