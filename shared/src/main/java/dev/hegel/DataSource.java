package dev.hegel;

import dev.hegel.lowlevel.Libhegel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The per-test-case primitive surface that generators draw against.
 *
 * <p>Generators depend on this interface rather than {@link Libhegel} directly, so they can be
 * tested against a fake data source. Every method translates engine return codes: {@code STOP_TEST}
 * becomes {@link StopTest}, an assumption rejection becomes {@link AssumeRejected}, an invalid
 * argument becomes {@link IllegalArgumentException} carrying the engine's diagnostic, and any other
 * non-OK code becomes a {@link HegelException}.
 */
interface DataSource {
    boolean generateBoolean(double p);

    long generateInteger(long min, long max);

    double generateFloat(
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude);

    byte[] generateBytes(long minSize, long maxSize);

    String generateString(StringGeneratorHandle generator);

    LocalDate generateDate(LocalDate min, LocalDate max);

    LocalTime generateTime(LocalTime min, LocalTime max);

    LocalDateTime generateDatetime(LocalDateTime min, LocalDateTime max);

    UUID generateUuid(Integer version);

    byte[] generateIpv4();

    byte[] generateIpv6();

    // String-generator handle construction. Parameters are validated eagerly: a rejected
    // configuration throws IllegalArgumentException with the engine's diagnostic.
    StringGeneratorHandle textGenerator(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters);

    StringGeneratorHandle regexGenerator(String pattern, boolean fullmatch, StringGeneratorHandle alphabet);

    StringGeneratorHandle emailGenerator();

    StringGeneratorHandle urlGenerator();

    StringGeneratorHandle domainGenerator(long maxLength);

    /**
     * Whether {@code generator} was built by the binding behind this source. A cached handle from
     * another binding (e.g. after a test swapped the {@link Engine}) must be rebuilt, not drawn
     * from.
     */
    boolean ownsStringGenerator(StringGeneratorHandle generator);

    void startSpan(long label);

    void stopSpan(boolean discard);

    long newCollection(long minSize, long maxSize);

    boolean collectionMore(long id);

    void collectionReject(long id, String why);

    long newPool();

    long poolAdd(long poolId);

    long poolGenerate(long poolId, boolean consume);

    // Stateful testing. The root source registers the machine, advances rounds and samples
    // invariants; at concurrency > 1 each worker pulls its rules through its own clone.

    /** What registering a state machine yields: its handle and the concurrency level the engine drew. */
    record StateMachine(long id, int concurrency) {}

    /**
     * {@code ruleGroups} and {@code ruleWeights} are parallel to {@code ruleNames} ({@code null}
     * weights = all equal); {@code invariantAlwaysCheck} is parallel to {@code invariantNames}.
     * The engine draws the concurrency level in {@code [minConcurrency, maxConcurrency]}.
     */
    StateMachine newStateMachine(
            List<String> ruleNames,
            long[] ruleGroups,
            double[] ruleWeights,
            List<String> invariantNames,
            boolean[] invariantAlwaysCheck,
            long minConcurrency,
            long maxConcurrency,
            int stepCount);

    /** Start the next round: its group id, or {@link Abi#STATE_MACHINE_DONE} when the machine is done. */
    long stateMachineNextGroup(long stateMachineId);

    /**
     * The next rule index for worker {@code workerIndex} this round, or {@link
     * Abi#STATE_MACHINE_DONE} at the worker's join point.
     */
    long stateMachineNextRule(long stateMachineId, long workerIndex);

    /** The rule most recently handed to worker {@code workerIndex} failed an assumption: retry the slot. */
    void stateMachineRuleRejected(long stateMachineId, long workerIndex);

    /**
     * A source over an independent choice stream of the same test case ({@code
     * hegel_test_case_clone}), attributed to concurrent worker {@code workerIndex}, for a worker
     * thread to draw through. Release it with {@link #release()} once the worker is done with it.
     */
    DataSource cloneForWorker(long workerIndex);

    /** Free the handle behind a source from {@link #cloneForWorker}. Safe once the case is aborted. */
    void release();

    /** The engine's sampling decision for invariant {@code invariantIndex} at this join point. */
    boolean stateMachineShouldCheckInvariant(long stateMachineId, long invariantIndex);

    /** Release the machine handle. Safe once the case is aborted. */
    void stateMachineFree(long stateMachineId);

    /** Whether the case has been concluded (overrun or invalid) and its primitives now short-circuit. */
    boolean isAborted();

    void target(double value, String label);
}
