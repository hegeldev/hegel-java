package dev.hegel.lowlevel;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * The libhegel binding surface, as a table of operations: one method per {@code hegel_*} function
 * in {@code hegel.h}.
 *
 * <p>This is the contract between a <em>binding</em> (an implementation over some FFI mechanism)
 * and a <em>frontend</em> (code that drives the engine). Hegel ships two bindings — the Foreign
 * Function and Memory API binding in {@code dev.hegel:hegel} and the JNA binding in {@code
 * dev.hegel:hegel-jna} — each registered as a {@link LibhegelBackend}; {@link #load()} picks up
 * whichever is on the classpath. Frontends may also substitute a fake for tests.
 *
 * <p>Opaque handles ({@code hegel_settings_t*}, {@code hegel_run_t*}, {@code hegel_test_case_t*},
 * {@code hegel_run_result_t*}, {@code hegel_string_generator_t*}) are passed as raw addresses
 * ({@code long}; {@code 0} is NULL); callers treat them as opaque and never dereference them.
 * Handles are caller-owned: every handle a method returns must be released with its matching
 * {@code *Free} method. A binding keeps one {@code hegel_context_t} per thread internally, so the
 * C API's leading context argument does not appear here; its last error message is read with
 * {@link #lastErrorMessage()}.
 *
 * <p>Two calling conventions coexist here, mirroring how a frontend consumes the ABI:
 *
 * <ul>
 *   <li>Per-test-case primitives (draws, spans, collections, pools, state machines, {@code target},
 *       {@code markComplete}) and the string-generator constructors return the raw libhegel return
 *       code ({@link Abi#OK}, {@link Abi#E_STOP_TEST}, {@link Abi#E_ASSUME}, ...); the frontend
 *       translates it and reads {@link #lastErrorMessage()} immediately on a non-OK code.
 *       Out-values are written into caller-supplied one-element arrays only on {@link Abi#OK}
 *       (except where noted).
 *   <li>Infrastructure calls (settings construction and setters, run lifecycle, result readers,
 *       frees) cannot legitimately fail with well-formed arguments, so implementations check the
 *       return code themselves and throw {@link LibhegelException} on an unexpected non-OK code.
 * </ul>
 *
 * <p>Strings the engine returns are copied out before the method returns, so they remain valid.
 * Implementations are thread-safe; a process needs one instance.
 */
public interface Libhegel {
    /**
     * Resolve {@code libhegel} (see {@link LibraryLoader}) and open it with the binding registered
     * on the classpath.
     *
     * @return a binding over the resolved library
     * @throws LibhegelException if no library can be resolved or no binding is registered
     */
    static Libhegel load() {
        return load(LibraryLoader.fromEnvironment().resolve());
    }

    /**
     * Open the shared library at {@code library} with the binding registered on the classpath
     * (through {@link ServiceLoader}). With several registered, the first one found wins.
     *
     * @param library the path of the {@code libhegel} shared object
     * @return a binding over that library
     * @throws LibhegelException if no binding is registered
     */
    static Libhegel load(Path library) {
        return load(library, ServiceLoader.load(LibhegelBackend.class));
    }

    /**
     * Open the shared library at {@code library} with the first of {@code backends}.
     *
     * @param library the path of the {@code libhegel} shared object
     * @param backends candidate bindings, in preference order
     * @return a binding over that library
     * @throws LibhegelException if {@code backends} is empty
     */
    static Libhegel load(Path library, Iterable<? extends LibhegelBackend> backends) {
        Iterator<? extends LibhegelBackend> it = backends.iterator();
        if (!it.hasNext()) {
            throw new LibhegelException("No libhegel binding is registered on the classpath. Add dev.hegel:hegel"
                    + " (FFM, Java 22+) or dev.hegel:hegel-jna (JNA, Java 17+), or register your own"
                    + " dev.hegel.lowlevel.LibhegelBackend service provider.");
        }
        return it.next().open(library);
    }

    // Settings. Setters cannot fail with this binding's inputs; implementations throw on non-OK.
    long settingsNew();

    void settingsFree(long s);

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

    /**
     * {@code hegel_new_state_machine}: {@code ruleGroups} is parallel to {@code ruleNames} (any
     * value but {@link Abi#STATE_MACHINE_DONE}), {@code invariantAlwaysCheck} parallel to {@code
     * invariantNames}. The engine draws the concurrency level in {@code [minConcurrency,
     * maxConcurrency]} and writes it to {@code outConcurrency}; pass {@code 1, 1} for a sequential
     * machine. {@code stepCount} is the target number of counted rounds per test case (at least 1;
     * the engine has no default, and 50 is the conventional choice).
     */
    int newStateMachine(
            long tc,
            List<String> ruleNames,
            long[] ruleGroups,
            List<String> invariantNames,
            boolean[] invariantAlwaysCheck,
            long minConcurrency,
            long maxConcurrency,
            long stepCount,
            long[] outId,
            long[] outConcurrency);

    /** The id of the group current for the new round, or {@link Abi#STATE_MACHINE_DONE}. */
    int stateMachineNextGroup(long tc, long stateMachineId, long[] outGroupId);

    /** {@code outRuleIndex[0]} receives the rule index, or {@link Abi#STATE_MACHINE_DONE}. */
    int stateMachineNextRule(long tc, long stateMachineId, long workerIndex, long[] outRuleIndex);

    int stateMachineRuleRejected(long tc, long stateMachineId, long workerIndex);

    int stateMachineShouldCheckInvariant(long tc, long stateMachineId, long invariantIndex, boolean[] outShouldCheck);

    /** {@code hegel_state_machine_free}: the handle is independent of its test case and run. */
    void stateMachineFree(long stateMachineId);

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

    /** The origin string the shrinker grouped the {@code index}-th distinct failure under. */
    String failureOrigin(long result, long index);

    // Diagnostics.
    String lastErrorMessage();

    String version();
}
