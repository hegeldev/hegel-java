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

    // Stateful testing. Machines are driven sequentially (one worker, one concurrency group).

    long newStateMachine(
            List<String> ruleNames, List<String> invariantNames, boolean[] invariantAlwaysCheck, int stepCount);

    /** Start the next round: its group id, or {@link Abi#STATE_MACHINE_DONE} when the machine is done. */
    long stateMachineNextGroup(long stateMachineId);

    /** The next rule index for this round, or {@link Abi#STATE_MACHINE_DONE} at the round's join point. */
    long stateMachineNextRule(long stateMachineId);

    /** The rule most recently handed out failed an assumption: do not count it as a step. */
    void stateMachineRuleRejected(long stateMachineId);

    /** The engine's sampling decision for invariant {@code invariantIndex} at this join point. */
    boolean stateMachineShouldCheckInvariant(long stateMachineId, long invariantIndex);

    /** Release the machine handle. Safe once the case is aborted. */
    void stateMachineFree(long stateMachineId);

    /** Whether the case has been concluded (overrun or invalid) and its primitives now short-circuit. */
    boolean isAborted();

    void target(double value, String label);
}
