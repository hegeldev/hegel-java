package dev.hegel;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The real libhegel binding, driving the C ABI over the Foreign Function and Memory API.
 *
 * <p>Resolves every C symbol once and caches the resulting {@link MethodHandle}s, which are
 * immutable after construction and safe to share across threads. Every call routes through {@link
 * #invoke} so error translation and the one-place FFI try/catch live in a single method.
 *
 * <p>Every fallible call takes a {@code hegel_context_t*} as its first argument, on which libhegel
 * records the diagnostic of a failed call. A context must not be shared between threads, so each
 * thread lazily creates its own; {@link #lastErrorMessage()} reads the current thread's context,
 * which is what the failing call just wrote. Contexts are never freed — one small allocation per
 * thread that touches the engine, for the life of the process.
 */
final class RealLibhegel implements Libhegel {
    private final Arena libArena;
    private final Linker linker;

    // hegel_run_start registers the output-callback upcall stub with the engine until
    // hegel_run_free, so each run gets a confined arena owning its stub (and out-slot), closed in
    // runFree. Runs are started and freed on the same thread (the Runner's), matching confinement.
    private final Map<Long, Arena> runArenas = new ConcurrentHashMap<>();

    private final ThreadLocal<MemorySegment> context;

    // struct hegel_date_t { int32_t year; uint8_t month; uint8_t day; }
    private static final StructLayout DATE_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("year"),
            JAVA_BYTE.withName("month"),
            JAVA_BYTE.withName("day"),
            MemoryLayout.paddingLayout(2));

    // struct hegel_time_t { uint8_t hour; uint8_t minute; uint8_t second; uint32_t microsecond; }
    private static final StructLayout TIME_LAYOUT = MemoryLayout.structLayout(
            JAVA_BYTE.withName("hour"),
            JAVA_BYTE.withName("minute"),
            JAVA_BYTE.withName("second"),
            MemoryLayout.paddingLayout(1),
            JAVA_INT.withName("microsecond"));

    // struct hegel_datetime_t { hegel_date_t date; hegel_time_t time; }
    private static final StructLayout DATETIME_LAYOUT =
            MemoryLayout.structLayout(DATE_LAYOUT.withName("date"), TIME_LAYOUT.withName("time"));

    // struct hegel_generate_bytes_result_t / hegel_generate_string_result_t { T* data; size_t len; }
    private static final StructLayout BUFFER_RESULT_LAYOUT =
            MemoryLayout.structLayout(ADDRESS.withName("data"), JAVA_LONG.withName("len"));

    private static final FunctionDescriptor OUTPUT_CALLBACK_DESC =
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG);

    private static final MethodHandle EMIT_LINE = findEmitLine();

    @Generated // MethodHandles cannot fail to find a private static method of this class.
    private static MethodHandle findEmitLine() {
        try {
            return MethodHandles.lookup()
                    .findStatic(
                            RealLibhegel.class,
                            "emitLine",
                            MethodType.methodType(
                                    void.class, Consumer.class, MemorySegment.class, MemorySegment.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new HegelException("failed to resolve the output-callback bridge", e);
        }
    }

    private final MethodHandle contextNew;
    private final MethodHandle contextLastError;
    private final MethodHandle settingsNew;
    private final MethodHandle settingsFree;
    private final MethodHandle settingsSetMode;
    private final MethodHandle settingsSetBackend;
    private final MethodHandle settingsSetTestCases;
    private final MethodHandle settingsSetVerbosity;
    private final MethodHandle settingsSetSeed;
    private final MethodHandle settingsSetDerandomize;
    private final MethodHandle settingsSetReportMultipleFailures;
    private final MethodHandle settingsSetDatabase;
    private final MethodHandle settingsSetDatabaseKey;
    private final MethodHandle settingsSetPhases;
    private final MethodHandle settingsSetSuppressHealthCheck;
    private final MethodHandle runStart;
    private final MethodHandle nextTestCase;
    private final MethodHandle runResult;
    private final MethodHandle runResultFree;
    private final MethodHandle runFree;
    private final MethodHandle testCaseFromBlob;
    private final MethodHandle testCaseFree;
    private final MethodHandle generateBoolean;
    private final MethodHandle generateInteger;
    private final MethodHandle generateFloat;
    private final MethodHandle generateBytes;
    private final MethodHandle generateBytesResultFree;
    private final MethodHandle generateString;
    private final MethodHandle generateStringResultFree;
    private final MethodHandle generateDate;
    private final MethodHandle generateTime;
    private final MethodHandle generateDatetime;
    private final MethodHandle generateUuid;
    private final MethodHandle generateIpv4;
    private final MethodHandle generateIpv6;
    private final MethodHandle stringGeneratorText;
    private final MethodHandle stringGeneratorRegex;
    private final MethodHandle stringGeneratorEmail;
    private final MethodHandle stringGeneratorUrl;
    private final MethodHandle stringGeneratorDomain;
    private final MethodHandle stringGeneratorFree;
    private final MethodHandle startSpan;
    private final MethodHandle stopSpan;
    private final MethodHandle newCollection;
    private final MethodHandle collectionMore;
    private final MethodHandle collectionReject;
    private final MethodHandle newPool;
    private final MethodHandle poolAdd;
    private final MethodHandle poolGenerate;
    private final MethodHandle newStateMachine;
    private final MethodHandle stateMachineNextRule;
    private final MethodHandle target;
    private final MethodHandle markComplete;
    private final MethodHandle runResultStatus;
    private final MethodHandle runResultError;
    private final MethodHandle runResultFailureCount;
    private final MethodHandle runResultFailure;
    private final MethodHandle failureFree;
    private final MethodHandle failureReproductionBlob;
    private final MethodHandle version;

    RealLibhegel(Path libraryPath) {
        this.libArena = Arena.ofShared();
        this.linker = Linker.nativeLinker();
        SymbolLookup lookup;
        try {
            lookup = SymbolLookup.libraryLookup(libraryPath, libArena);
        } catch (IllegalArgumentException e) {
            libArena.close();
            throw new HegelException("Failed to open libhegel at " + libraryPath + ": " + e.getMessage());
        }

        // rc(ctx, args...) descriptors; the leading JAVA_INT is the hegel_result_t return.
        this.contextNew = h(linker, lookup, "hegel_context_new", FunctionDescriptor.of(ADDRESS));
        this.contextLastError = h(linker, lookup, "hegel_context_last_error", FunctionDescriptor.of(ADDRESS, ADDRESS));
        this.settingsNew = h(linker, lookup, "hegel_settings_new", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.settingsFree = h(linker, lookup, "hegel_settings_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.settingsSetMode = h(
                linker, lookup, "hegel_settings_set_mode", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.settingsSetBackend = h(
                linker,
                lookup,
                "hegel_settings_set_backend",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.settingsSetTestCases = h(
                linker,
                lookup,
                "hegel_settings_set_test_cases",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
        this.settingsSetVerbosity = h(
                linker,
                lookup,
                "hegel_settings_set_verbosity",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.settingsSetSeed = h(
                linker,
                lookup,
                "hegel_settings_set_seed",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_BOOLEAN));
        this.settingsSetDerandomize = h(
                linker,
                lookup,
                "hegel_settings_set_derandomize",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_BOOLEAN));
        this.settingsSetReportMultipleFailures = h(
                linker,
                lookup,
                "hegel_settings_set_report_multiple_failures",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_BOOLEAN));
        this.settingsSetDatabase = h(
                linker,
                lookup,
                "hegel_settings_set_database",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.settingsSetDatabaseKey = h(
                linker,
                lookup,
                "hegel_settings_set_database_key",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.settingsSetPhases = h(
                linker,
                lookup,
                "hegel_settings_set_phases",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.settingsSetSuppressHealthCheck = h(
                linker,
                lookup,
                "hegel_settings_set_suppress_health_check",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.runStart = h(
                linker,
                lookup,
                "hegel_run_start",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        this.nextTestCase =
                h(linker, lookup, "hegel_next_test_case", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.runResult =
                h(linker, lookup, "hegel_run_result", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.runResultFree =
                h(linker, lookup, "hegel_run_result_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.runFree = h(linker, lookup, "hegel_run_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.testCaseFromBlob = h(
                linker,
                lookup,
                "hegel_test_case_from_blob",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        this.testCaseFree =
                h(linker, lookup, "hegel_test_case_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.generateBoolean = h(
                linker,
                lookup,
                "hegel_generate_boolean",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_DOUBLE, JAVA_BOOLEAN, JAVA_BOOLEAN, ADDRESS));
        this.generateInteger = h(
                linker,
                lookup,
                "hegel_generate_integer",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));
        this.generateFloat = h(
                linker,
                lookup,
                "hegel_generate_float",
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        ADDRESS,
                        JAVA_INT,
                        JAVA_DOUBLE,
                        JAVA_DOUBLE,
                        JAVA_BOOLEAN,
                        JAVA_BOOLEAN,
                        JAVA_BOOLEAN,
                        JAVA_BOOLEAN,
                        JAVA_DOUBLE,
                        ADDRESS));
        this.generateBytes = h(
                linker,
                lookup,
                "hegel_generate_bytes",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));
        this.generateBytesResultFree = h(
                linker, lookup, "hegel_generate_bytes_result_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.generateString = h(
                linker,
                lookup,
                "hegel_generate_string",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        this.generateStringResultFree = h(
                linker, lookup, "hegel_generate_string_result_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.generateDate = h(
                linker,
                lookup,
                "hegel_generate_date",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, DATE_LAYOUT, DATE_LAYOUT, ADDRESS));
        this.generateTime = h(
                linker,
                lookup,
                "hegel_generate_time",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, TIME_LAYOUT, TIME_LAYOUT, ADDRESS));
        this.generateDatetime = h(
                linker,
                lookup,
                "hegel_generate_datetime",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, DATETIME_LAYOUT, DATETIME_LAYOUT, ADDRESS));
        this.generateUuid = h(
                linker,
                lookup,
                "hegel_generate_uuid",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_BYTE, JAVA_BOOLEAN, ADDRESS));
        this.generateIpv4 =
                h(linker, lookup, "hegel_generate_ipv4", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.generateIpv6 =
                h(linker, lookup, "hegel_generate_ipv6", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.stringGeneratorText = h(
                linker,
                lookup,
                "hegel_string_generator_text",
                FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG,
                        ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));
        this.stringGeneratorRegex = h(
                linker,
                lookup,
                "hegel_string_generator_regex",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_BOOLEAN, ADDRESS, ADDRESS));
        this.stringGeneratorEmail =
                h(linker, lookup, "hegel_string_generator_email", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.stringGeneratorUrl =
                h(linker, lookup, "hegel_string_generator_url", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.stringGeneratorDomain = h(
                linker,
                lookup,
                "hegel_string_generator_domain",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
        this.stringGeneratorFree =
                h(linker, lookup, "hegel_string_generator_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.startSpan =
                h(linker, lookup, "hegel_start_span", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
        this.stopSpan =
                h(linker, lookup, "hegel_stop_span", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_BOOLEAN));
        this.newCollection = h(
                linker,
                lookup,
                "hegel_new_collection",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));
        this.collectionMore = h(
                linker,
                lookup,
                "hegel_collection_more",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        this.collectionReject = h(
                linker,
                lookup,
                "hegel_collection_reject",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        this.newPool = h(linker, lookup, "hegel_new_pool", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.poolAdd = h(
                linker,
                lookup,
                "hegel_pool_add",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        this.poolGenerate = h(
                linker,
                lookup,
                "hegel_pool_generate",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_BOOLEAN, ADDRESS));
        this.newStateMachine = h(
                linker,
                lookup,
                "hegel_new_state_machine",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));
        this.stateMachineNextRule = h(
                linker,
                lookup,
                "hegel_state_machine_next_rule",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        this.target = h(
                linker,
                lookup,
                "hegel_target",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_DOUBLE, ADDRESS));
        this.markComplete = h(
                linker,
                lookup,
                "hegel_mark_complete",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        this.runResultStatus = h(
                linker, lookup, "hegel_run_result_status", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.runResultError =
                h(linker, lookup, "hegel_run_result_error", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.runResultFailureCount = h(
                linker,
                lookup,
                "hegel_run_result_failure_count",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.runResultFailure = h(
                linker,
                lookup,
                "hegel_run_result_failure",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        this.failureFree = h(linker, lookup, "hegel_failure_free", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.failureReproductionBlob = h(
                linker,
                lookup,
                "hegel_failure_reproduction_blob",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.version = h(linker, lookup, "hegel_version", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.context = ThreadLocal.withInitial(() -> (MemorySegment) invoke(contextNew));
    }

    private static MethodHandle h(Linker linker, SymbolLookup lookup, String symbol, FunctionDescriptor desc) {
        return linker.downcallHandle(findSymbol(lookup, symbol), desc);
    }

    static MemorySegment findSymbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new HegelException("libhegel is missing symbol '"
                        + symbol
                        + "' (ABI/version mismatch). Rebuild or update the engine."));
    }

    /** Single point of FFI invocation and error wrapping. */
    static Object invoke(MethodHandle handle, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new HegelException("libhegel FFI call failed: " + t, t);
        }
    }

    private MemorySegment ctx() {
        return context.get();
    }

    private int rc(MethodHandle handle, Object... args) {
        Object[] withCtx = new Object[args.length + 1];
        withCtx[0] = ctx();
        System.arraycopy(args, 0, withCtx, 1, args.length);
        return (Integer) invoke(handle, withCtx);
    }

    /** Require OK from a call that cannot fail with this binding's inputs. */
    private void check(String op, int code) {
        if (code != Abi.OK) {
            throw new HegelException(
                    op + " failed (rc=" + code + "): " + java.util.Objects.toString(lastErrorMessage(), ""));
        }
    }

    private static MemorySegment cstr(Arena a, String s) {
        return s == null ? MemorySegment.NULL : a.allocateFrom(s);
    }

    static String readCString(MemorySegment ptr) {
        if (ptr == null || ptr.address() == 0) {
            return null;
        }
        return ptr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    }

    /**
     * Bridge one line of engine output to the run's {@link Consumer}. An exception escaping an
     * upcall would tear down the VM, so every throwable is swallowed here.
     */
    static void emitLine(Consumer<String> output, MemorySegment userData, MemorySegment line, long len) {
        try {
            byte[] bytes = line.reinterpret(len).toArray(JAVA_BYTE);
            output.accept(new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            // Deliberately dropped: output delivery must never unwind into the engine.
        }
    }

    private MemorySegment upcallStub(Arena arena, Consumer<String> output) {
        return linker.upcallStub(EMIT_LINE.bindTo(output), OUTPUT_CALLBACK_DESC, arena);
    }

    // --- settings ---

    @Override
    public MemorySegment settingsNew() {
        MemorySegment out = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_settings_new", rc(settingsNew, out));
        return out.get(ADDRESS, 0);
    }

    @Override
    public void settingsFree(MemorySegment s) {
        check("hegel_settings_free", rc(settingsFree, s));
    }

    @Override
    public void settingsMode(MemorySegment s, int mode) {
        check("hegel_settings_set_mode", rc(settingsSetMode, s, mode));
    }

    @Override
    public void settingsBackend(MemorySegment s, int backend) {
        check("hegel_settings_set_backend", rc(settingsSetBackend, s, backend));
    }

    @Override
    public void settingsTestCases(MemorySegment s, long n) {
        check("hegel_settings_set_test_cases", rc(settingsSetTestCases, s, n));
    }

    @Override
    public void settingsVerbosity(MemorySegment s, int v) {
        check("hegel_settings_set_verbosity", rc(settingsSetVerbosity, s, v));
    }

    @Override
    public void settingsSeed(MemorySegment s, long seed, boolean hasSeed) {
        check("hegel_settings_set_seed", rc(settingsSetSeed, s, seed, hasSeed));
    }

    @Override
    public void settingsDerandomize(MemorySegment s, boolean derandomize) {
        check("hegel_settings_set_derandomize", rc(settingsSetDerandomize, s, derandomize));
    }

    @Override
    public void settingsReportMultipleFailures(MemorySegment s, boolean yes) {
        check("hegel_settings_set_report_multiple_failures", rc(settingsSetReportMultipleFailures, s, yes));
    }

    @Override
    public void settingsDatabase(MemorySegment s, String path) {
        // libhegel copies the string during the call, so a per-call auto arena suffices.
        check("hegel_settings_set_database", rc(settingsSetDatabase, s, cstr(Arena.ofAuto(), path)));
    }

    @Override
    public void settingsDatabaseKey(MemorySegment s, String key) {
        check("hegel_settings_set_database_key", rc(settingsSetDatabaseKey, s, cstr(Arena.ofAuto(), key)));
    }

    @Override
    public void settingsPhases(MemorySegment s, int mask) {
        check("hegel_settings_set_phases", rc(settingsSetPhases, s, mask));
    }

    @Override
    public void settingsSuppressHealthCheck(MemorySegment s, int mask) {
        check("hegel_settings_set_suppress_health_check", rc(settingsSetSuppressHealthCheck, s, mask));
    }

    // --- run lifecycle ---

    @Override
    public MemorySegment runStart(MemorySegment settings, Consumer<String> output) {
        Arena arena = Arena.ofConfined();
        MemorySegment callback = output == null ? MemorySegment.NULL : upcallStub(arena, output);
        MemorySegment out = arena.allocate(ADDRESS);
        int code = rc(runStart, settings, callback, MemorySegment.NULL, out);
        if (code != Abi.OK) {
            arena.close();
            throw new HegelException(
                    "hegel_run_start failed (rc=" + code + "): " + java.util.Objects.toString(lastErrorMessage(), ""));
        }
        MemorySegment run = out.get(ADDRESS, 0);
        runArenas.put(run.address(), arena);
        return run;
    }

    @Override
    public MemorySegment nextTestCase(MemorySegment run) {
        MemorySegment out = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_next_test_case", rc(nextTestCase, run, out));
        return out.get(ADDRESS, 0);
    }

    @Override
    public MemorySegment runResult(MemorySegment run) {
        MemorySegment out = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_run_result", rc(runResult, run, out));
        return out.get(ADDRESS, 0);
    }

    @Override
    public void runResultFree(MemorySegment result) {
        check("hegel_run_result_free", rc(runResultFree, result));
    }

    @Override
    public void runFree(MemorySegment run) {
        check("hegel_run_free", rc(runFree, run));
        Arena arena = runArenas.remove(run.address());
        arena.close();
    }

    @Override
    public int testCaseFromBlob(MemorySegment settings, String blob, Consumer<String> output, MemorySegment[] out) {
        // The blob replay's output is emitted synchronously during this call, so the stub only
        // needs to live for its duration.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment callback = output == null ? MemorySegment.NULL : upcallStub(arena, output);
            MemorySegment outSeg = arena.allocate(ADDRESS);
            int code = rc(testCaseFromBlob, settings, cstr(arena, blob), callback, MemorySegment.NULL, outSeg);
            if (code == Abi.OK) {
                out[0] = outSeg.get(ADDRESS, 0);
            }
            return code;
        }
    }

    @Override
    public void testCaseFree(MemorySegment tc) {
        check("hegel_test_case_free", rc(testCaseFree, tc));
    }

    // --- draws ---

    @Override
    public int generateBoolean(MemorySegment tc, double p, boolean[] out) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_BOOLEAN);
        int code = rc(generateBoolean, tc, p, false, false, seg);
        if (code == Abi.OK) {
            out[0] = seg.get(JAVA_BOOLEAN, 0);
        }
        return code;
    }

    @Override
    public int generateInteger(MemorySegment tc, long min, long max, long[] out) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(generateInteger, tc, min, max, seg);
        if (code == Abi.OK) {
            out[0] = seg.get(JAVA_LONG, 0);
        }
        return code;
    }

    @Override
    public int generateFloat(
            MemorySegment tc,
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude,
            double[] out) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_DOUBLE);
        int code = rc(
                generateFloat,
                tc,
                width,
                min,
                max,
                allowNan,
                allowInfinity,
                excludeMin,
                excludeMax,
                smallestNonzeroMagnitude,
                seg);
        if (code == Abi.OK) {
            out[0] = seg.get(JAVA_DOUBLE, 0);
        }
        return code;
    }

    @Override
    public int generateBytes(MemorySegment tc, long minSize, long maxSize, byte[][] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(BUFFER_RESULT_LAYOUT);
            int code = rc(generateBytes, tc, minSize, maxSize, result);
            if (code == Abi.OK) {
                out[0] = copyBuffer(result);
                check("hegel_generate_bytes_result_free", rc(generateBytesResultFree, result));
            }
            return code;
        }
    }

    @Override
    public int generateString(MemorySegment tc, MemorySegment generator, String[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(BUFFER_RESULT_LAYOUT);
            int code = rc(generateString, tc, generator, result);
            if (code == Abi.OK) {
                out[0] = new String(copyBuffer(result), StandardCharsets.UTF_8);
                check("hegel_generate_string_result_free", rc(generateStringResultFree, result));
            }
            return code;
        }
    }

    /** Copy an engine-allocated {@code {data, len}} buffer out before it is freed. */
    private static byte[] copyBuffer(MemorySegment result) {
        MemorySegment data = result.get(ADDRESS, 0);
        long len = result.get(JAVA_LONG, ADDRESS.byteSize());
        return data.reinterpret(len).toArray(JAVA_BYTE);
    }

    @Override
    public int generateDate(MemorySegment tc, LocalDate min, LocalDate max, LocalDate[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outSeg = arena.allocate(DATE_LAYOUT);
            int code = rc(generateDate, tc, dateSegment(arena, min), dateSegment(arena, max), outSeg);
            if (code == Abi.OK) {
                out[0] = readDate(outSeg, 0);
            }
            return code;
        }
    }

    @Override
    public int generateTime(MemorySegment tc, LocalTime min, LocalTime max, LocalTime[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outSeg = arena.allocate(TIME_LAYOUT);
            int code = rc(generateTime, tc, timeSegment(arena, min), timeSegment(arena, max), outSeg);
            if (code == Abi.OK) {
                out[0] = readTime(outSeg, 0);
            }
            return code;
        }
    }

    @Override
    public int generateDatetime(MemorySegment tc, LocalDateTime min, LocalDateTime max, LocalDateTime[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outSeg = arena.allocate(DATETIME_LAYOUT);
            int code = rc(generateDatetime, tc, datetimeSegment(arena, min), datetimeSegment(arena, max), outSeg);
            if (code == Abi.OK) {
                out[0] = LocalDateTime.of(readDate(outSeg, 0), readTime(outSeg, DATE_LAYOUT.byteSize()));
            }
            return code;
        }
    }

    private static MemorySegment dateSegment(Arena arena, LocalDate date) {
        MemorySegment seg = arena.allocate(DATE_LAYOUT);
        writeDate(seg, 0, date);
        return seg;
    }

    private static MemorySegment timeSegment(Arena arena, LocalTime time) {
        MemorySegment seg = arena.allocate(TIME_LAYOUT);
        writeTime(seg, 0, time);
        return seg;
    }

    private static MemorySegment datetimeSegment(Arena arena, LocalDateTime dt) {
        MemorySegment seg = arena.allocate(DATETIME_LAYOUT);
        writeDate(seg, 0, dt.toLocalDate());
        writeTime(seg, DATE_LAYOUT.byteSize(), dt.toLocalTime());
        return seg;
    }

    private static void writeDate(MemorySegment seg, long offset, LocalDate date) {
        seg.set(JAVA_INT, offset, date.getYear());
        seg.set(JAVA_BYTE, offset + 4, (byte) date.getMonthValue());
        seg.set(JAVA_BYTE, offset + 5, (byte) date.getDayOfMonth());
    }

    private static LocalDate readDate(MemorySegment seg, long offset) {
        return LocalDate.of(seg.get(JAVA_INT, offset), seg.get(JAVA_BYTE, offset + 4), seg.get(JAVA_BYTE, offset + 5));
    }

    private static void writeTime(MemorySegment seg, long offset, LocalTime time) {
        seg.set(JAVA_BYTE, offset, (byte) time.getHour());
        seg.set(JAVA_BYTE, offset + 1, (byte) time.getMinute());
        seg.set(JAVA_BYTE, offset + 2, (byte) time.getSecond());
        seg.set(JAVA_INT, offset + 4, time.getNano() / 1_000);
    }

    private static LocalTime readTime(MemorySegment seg, long offset) {
        return LocalTime.of(
                seg.get(JAVA_BYTE, offset),
                seg.get(JAVA_BYTE, offset + 1),
                seg.get(JAVA_BYTE, offset + 2),
                seg.get(JAVA_INT, offset + 4) * 1_000);
    }

    @Override
    public int generateUuid(MemorySegment tc, int version, boolean hasVersion, byte[] out16) {
        return fixedBytesDraw(seg -> rc(generateUuid, tc, (byte) version, hasVersion, seg), out16);
    }

    @Override
    public int generateIpv4(MemorySegment tc, byte[] out4) {
        return fixedBytesDraw(seg -> rc(generateIpv4, tc, seg), out4);
    }

    @Override
    public int generateIpv6(MemorySegment tc, byte[] out16) {
        return fixedBytesDraw(seg -> rc(generateIpv6, tc, seg), out16);
    }

    @FunctionalInterface
    private interface BytesDraw {
        int run(MemorySegment out);
    }

    /** Run a draw writing into a fixed-size byte buffer, copying it out on success. */
    private static int fixedBytesDraw(BytesDraw draw, byte[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(out.length);
            int code = draw.run(seg);
            if (code == Abi.OK) {
                MemorySegment.copy(seg, JAVA_BYTE, 0, out, 0, out.length);
            }
            return code;
        }
    }

    // --- string-generator handles ---

    @Override
    public int stringGeneratorText(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters,
            MemorySegment[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment categoriesSeg = cstrArray(arena, categories);
            MemorySegment excludeSeg = cstrArray(arena, excludeCategories);
            byte[] include = utf8OrNull(includeCharacters);
            byte[] exclude = utf8OrNull(excludeCharacters);
            MemorySegment outSeg = arena.allocate(ADDRESS);
            int code = rc(
                    stringGeneratorText,
                    minSize,
                    maxSize,
                    cstr(arena, codec),
                    (int) minCodepoint,
                    (int) maxCodepoint,
                    categoriesSeg,
                    categories == null ? 0L : (long) categories.size(),
                    excludeSeg,
                    excludeCategories == null ? 0L : (long) excludeCategories.size(),
                    bytesOrNull(arena, include),
                    include == null ? 0L : (long) include.length,
                    bytesOrNull(arena, exclude),
                    exclude == null ? 0L : (long) exclude.length,
                    outSeg);
            // Read the (zero-initialised) out slot unconditionally: callers check the return code
            // before using it.
            out[0] = outSeg.get(ADDRESS, 0);
            return code;
        }
    }

    private static byte[] utf8OrNull(String s) {
        return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
    }

    private static MemorySegment bytesOrNull(Arena arena, byte[] bytes) {
        if (bytes == null) {
            return MemorySegment.NULL;
        }
        MemorySegment seg = arena.allocate(Math.max(bytes.length, 1));
        MemorySegment.copy(bytes, 0, seg, JAVA_BYTE, 0, bytes.length);
        return seg;
    }

    /** A NULL-distinct {@code char**}: {@code null} maps to NULL, an empty list to a valid pointer. */
    private static MemorySegment cstrArray(Arena arena, List<String> strings) {
        if (strings == null) {
            return MemorySegment.NULL;
        }
        MemorySegment array = arena.allocate(ADDRESS, Math.max(strings.size(), 1));
        for (int i = 0; i < strings.size(); i++) {
            array.setAtIndex(ADDRESS, i, cstr(arena, strings.get(i)));
        }
        return array;
    }

    @Override
    public int stringGeneratorRegex(String pattern, boolean fullmatch, MemorySegment alphabet, MemorySegment[] out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outSeg = arena.allocate(ADDRESS);
            int code = rc(
                    stringGeneratorRegex,
                    cstr(arena, pattern),
                    fullmatch,
                    alphabet == null ? MemorySegment.NULL : alphabet,
                    outSeg);
            out[0] = outSeg.get(ADDRESS, 0);
            return code;
        }
    }

    @Override
    public int stringGeneratorEmail(MemorySegment[] out) {
        return handleConstructor(stringGeneratorEmail, out);
    }

    @Override
    public int stringGeneratorUrl(MemorySegment[] out) {
        return handleConstructor(stringGeneratorUrl, out);
    }

    /** Run a no-argument string-generator constructor. */
    private int handleConstructor(MethodHandle constructor, MemorySegment[] out) {
        MemorySegment outSeg = Arena.ofAuto().allocate(ADDRESS);
        int code = rc(constructor, outSeg);
        out[0] = outSeg.get(ADDRESS, 0);
        return code;
    }

    @Override
    public int stringGeneratorDomain(long maxLength, MemorySegment[] out) {
        MemorySegment outSeg = Arena.ofAuto().allocate(ADDRESS);
        int code = rc(stringGeneratorDomain, maxLength, outSeg);
        out[0] = outSeg.get(ADDRESS, 0);
        return code;
    }

    @Override
    public void stringGeneratorFree(MemorySegment generator) {
        check("hegel_string_generator_free", rc(stringGeneratorFree, generator));
    }

    // --- structure ---

    @Override
    public int startSpan(MemorySegment tc, long label) {
        return rc(startSpan, tc, label);
    }

    @Override
    public int stopSpan(MemorySegment tc, boolean discard) {
        return rc(stopSpan, tc, discard);
    }

    @Override
    public int newCollection(MemorySegment tc, long minSize, long maxSize, long[] outId) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(newCollection, tc, minSize, maxSize, seg);
        outId[0] = seg.get(JAVA_LONG, 0);
        return code;
    }

    @Override
    public int collectionMore(MemorySegment tc, long id, boolean[] outMore) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_BOOLEAN);
        int code = rc(collectionMore, tc, id, seg);
        if (code == Abi.OK) {
            outMore[0] = seg.get(JAVA_BOOLEAN, 0);
        }
        return code;
    }

    @Override
    public int collectionReject(MemorySegment tc, long id, String why) {
        return rc(collectionReject, tc, id, cstr(Arena.ofAuto(), why));
    }

    @Override
    public int newPool(MemorySegment tc, long[] outId) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(newPool, tc, seg);
        outId[0] = seg.get(JAVA_LONG, 0);
        return code;
    }

    @Override
    public int poolAdd(MemorySegment tc, long poolId, long[] outVariableId) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(poolAdd, tc, poolId, seg);
        outVariableId[0] = seg.get(JAVA_LONG, 0);
        return code;
    }

    @Override
    public int poolGenerate(MemorySegment tc, long poolId, boolean consume, long[] outVariableId) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(poolGenerate, tc, poolId, consume, seg);
        outVariableId[0] = seg.get(JAVA_LONG, 0);
        return code;
    }

    @Override
    public int newStateMachine(MemorySegment tc, List<String> ruleNames, List<String> invariantNames, long[] outId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rules = cstrArray(arena, ruleNames);
            MemorySegment invariants = cstrArray(arena, invariantNames);
            MemorySegment seg = arena.allocate(JAVA_LONG);
            int code = rc(
                    newStateMachine, tc, rules, (long) ruleNames.size(), invariants, (long) invariantNames.size(), seg);
            outId[0] = seg.get(JAVA_LONG, 0);
            return code;
        }
    }

    @Override
    public int stateMachineNextRule(MemorySegment tc, long stateMachineId, long[] outRuleIndex) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        int code = rc(stateMachineNextRule, tc, stateMachineId, seg);
        if (code == Abi.OK) {
            outRuleIndex[0] = seg.get(JAVA_LONG, 0);
        }
        return code;
    }

    @Override
    public int target(MemorySegment tc, double value, String label) {
        return rc(target, tc, value, cstr(Arena.ofAuto(), label));
    }

    @Override
    public int markComplete(MemorySegment tc, int status, String origin) {
        return rc(markComplete, tc, status, cstr(Arena.ofAuto(), origin));
    }

    // --- results ---

    @Override
    public int runResultStatus(MemorySegment result) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_INT);
        check("hegel_run_result_status", rc(runResultStatus, result, seg));
        return seg.get(JAVA_INT, 0);
    }

    @Override
    public String runResultError(MemorySegment result) {
        MemorySegment seg = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_run_result_error", rc(runResultError, result, seg));
        return readCString(seg.get(ADDRESS, 0));
    }

    @Override
    public long runResultFailureCount(MemorySegment result) {
        MemorySegment seg = Arena.ofAuto().allocate(JAVA_LONG);
        check("hegel_run_result_failure_count", rc(runResultFailureCount, result, seg));
        return seg.get(JAVA_LONG, 0);
    }

    @Override
    public String failureBlob(MemorySegment result, long index) {
        MemorySegment failureOut = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_run_result_failure", rc(runResultFailure, result, index, failureOut));
        MemorySegment failure = failureOut.get(ADDRESS, 0);
        MemorySegment blobOut = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_failure_reproduction_blob", rc(failureReproductionBlob, failure, blobOut));
        String blob = readCString(blobOut.get(ADDRESS, 0));
        check("hegel_failure_free", rc(failureFree, failure));
        return blob;
    }

    // --- diagnostics ---

    @Override
    public String lastErrorMessage() {
        return readCString((MemorySegment) invoke(contextLastError, ctx()));
    }

    @Override
    public String version() {
        MemorySegment seg = Arena.ofAuto().allocate(ADDRESS);
        check("hegel_version", rc(version, seg));
        return readCString(seg.get(ADDRESS, 0));
    }
}
