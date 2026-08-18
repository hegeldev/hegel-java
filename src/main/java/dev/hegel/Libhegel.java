package dev.hegel;

import java.lang.foreign.MemorySegment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * The libhegel binding surface, as a table of operations.
 *
 * <p>Modelled as an interface so tests can substitute a fake binding that returns chosen return
 * codes, exercising every error path without the real engine. The production implementation is
 * {@link RealLibhegel}, which drives libhegel over the Foreign Function and Memory API.
 *
 * <p>Opaque handles ({@code hegel_settings_t*}, {@code hegel_run_t*}, {@code hegel_test_case_t*},
 * {@code hegel_run_result_t*}, {@code hegel_string_generator_t*}) are passed as {@link
 * MemorySegment}; callers treat them as opaque and never dereference them. Handles are caller-owned:
 * every handle a method returns must be released with its matching {@code *Free} method.
 *
 * <p>Two calling conventions coexist here, mirroring how the frontend consumes the ABI:
 *
 * <ul>
 *   <li>Per-test-case primitives (draws, spans, collections, pools, state machines, {@code target},
 *       {@code markComplete}) and the string-generator constructors return the raw libhegel return
 *       code; {@link LiveDataSource} translates it and reads {@link #lastErrorMessage()} immediately
 *       on a non-OK code. Out-values are written into caller-supplied one-element arrays only on
 *       {@link Abi#OK} (except where noted).
 *   <li>Infrastructure calls (settings construction and setters, run lifecycle, result readers,
 *       frees) cannot legitimately fail with the arguments this binding passes, so implementations
 *       check the return code themselves and throw {@link HegelException} on an unexpected non-OK
 *       code.
 * </ul>
 *
 * <p>Strings the engine returns are copied out before the method returns, so they remain valid.
 */
interface Libhegel {
    // Settings. Setters cannot fail with this binding's inputs; implementations throw on non-OK.
    MemorySegment settingsNew();

    void settingsFree(MemorySegment s);

    void settingsMode(MemorySegment s, int mode);

    void settingsBackend(MemorySegment s, int backend);

    void settingsTestCases(MemorySegment s, long n);

    void settingsVerbosity(MemorySegment s, int v);

    void settingsSeed(MemorySegment s, long seed, boolean hasSeed);

    void settingsDerandomize(MemorySegment s, boolean derandomize);

    void settingsReportMultipleFailures(MemorySegment s, boolean yes);

    /**
     * {@code path == null} leaves the engine default; {@code ""} disables; otherwise sets the dir.
     */
    void settingsDatabase(MemorySegment s, String path);

    void settingsDatabaseKey(MemorySegment s, String key);

    void settingsPhases(MemorySegment s, int mask);

    void settingsSuppressHealthCheck(MemorySegment s, int mask);

    // Run lifecycle.

    /**
     * Start a run. Engine output (progress lines, verbose traces) is delivered per line to {@code
     * output}; {@code null} leaves it on stderr. The callback stays registered until {@link
     * #runFree}.
     */
    MemorySegment runStart(MemorySegment settings, Consumer<String> output);

    /** The next test-case handle, or {@code NULL} once the run is finished. */
    MemorySegment nextTestCase(MemorySegment run);

    /** A caller-owned snapshot of the finished run's result; release with {@link #runResultFree}. */
    MemorySegment runResult(MemorySegment run);

    void runResultFree(MemorySegment result);

    void runFree(MemorySegment run);

    /**
     * Replay a base64 reproduce blob as a standalone test case. Returns the raw rc ({@link
     * Abi#E_INVALID_ARG} for a corrupt or incompatible blob); on OK, {@code out[0]} receives the
     * caller-owned handle. {@code output} has the same contract as in {@link #runStart} but need not
     * outlive the call.
     */
    int testCaseFromBlob(MemorySegment settings, String blob, Consumer<String> output, MemorySegment[] out);

    void testCaseFree(MemorySegment tc);

    // Per-test-case draws. Each returns the raw rc.
    int generateBoolean(MemorySegment tc, double p, boolean[] out);

    int generateInteger(MemorySegment tc, long min, long max, long[] out);

    int generateFloat(
            MemorySegment tc,
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude,
            double[] out);

    int generateBytes(MemorySegment tc, long minSize, long maxSize, byte[][] out);

    int generateString(MemorySegment tc, MemorySegment generator, String[] out);

    int generateDate(MemorySegment tc, LocalDate min, LocalDate max, LocalDate[] out);

    /** Time bounds and results are at microsecond resolution (the engine's granularity). */
    int generateTime(MemorySegment tc, LocalTime min, LocalTime max, LocalTime[] out);

    int generateDatetime(MemorySegment tc, LocalDateTime min, LocalDateTime max, LocalDateTime[] out);

    /** On OK writes the UUID's 16 big-endian bytes into {@code out16}. */
    int generateUuid(MemorySegment tc, int version, boolean hasVersion, byte[] out16);

    /** On OK writes the address's 4 network-order bytes into {@code out4}. */
    int generateIpv4(MemorySegment tc, byte[] out4);

    /** On OK writes the address's 16 network-order bytes into {@code out16}. */
    int generateIpv6(MemorySegment tc, byte[] out16);

    // String-generator handles. Constructors return the raw rc (INVALID_ARG for a configuration
    // that is rejected, e.g. an empty alphabet with max_size > 0); handles are released with
    // stringGeneratorFree.
    int stringGeneratorText(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters,
            MemorySegment[] out);

    int stringGeneratorRegex(String pattern, boolean fullmatch, MemorySegment alphabet, MemorySegment[] out);

    int stringGeneratorEmail(MemorySegment[] out);

    int stringGeneratorUrl(MemorySegment[] out);

    int stringGeneratorDomain(long maxLength, MemorySegment[] out);

    void stringGeneratorFree(MemorySegment generator);

    // Structure: spans, collections, pools, state machines. Each returns the raw rc.
    int startSpan(MemorySegment tc, long label);

    int stopSpan(MemorySegment tc, boolean discard);

    int newCollection(MemorySegment tc, long minSize, long maxSize, long[] outId);

    int collectionMore(MemorySegment tc, long id, boolean[] outMore);

    int collectionReject(MemorySegment tc, long id, String why);

    int newPool(MemorySegment tc, long[] outId);

    int poolAdd(MemorySegment tc, long poolId, long[] outVariableId);

    int poolGenerate(MemorySegment tc, long poolId, boolean consume, long[] outVariableId);

    int newStateMachine(MemorySegment tc, List<String> ruleNames, List<String> invariantNames, long[] outId);

    /** {@code outRuleIndex[0]} receives the rule index, or {@link Abi#STATE_MACHINE_DONE}. */
    int stateMachineNextRule(MemorySegment tc, long stateMachineId, long[] outRuleIndex);

    int target(MemorySegment tc, double value, String label);

    int markComplete(MemorySegment tc, int status, String origin);

    // Results.
    int runResultStatus(MemorySegment result);

    /** The run-level error message, or {@code null} when the run completed normally. */
    String runResultError(MemorySegment result);

    long runResultFailureCount(MemorySegment result);

    /**
     * The reproduce blob of the {@code index}-th distinct failure, or {@code null} if libhegel
     * produced none for it.
     */
    String failureBlob(MemorySegment result, long index);

    // Diagnostics.
    String lastErrorMessage();

    String version();
}
