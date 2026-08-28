package dev.hegel;

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
 * {@code RealLibhegel} (Foreign Function and Memory API, in {@code hegel}) or {@code JnaLibhegel}
 * (JNA, in {@code hegel-jna}).
 *
 * <p>Opaque handles ({@code hegel_settings_t*}, {@code hegel_run_t*}, {@code hegel_test_case_t*},
 * {@code hegel_run_result_t*}, {@code hegel_string_generator_t*}) are passed as raw addresses
 * ({@code long}; {@code 0} is NULL); callers treat them as opaque and never dereference them.
 * Handles are caller-owned: every handle a method returns must be released with its matching
 * {@code *Free} method.
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
    long settingsNew();

    void settingsFree(long s);

    void settingsMode(long s, int mode);

    void settingsBackend(long s, int backend);

    void settingsTestCases(long s, long n);

    void settingsVerbosity(long s, int v);

    void settingsSeed(long s, long seed, boolean hasSeed);

    void settingsDerandomize(long s, boolean derandomize);

    void settingsReportMultipleFailures(long s, boolean yes);

    /**
     * {@code path == null} leaves the engine default; {@code ""} disables; otherwise sets the dir.
     */
    void settingsDatabase(long s, String path);

    void settingsDatabaseKey(long s, String key);

    void settingsPhases(long s, int mask);

    void settingsSuppressHealthCheck(long s, int mask);

    // Run lifecycle.

    /**
     * Start a run. Engine output (progress lines, verbose traces) is delivered per line to {@code
     * output}; {@code null} leaves it on stderr. The callback stays registered until {@link
     * #runFree}.
     */
    long runStart(long settings, Consumer<String> output);

    /** The next test-case handle, or {@code 0} once the run is finished. */
    long nextTestCase(long run);

    /** A caller-owned snapshot of the finished run's result; release with {@link #runResultFree}. */
    long runResult(long run);

    void runResultFree(long result);

    void runFree(long run);

    /**
     * Replay a base64 reproduce blob as a standalone test case. Returns the raw rc ({@link
     * Abi#E_INVALID_ARG} for a corrupt or incompatible blob); on OK, {@code out[0]} receives the
     * caller-owned handle. {@code output} has the same contract as in {@link #runStart} but need not
     * outlive the call.
     */
    int testCaseFromBlob(long settings, String blob, Consumer<String> output, long[] out);

    void testCaseFree(long tc);

    // Per-test-case draws. Each returns the raw rc.
    int generateBoolean(long tc, double p, boolean[] out);

    int generateInteger(long tc, long min, long max, long[] out);

    int generateFloat(
            long tc,
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude,
            double[] out);

    int generateBytes(long tc, long minSize, long maxSize, byte[][] out);

    int generateString(long tc, long generator, String[] out);

    int generateDate(long tc, LocalDate min, LocalDate max, LocalDate[] out);

    /** Time bounds and results are at microsecond resolution (the engine's granularity). */
    int generateTime(long tc, LocalTime min, LocalTime max, LocalTime[] out);

    int generateDatetime(long tc, LocalDateTime min, LocalDateTime max, LocalDateTime[] out);

    /** On OK writes the UUID's 16 big-endian bytes into {@code out16}. */
    int generateUuid(long tc, int version, boolean hasVersion, byte[] out16);

    /** On OK writes the address's 4 network-order bytes into {@code out4}. */
    int generateIpv4(long tc, byte[] out4);

    /** On OK writes the address's 16 network-order bytes into {@code out16}. */
    int generateIpv6(long tc, byte[] out16);

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
            long[] out);

    int stringGeneratorRegex(String pattern, boolean fullmatch, long alphabet, long[] out);

    int stringGeneratorEmail(long[] out);

    int stringGeneratorUrl(long[] out);

    int stringGeneratorDomain(long maxLength, long[] out);

    void stringGeneratorFree(long generator);

    // Structure: spans, collections, pools, state machines. Each returns the raw rc.
    int startSpan(long tc, long label);

    int stopSpan(long tc, boolean discard);

    int newCollection(long tc, long minSize, long maxSize, long[] outId);

    int collectionMore(long tc, long id, boolean[] outMore);

    int collectionReject(long tc, long id, String why);

    int newPool(long tc, long[] outId);

    int poolAdd(long tc, long poolId, long[] outVariableId);

    int poolGenerate(long tc, long poolId, boolean consume, long[] outVariableId);

    int newStateMachine(long tc, List<String> ruleNames, List<String> invariantNames, long[] outId);

    /** {@code outRuleIndex[0]} receives the rule index, or {@link Abi#STATE_MACHINE_DONE}. */
    int stateMachineNextRule(long tc, long stateMachineId, long[] outRuleIndex);

    int target(long tc, double value, String label);

    int markComplete(long tc, int status, String origin);

    // Results.
    int runResultStatus(long result);

    /** The run-level error message, or {@code null} when the run completed normally. */
    String runResultError(long result);

    long runResultFailureCount(long result);

    /**
     * The reproduce blob of the {@code index}-th distinct failure, or {@code null} if libhegel
     * produced none for it.
     */
    String failureBlob(long result, long index);

    // Diagnostics.
    String lastErrorMessage();

    String version();
}
