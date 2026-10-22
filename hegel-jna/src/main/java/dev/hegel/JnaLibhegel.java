package dev.hegel;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.Libhegel;
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
 * The real libhegel binding, driving the C ABI over JNA (Java Native Access), for JVMs without the
 * Foreign Function and Memory API (Java 17+; the {@code hegel} artifact binds via FFM on 22+).
 *
 * <p>Every fallible call takes a {@code hegel_context_t*} as its first argument, on which libhegel
 * records the diagnostic of a failed call. A context must not be shared between threads, so each
 * thread lazily creates its own; {@link #lastErrorMessage()} reads the current thread's context,
 * which is what the failing call just wrote. Contexts are never freed — one small allocation per
 * thread that touches the engine, for the life of the process.
 */
final class JnaLibhegel implements Libhegel {
    // hegel_run_start registers the output callback with the engine until hegel_run_free, so a
    // strong reference is held per run (JNA frees the native thunk when the Callback is collected).
    private final Map<Long, LineCallback> runCallbacks = new ConcurrentHashMap<>();

    private final HegelNative lib;
    private final ThreadLocal<Pointer> context;

    JnaLibhegel(Path libraryPath) {
        try {
            // Every string crosses as an explicit hegel_string_t (UTF-8 bytes plus length), so no
            // JNA String conversion is involved.
            this.lib = Native.load(libraryPath.toString(), HegelNative.class);
        } catch (UnsatisfiedLinkError e) {
            throw new HegelException("Failed to open libhegel at " + libraryPath + ": " + e.getMessage());
        }
        this.context = ThreadLocal.withInitial(lib::hegel_context_new);
    }

    interface HegelNative extends Library {
        Pointer hegel_context_new();

        HegelString.ByValue hegel_context_last_error(Pointer ctx);

        int hegel_settings_new(Pointer ctx, PointerByReference out);

        int hegel_settings_free(Pointer ctx, Pointer s);

        int hegel_settings_set_backend(Pointer ctx, Pointer s, int backend);

        int hegel_settings_set_test_cases(Pointer ctx, Pointer s, long n);

        int hegel_settings_set_verbosity(Pointer ctx, Pointer s, int v);

        int hegel_settings_set_seed(Pointer ctx, Pointer s, long seed, byte hasSeed);

        int hegel_settings_set_derandomize(Pointer ctx, Pointer s, byte derandomize);

        int hegel_settings_set_report_multiple_failures(Pointer ctx, Pointer s, byte yes);

        // A Structure parameter crosses as a pointer to it (NULL for null): `const hegel_string_t *`.
        int hegel_settings_set_database(Pointer ctx, Pointer s, HegelString path);

        int hegel_settings_set_database_key(Pointer ctx, Pointer s, HegelString key);

        int hegel_settings_set_phases(Pointer ctx, Pointer s, int mask);

        int hegel_settings_set_suppress_health_check(Pointer ctx, Pointer s, int mask);

        int hegel_settings_set_print_blob(Pointer ctx, Pointer s, byte yes);

        int hegel_settings_get_test_cases(Pointer ctx, Pointer s, LongByReference out);

        int hegel_settings_get_print_blob(Pointer ctx, Pointer s, ByteByReference out);

        int hegel_settings_set_nondeterminism_strictness(Pointer ctx, Pointer s, int strictness);

        int hegel_settings_get_nondeterminism_strictness(Pointer ctx, Pointer s, IntByReference out);

        int hegel_run_start(
                Pointer ctx, Pointer settings, LineCallback callback, Pointer userData, PointerByReference out);

        int hegel_run_start_blob(
                Pointer ctx,
                Pointer settings,
                HegelString.ByValue blob,
                LineCallback callback,
                Pointer userData,
                PointerByReference out);

        int hegel_next_test_case(Pointer ctx, Pointer run, PointerByReference out);

        int hegel_run_result(Pointer ctx, Pointer run, PointerByReference out);

        int hegel_run_result_free(Pointer ctx, Pointer result);

        int hegel_run_free(Pointer ctx, Pointer run);

        int hegel_test_case_from_blob(
                Pointer ctx,
                Pointer settings,
                HegelString.ByValue blob,
                LineCallback callback,
                Pointer userData,
                PointerByReference out);

        int hegel_test_case_free(Pointer ctx, Pointer tc);

        int hegel_test_case_should_capture(Pointer ctx, Pointer tc, ByteByReference out);

        int hegel_test_case_clone(Pointer ctx, Pointer tc, PointerByReference out);

        int hegel_test_case_set_worker(Pointer ctx, Pointer tc, long workerIndex);

        int hegel_generate_boolean(Pointer ctx, Pointer tc, double p, byte hasForced, byte forced, ByteByReference out);

        int hegel_generate_integer(Pointer ctx, Pointer tc, long min, long max, LongByReference out);

        int hegel_generate_float(
                Pointer ctx,
                Pointer tc,
                int width,
                double min,
                double max,
                byte allowNan,
                byte allowInfinity,
                byte excludeMin,
                byte excludeMax,
                double smallestNonzeroMagnitude,
                DoubleByReference out);

        int hegel_generate_bytes(Pointer ctx, Pointer tc, long minSize, long maxSize, HegelString out);

        int hegel_generate_bytes_result_free(Pointer ctx, HegelString result);

        int hegel_generate_string(Pointer ctx, Pointer tc, Pointer generator, HegelString out);

        int hegel_owned_string_free(Pointer ctx, HegelString result);

        int hegel_generate_date(Pointer ctx, Pointer tc, HegelDate.ByValue min, HegelDate.ByValue max, HegelDate out);

        int hegel_generate_time(Pointer ctx, Pointer tc, HegelTime.ByValue min, HegelTime.ByValue max, HegelTime out);

        int hegel_generate_datetime(
                Pointer ctx, Pointer tc, HegelDatetime.ByValue min, HegelDatetime.ByValue max, HegelDatetime out);

        int hegel_generate_uuid(Pointer ctx, Pointer tc, byte version, byte hasVersion, Pointer out);

        int hegel_generate_ipv4(Pointer ctx, Pointer tc, Pointer out);

        int hegel_generate_ipv6(Pointer ctx, Pointer tc, Pointer out);

        int hegel_string_generator_text(
                Pointer ctx,
                long minSize,
                long maxSize,
                HegelString codec,
                int minCodepoint,
                int maxCodepoint,
                Pointer categories,
                long categoriesLen,
                Pointer excludeCategories,
                long excludeCategoriesLen,
                HegelString.ByValue includeCharacters,
                HegelString.ByValue excludeCharacters,
                PointerByReference out);

        int hegel_string_generator_regex(
                Pointer ctx, HegelString.ByValue pattern, byte fullmatch, Pointer alphabet, PointerByReference out);

        int hegel_string_generator_email(Pointer ctx, PointerByReference out);

        int hegel_string_generator_url(Pointer ctx, PointerByReference out);

        int hegel_string_generator_domain(Pointer ctx, long maxLength, PointerByReference out);

        int hegel_string_generator_free(Pointer ctx, Pointer generator);

        int hegel_start_span(Pointer ctx, Pointer tc, long label);

        int hegel_stop_span(Pointer ctx, Pointer tc, byte discard);

        int hegel_new_collection(Pointer ctx, Pointer tc, long minSize, long maxSize, LongByReference out);

        int hegel_collection_more(Pointer ctx, Pointer tc, long id, ByteByReference out);

        int hegel_collection_reject(Pointer ctx, Pointer tc, long id, HegelString why);

        int hegel_new_pool(Pointer ctx, Pointer tc, LongByReference out);

        int hegel_pool_add(Pointer ctx, Pointer tc, long poolId, LongByReference out);

        int hegel_pool_generate(Pointer ctx, Pointer tc, long poolId, byte consume, LongByReference out);

        // State-machine handles cross as raw addresses (long), like every other opaque handle; the
        // name lists are contiguous hegel_string_t arrays (see strArray).
        int hegel_new_state_machine(
                Pointer ctx,
                Pointer tc,
                Pointer ruleNames,
                Pointer ruleGroups,
                Pointer ruleWeights,
                long ruleNamesLen,
                Pointer invariantNames,
                Pointer invariantAlwaysCheck,
                long invariantNamesLen,
                long minConcurrency,
                long maxConcurrency,
                long stepCount,
                LongByReference outStateMachine,
                LongByReference outConcurrency);

        int hegel_state_machine_next_group(Pointer ctx, Pointer tc, long stateMachineId, LongByReference out);

        int hegel_state_machine_next_rule(
                Pointer ctx, Pointer tc, long stateMachineId, long workerIndex, LongByReference out);

        int hegel_state_machine_rule_rejected(Pointer ctx, Pointer tc, long stateMachineId, long workerIndex);

        int hegel_state_machine_should_check_invariant(
                Pointer ctx, Pointer tc, long stateMachineId, long invariantIndex, ByteByReference out);

        int hegel_state_machine_free(Pointer ctx, long stateMachineId);

        int hegel_target(Pointer ctx, Pointer tc, double value, HegelString.ByValue label);

        int hegel_mark_complete(Pointer ctx, Pointer tc, int status, HegelString origin);

        int hegel_run_result_status(Pointer ctx, Pointer result, IntByReference out);

        int hegel_run_result_error(Pointer ctx, Pointer result, PointerByReference out);

        int hegel_run_result_failure_count(Pointer ctx, Pointer result, LongByReference out);

        int hegel_run_result_failure(Pointer ctx, Pointer result, long index, PointerByReference out);

        int hegel_failure_free(Pointer ctx, Pointer failure);

        int hegel_failure_reproduction_blob(Pointer ctx, Pointer failure, PointerByReference out);

        // A present string is written into the caller's hegel_string_t; an optional one (blob,
        // caveat) comes back as a `const hegel_string_t *` slot, NULL when absent.
        int hegel_failure_origin(Pointer ctx, Pointer failure, HegelString out);

        int hegel_failure_caveat(Pointer ctx, Pointer failure, PointerByReference out);

        int hegel_version(Pointer ctx, HegelString out);
    }

    /** {@code struct hegel_date_t { int32_t year; uint8_t month; uint8_t day; }} */
    @Structure.FieldOrder({"year", "month", "day"})
    public static class HegelDate extends Structure {
        public int year;
        public byte month;
        public byte day;

        public static class ByValue extends HegelDate implements Structure.ByValue {}
    }

    /** {@code struct hegel_time_t { uint8_t hour; uint8_t minute; uint8_t second; uint32_t nanosecond; }} */
    @Structure.FieldOrder({"hour", "minute", "second", "nanosecond"})
    public static class HegelTime extends Structure {
        public byte hour;
        public byte minute;
        public byte second;
        public int nanosecond;

        public static class ByValue extends HegelTime implements Structure.ByValue {}
    }

    /** {@code struct hegel_datetime_t { hegel_date_t date; hegel_time_t time; }} */
    @Structure.FieldOrder({"date", "time"})
    public static class HegelDatetime extends Structure {
        public HegelDate date;
        public HegelTime time;

        public static class ByValue extends HegelDatetime implements Structure.ByValue {}
    }

    /**
     * {@code struct hegel_string_t { const char *data; size_t len; }} — the shape every string
     * crosses in since libhegel 0.46 (by value, or by pointer where absent is meaningful), shared
     * by {@code hegel_owned_string_t} and {@code hegel_generate_bytes_result_t}. {@code len} counts
     * UTF-8 bytes; inputs need no NUL terminator and a zero length accepts a NULL {@code data}.
     */
    @Structure.FieldOrder({"data", "len"})
    public static class HegelString extends Structure {
        public Pointer data;
        public long len;

        public HegelString() {}

        /** A view over an engine-owned struct at {@code p}, read on construction. */
        HegelString(Pointer p) {
            super(p);
            read();
        }

        public static class ByValue extends HegelString implements Structure.ByValue {}
    }

    private static final int STRING_SIZE = new HegelString().size();

    interface OutputCallback extends Callback {
        void invoke(Pointer userData, Pointer line, long len);
    }

    /**
     * Bridges engine output lines to the run's {@link Consumer}. A named class rather than a
     * lambda so JNA can reliably resolve the callback method reflectively.
     */
    static final class LineCallback implements OutputCallback {
        private final Consumer<String> output;

        LineCallback(Consumer<String> output) {
            this.output = output;
        }

        @Override
        public void invoke(Pointer userData, Pointer line, long len) {
            emitLine(output, line, len);
        }
    }

    /**
     * Bridge one line of engine output to the run's {@link Consumer}. An exception escaping a
     * native callback must never unwind into the engine, so every throwable is swallowed here.
     */
    static void emitLine(Consumer<String> output, Pointer line, long len) {
        try {
            byte[] bytes = line.getByteArray(0, (int) len);
            output.accept(new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            // Deliberately dropped: output delivery must never unwind into the engine.
        }
    }

    private Pointer ctx() {
        return context.get();
    }

    private static Pointer pointer(long handle) {
        return handle == 0 ? null : new Pointer(handle);
    }

    private static long address(Pointer p) {
        return Pointer.nativeValue(p);
    }

    private static byte cbool(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private void check(String op, int code) {
        if (code != Abi.OK) {
            throw new HegelException(
                    op + " failed (rc=" + code + "): " + java.util.Objects.toString(lastErrorMessage(), ""));
        }
    }

    /**
     * A by-value {@code hegel_string_t} of {@code s}'s UTF-8 bytes. {@code null} crosses as {@code
     * {NULL, 0}}, which the engine reads as an empty string. The struct holds its buffer, so the
     * bytes stay alive as long as the struct is referenced (the call's argument list does).
     */
    private static HegelString.ByValue str(String s) {
        HegelString.ByValue value = new HegelString.ByValue();
        if (s != null) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            Memory buf = new Memory(Math.max(bytes.length, 1));
            buf.write(0, bytes, 0, bytes.length);
            value.data = buf;
            value.len = bytes.length;
        }
        return value;
    }

    /** A nullable {@code const hegel_string_t*}: {@code null} maps to NULL (absent), {@code ""} to present-and-empty. */
    private static HegelString strPtr(String s) {
        return s == null ? null : str(s);
    }

    /**
     * A NULL-distinct contiguous {@code hegel_string_t[]}: {@code null} maps to NULL, an empty list
     * to a valid pointer. The structs and the bytes they point at share one allocation, so the
     * block passed as the argument keeps everything alive for the call.
     */
    private static Pointer strArray(List<String> strings) {
        if (strings == null) {
            return null;
        }
        byte[][] encoded = new byte[strings.size()][];
        long total = (long) STRING_SIZE * strings.size();
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = strings.get(i).getBytes(StandardCharsets.UTF_8);
            total += encoded[i].length;
        }
        Memory block = new Memory(Math.max(total, 1));
        long offset = (long) STRING_SIZE * strings.size();
        for (int i = 0; i < encoded.length; i++) {
            long entry = (long) STRING_SIZE * i;
            // A zero-length string needs no bytes; NULL data is accepted with a zero length.
            block.setPointer(entry, encoded[i].length == 0 ? null : block.share(offset));
            block.setLong(entry + Native.POINTER_SIZE, encoded[i].length);
            block.write(offset, encoded[i], 0, encoded[i].length);
            offset += encoded[i].length;
        }
        return block;
    }

    /**
     * Read a {@code hegel_string_t} the engine filled in ({@code len} bytes of UTF-8, interior NULs
     * included). A NULL {@code data} pointer reads as {@code null}.
     */
    static String readString(HegelString string) {
        return string.data == null ? null : new String(copyBuffer(string), StandardCharsets.UTF_8);
    }

    /** Read through a {@code const hegel_string_t*}: NULL (absent) reads as {@code null}. */
    static String readStringAt(Pointer string) {
        return string == null ? null : readString(new HegelString(string));
    }

    // --- settings ---

    @Override
    public int settingsNew(long[] out) {
        // The reference starts out NULL and the engine writes it only on success, so a failed call
        // reports 0 without a branch of its own.
        PointerByReference handle = new PointerByReference();
        int code = lib.hegel_settings_new(ctx(), handle);
        out[0] = address(handle.getValue());
        return code;
    }

    @Override
    public void settingsFree(long s) {
        check("hegel_settings_free", lib.hegel_settings_free(ctx(), pointer(s)));
    }

    @Override
    public void settingsBackend(long s, int backend) {
        check("hegel_settings_set_backend", lib.hegel_settings_set_backend(ctx(), pointer(s), backend));
    }

    @Override
    public void settingsTestCases(long s, long n) {
        check("hegel_settings_set_test_cases", lib.hegel_settings_set_test_cases(ctx(), pointer(s), n));
    }

    @Override
    public void settingsVerbosity(long s, int v) {
        check("hegel_settings_set_verbosity", lib.hegel_settings_set_verbosity(ctx(), pointer(s), v));
    }

    @Override
    public void settingsSeed(long s, long seed, boolean hasSeed) {
        check("hegel_settings_set_seed", lib.hegel_settings_set_seed(ctx(), pointer(s), seed, cbool(hasSeed)));
    }

    @Override
    public void settingsDerandomize(long s, boolean derandomize) {
        check(
                "hegel_settings_set_derandomize",
                lib.hegel_settings_set_derandomize(ctx(), pointer(s), cbool(derandomize)));
    }

    @Override
    public void settingsReportMultipleFailures(long s, boolean yes) {
        check(
                "hegel_settings_set_report_multiple_failures",
                lib.hegel_settings_set_report_multiple_failures(ctx(), pointer(s), cbool(yes)));
    }

    @Override
    public void settingsDatabase(long s, String path) {
        check("hegel_settings_set_database", lib.hegel_settings_set_database(ctx(), pointer(s), strPtr(path)));
    }

    @Override
    public void settingsDatabaseKey(long s, String key) {
        check("hegel_settings_set_database_key", lib.hegel_settings_set_database_key(ctx(), pointer(s), strPtr(key)));
    }

    @Override
    public void settingsPhases(long s, int mask) {
        check("hegel_settings_set_phases", lib.hegel_settings_set_phases(ctx(), pointer(s), mask));
    }

    @Override
    public void settingsSuppressHealthCheck(long s, int mask) {
        check(
                "hegel_settings_set_suppress_health_check",
                lib.hegel_settings_set_suppress_health_check(ctx(), pointer(s), mask));
    }

    @Override
    public void settingsPrintBlob(long s, boolean yes) {
        check("hegel_settings_set_print_blob", lib.hegel_settings_set_print_blob(ctx(), pointer(s), cbool(yes)));
    }

    @Override
    public long settingsGetTestCases(long s) {
        LongByReference out = new LongByReference();
        check("hegel_settings_get_test_cases", lib.hegel_settings_get_test_cases(ctx(), pointer(s), out));
        return out.getValue();
    }

    @Override
    public boolean settingsGetPrintBlob(long s) {
        ByteByReference out = new ByteByReference();
        check("hegel_settings_get_print_blob", lib.hegel_settings_get_print_blob(ctx(), pointer(s), out));
        return out.getValue() != 0;
    }

    @Override
    public void settingsNondeterminismStrictness(long s, int strictness) {
        check(
                "hegel_settings_set_nondeterminism_strictness",
                lib.hegel_settings_set_nondeterminism_strictness(ctx(), pointer(s), strictness));
    }

    @Override
    public int settingsGetNondeterminismStrictness(long s) {
        IntByReference out = new IntByReference();
        check(
                "hegel_settings_get_nondeterminism_strictness",
                lib.hegel_settings_get_nondeterminism_strictness(ctx(), pointer(s), out));
        return out.getValue();
    }

    // --- run lifecycle ---

    @Override
    public long runStart(long settings, Consumer<String> output) {
        LineCallback callback = output == null ? null : new LineCallback(output);
        PointerByReference out = new PointerByReference();
        int code = lib.hegel_run_start(ctx(), pointer(settings), callback, null, out);
        if (code != Abi.OK) {
            throw new HegelException(
                    "hegel_run_start failed (rc=" + code + "): " + java.util.Objects.toString(lastErrorMessage(), ""));
        }
        long run = address(out.getValue());
        if (callback != null) {
            runCallbacks.put(run, callback);
        }
        return run;
    }

    @Override
    public long runStartBlob(long settings, String blob, Consumer<String> output) {
        LineCallback callback = output == null ? null : new LineCallback(output);
        PointerByReference out = new PointerByReference();
        int code = lib.hegel_run_start_blob(ctx(), pointer(settings), str(blob), callback, null, out);
        if (code != Abi.OK) {
            throw new HegelException("hegel_run_start_blob failed (rc="
                    + code
                    + "): "
                    + java.util.Objects.toString(lastErrorMessage(), ""));
        }
        long run = address(out.getValue());
        if (callback != null) {
            runCallbacks.put(run, callback);
        }
        return run;
    }

    @Override
    public long nextTestCase(long run) {
        PointerByReference out = new PointerByReference();
        check("hegel_next_test_case", lib.hegel_next_test_case(ctx(), pointer(run), out));
        return address(out.getValue());
    }

    @Override
    public long runResult(long run) {
        PointerByReference out = new PointerByReference();
        check("hegel_run_result", lib.hegel_run_result(ctx(), pointer(run), out));
        return address(out.getValue());
    }

    @Override
    public void runResultFree(long result) {
        check("hegel_run_result_free", lib.hegel_run_result_free(ctx(), pointer(result)));
    }

    @Override
    public void runFree(long run) {
        check("hegel_run_free", lib.hegel_run_free(ctx(), pointer(run)));
        runCallbacks.remove(run);
    }

    @Override
    public int testCaseFromBlob(long settings, String blob, Consumer<String> output, long[] out) {
        // The blob replay's output is emitted synchronously during this call, so the callback only
        // needs to live for its duration.
        LineCallback callback = output == null ? null : new LineCallback(output);
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_test_case_from_blob(ctx(), pointer(settings), str(blob), callback, null, outRef);
        if (code == Abi.OK) {
            out[0] = address(outRef.getValue());
        }
        return code;
    }

    @Override
    public void testCaseFree(long tc) {
        check("hegel_test_case_free", lib.hegel_test_case_free(ctx(), pointer(tc)));
    }

    @Override
    public boolean testCaseShouldCapture(long tc) {
        ByteByReference out = new ByteByReference();
        check("hegel_test_case_should_capture", lib.hegel_test_case_should_capture(ctx(), pointer(tc), out));
        return out.getValue() != 0;
    }

    @Override
    public int testCaseClone(long tc, long[] out) {
        PointerByReference ref = new PointerByReference();
        int code = lib.hegel_test_case_clone(ctx(), pointer(tc), ref);
        if (code == Abi.OK) {
            out[0] = address(ref.getValue());
        }
        return code;
    }

    @Override
    public void testCaseSetWorker(long tc, long workerIndex) {
        check("hegel_test_case_set_worker", lib.hegel_test_case_set_worker(ctx(), pointer(tc), workerIndex));
    }

    // --- draws ---

    @Override
    public int generateBoolean(long tc, double p, boolean[] out) {
        ByteByReference ref = new ByteByReference();
        int code = lib.hegel_generate_boolean(ctx(), pointer(tc), p, cbool(false), cbool(false), ref);
        if (code == Abi.OK) {
            out[0] = ref.getValue() != 0;
        }
        return code;
    }

    @Override
    public int generateInteger(long tc, long min, long max, long[] out) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_generate_integer(ctx(), pointer(tc), min, max, ref);
        if (code == Abi.OK) {
            out[0] = ref.getValue();
        }
        return code;
    }

    @Override
    public int generateFloat(
            long tc,
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude,
            double[] out) {
        DoubleByReference ref = new DoubleByReference();
        int code = lib.hegel_generate_float(
                ctx(),
                pointer(tc),
                width,
                min,
                max,
                cbool(allowNan),
                cbool(allowInfinity),
                cbool(excludeMin),
                cbool(excludeMax),
                smallestNonzeroMagnitude,
                ref);
        if (code == Abi.OK) {
            out[0] = ref.getValue();
        }
        return code;
    }

    @Override
    public int generateBytes(long tc, long minSize, long maxSize, byte[][] out) {
        HegelString result = new HegelString();
        int code = lib.hegel_generate_bytes(ctx(), pointer(tc), minSize, maxSize, result);
        if (code == Abi.OK) {
            out[0] = copyBuffer(result);
            check("hegel_generate_bytes_result_free", lib.hegel_generate_bytes_result_free(ctx(), result));
        }
        return code;
    }

    @Override
    public int generateString(long tc, long generator, String[] out) {
        // An owned string: {data, len} plus a NUL the length excludes, released with
        // hegel_owned_string_free once copied out.
        HegelString result = new HegelString();
        int code = lib.hegel_generate_string(ctx(), pointer(tc), pointer(generator), result);
        if (code == Abi.OK) {
            out[0] = new String(copyBuffer(result), StandardCharsets.UTF_8);
            check("hegel_owned_string_free", lib.hegel_owned_string_free(ctx(), result));
        }
        return code;
    }

    /** Copy the {@code len} bytes an engine-filled {@code {data, len}} struct points at. */
    private static byte[] copyBuffer(HegelString result) {
        return result.data.getByteArray(0, (int) result.len);
    }

    @Override
    public int generateDate(long tc, LocalDate min, LocalDate max, LocalDate[] out) {
        HegelDate result = new HegelDate();
        int code = lib.hegel_generate_date(ctx(), pointer(tc), dateValue(min), dateValue(max), result);
        if (code == Abi.OK) {
            out[0] = readDate(result);
        }
        return code;
    }

    @Override
    public int generateTime(long tc, LocalTime min, LocalTime max, LocalTime[] out) {
        HegelTime result = new HegelTime();
        int code = lib.hegel_generate_time(ctx(), pointer(tc), timeValue(min), timeValue(max), result);
        if (code == Abi.OK) {
            out[0] = readTime(result);
        }
        return code;
    }

    @Override
    public int generateDatetime(long tc, LocalDateTime min, LocalDateTime max, LocalDateTime[] out) {
        HegelDatetime result = new HegelDatetime();
        int code = lib.hegel_generate_datetime(ctx(), pointer(tc), datetimeValue(min), datetimeValue(max), result);
        if (code == Abi.OK) {
            out[0] = LocalDateTime.of(readDate(result.date), readTime(result.time));
        }
        return code;
    }

    private static HegelDate.ByValue dateValue(LocalDate date) {
        HegelDate.ByValue value = new HegelDate.ByValue();
        fillDate(value, date);
        return value;
    }

    private static HegelTime.ByValue timeValue(LocalTime time) {
        HegelTime.ByValue value = new HegelTime.ByValue();
        fillTime(value, time);
        return value;
    }

    private static HegelDatetime.ByValue datetimeValue(LocalDateTime dt) {
        HegelDatetime.ByValue value = new HegelDatetime.ByValue();
        value.date = new HegelDate();
        value.time = new HegelTime();
        fillDate(value.date, dt.toLocalDate());
        fillTime(value.time, dt.toLocalTime());
        return value;
    }

    private static void fillDate(HegelDate value, LocalDate date) {
        value.year = date.getYear();
        value.month = (byte) date.getMonthValue();
        value.day = (byte) date.getDayOfMonth();
    }

    private static LocalDate readDate(HegelDate value) {
        return LocalDate.of(value.year, value.month, value.day);
    }

    private static void fillTime(HegelTime value, LocalTime time) {
        value.hour = (byte) time.getHour();
        value.minute = (byte) time.getMinute();
        value.second = (byte) time.getSecond();
        value.nanosecond = time.getNano();
    }

    private static LocalTime readTime(HegelTime value) {
        return LocalTime.of(value.hour, value.minute, value.second, value.nanosecond);
    }

    @Override
    public int generateUuid(long tc, int version, boolean hasVersion, byte[] out16) {
        return fixedBytesDraw(
                buf -> lib.hegel_generate_uuid(ctx(), pointer(tc), (byte) version, cbool(hasVersion), buf), out16);
    }

    @Override
    public int generateIpv4(long tc, byte[] out4) {
        return fixedBytesDraw(buf -> lib.hegel_generate_ipv4(ctx(), pointer(tc), buf), out4);
    }

    @Override
    public int generateIpv6(long tc, byte[] out16) {
        return fixedBytesDraw(buf -> lib.hegel_generate_ipv6(ctx(), pointer(tc), buf), out16);
    }

    @FunctionalInterface
    private interface BytesDraw {
        int run(Pointer out);
    }

    /** Run a draw writing into a fixed-size byte buffer, copying it out on success. */
    private static int fixedBytesDraw(BytesDraw draw, byte[] out) {
        Memory buf = new Memory(out.length);
        int code = draw.run(buf);
        if (code == Abi.OK) {
            buf.read(0, out, 0, out.length);
        }
        return code;
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
            long[] out) {
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_text(
                ctx(),
                minSize,
                maxSize,
                strPtr(codec),
                (int) minCodepoint,
                (int) maxCodepoint,
                strArray(categories),
                categories == null ? 0L : categories.size(),
                strArray(excludeCategories),
                excludeCategories == null ? 0L : excludeCategories.size(),
                str(includeCharacters),
                str(excludeCharacters),
                outRef);
        // Read the (null-initialised) out slot unconditionally: callers check the return code
        // before using it.
        out[0] = address(outRef.getValue());
        return code;
    }

    @Override
    public int stringGeneratorRegex(String pattern, boolean fullmatch, long alphabet, long[] out) {
        // The pattern crosses as a length-delimited string, so it may contain NUL, which Python's
        // `re` accepts.
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_regex(ctx(), str(pattern), cbool(fullmatch), pointer(alphabet), outRef);
        out[0] = address(outRef.getValue());
        return code;
    }

    @Override
    public int stringGeneratorEmail(long[] out) {
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_email(ctx(), outRef);
        out[0] = address(outRef.getValue());
        return code;
    }

    @Override
    public int stringGeneratorUrl(long[] out) {
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_url(ctx(), outRef);
        out[0] = address(outRef.getValue());
        return code;
    }

    @Override
    public int stringGeneratorDomain(long maxLength, long[] out) {
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_domain(ctx(), maxLength, outRef);
        out[0] = address(outRef.getValue());
        return code;
    }

    @Override
    public void stringGeneratorFree(long generator) {
        check("hegel_string_generator_free", lib.hegel_string_generator_free(ctx(), pointer(generator)));
    }

    // --- structure ---

    @Override
    public int startSpan(long tc, long label) {
        return lib.hegel_start_span(ctx(), pointer(tc), label);
    }

    @Override
    public int stopSpan(long tc, boolean discard) {
        return lib.hegel_stop_span(ctx(), pointer(tc), cbool(discard));
    }

    @Override
    public int newCollection(long tc, long minSize, long maxSize, long[] outId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_new_collection(ctx(), pointer(tc), minSize, maxSize, ref);
        outId[0] = ref.getValue();
        return code;
    }

    @Override
    public int collectionMore(long tc, long id, boolean[] outMore) {
        ByteByReference ref = new ByteByReference();
        int code = lib.hegel_collection_more(ctx(), pointer(tc), id, ref);
        if (code == Abi.OK) {
            outMore[0] = ref.getValue() != 0;
        }
        return code;
    }

    @Override
    public int collectionReject(long tc, long id, String why) {
        return lib.hegel_collection_reject(ctx(), pointer(tc), id, strPtr(why));
    }

    @Override
    public int newPool(long tc, long[] outId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_new_pool(ctx(), pointer(tc), ref);
        outId[0] = ref.getValue();
        return code;
    }

    @Override
    public int poolAdd(long tc, long poolId, long[] outVariableId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_pool_add(ctx(), pointer(tc), poolId, ref);
        outVariableId[0] = ref.getValue();
        return code;
    }

    @Override
    public int poolGenerate(long tc, long poolId, boolean consume, long[] outVariableId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_pool_generate(ctx(), pointer(tc), poolId, cbool(consume), ref);
        outVariableId[0] = ref.getValue();
        return code;
    }

    @Override
    public int newStateMachine(
            long tc,
            List<String> ruleNames,
            long[] ruleGroups,
            double[] ruleWeights,
            List<String> invariantNames,
            boolean[] invariantAlwaysCheck,
            long minConcurrency,
            long maxConcurrency,
            long stepCount,
            long[] outId,
            long[] outConcurrency) {
        Memory groups = new Memory(8L * Math.max(ruleGroups.length, 1));
        groups.write(0, ruleGroups, 0, ruleGroups.length);
        // NULL keeps every rule at the same weight.
        Pointer weights = Pointer.NULL;
        if (ruleWeights != null) {
            Memory m = new Memory(8L * Math.max(ruleWeights.length, 1));
            m.write(0, ruleWeights, 0, ruleWeights.length);
            weights = m;
        }
        byte[] flags = new byte[invariantAlwaysCheck.length];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = cbool(invariantAlwaysCheck[i]);
        }
        Memory alwaysCheck = new Memory(Math.max(flags.length, 1));
        alwaysCheck.write(0, flags, 0, flags.length);
        LongByReference id = new LongByReference();
        LongByReference concurrency = new LongByReference();
        int code = lib.hegel_new_state_machine(
                ctx(),
                pointer(tc),
                strArray(ruleNames),
                groups,
                weights,
                ruleNames.size(),
                strArray(invariantNames),
                alwaysCheck,
                invariantNames.size(),
                minConcurrency,
                maxConcurrency,
                stepCount,
                id,
                concurrency);
        if (code == Abi.OK) {
            outId[0] = id.getValue();
            outConcurrency[0] = concurrency.getValue();
        }
        return code;
    }

    @Override
    public int stateMachineNextGroup(long tc, long stateMachineId, long[] outGroupId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_state_machine_next_group(ctx(), pointer(tc), stateMachineId, ref);
        if (code == Abi.OK) {
            outGroupId[0] = ref.getValue();
        }
        return code;
    }

    @Override
    public int stateMachineNextRule(long tc, long stateMachineId, long workerIndex, long[] outRuleIndex) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_state_machine_next_rule(ctx(), pointer(tc), stateMachineId, workerIndex, ref);
        if (code == Abi.OK) {
            outRuleIndex[0] = ref.getValue();
        }
        return code;
    }

    @Override
    public int stateMachineRuleRejected(long tc, long stateMachineId, long workerIndex) {
        return lib.hegel_state_machine_rule_rejected(ctx(), pointer(tc), stateMachineId, workerIndex);
    }

    @Override
    public int stateMachineShouldCheckInvariant(
            long tc, long stateMachineId, long invariantIndex, boolean[] outShouldCheck) {
        ByteByReference ref = new ByteByReference();
        int code =
                lib.hegel_state_machine_should_check_invariant(ctx(), pointer(tc), stateMachineId, invariantIndex, ref);
        if (code == Abi.OK) {
            outShouldCheck[0] = ref.getValue() != 0;
        }
        return code;
    }

    @Override
    public void stateMachineFree(long stateMachineId) {
        check("hegel_state_machine_free", lib.hegel_state_machine_free(ctx(), stateMachineId));
    }

    @Override
    public int target(long tc, double value, String label) {
        return lib.hegel_target(ctx(), pointer(tc), value, str(label));
    }

    @Override
    public int markComplete(long tc, int status, String origin) {
        return lib.hegel_mark_complete(ctx(), pointer(tc), status, strPtr(origin));
    }

    // --- results ---

    @Override
    public int runResultStatus(long result) {
        IntByReference ref = new IntByReference();
        check("hegel_run_result_status", lib.hegel_run_result_status(ctx(), pointer(result), ref));
        return ref.getValue();
    }

    @Override
    public String runResultError(long result) {
        PointerByReference ref = new PointerByReference();
        check("hegel_run_result_error", lib.hegel_run_result_error(ctx(), pointer(result), ref));
        return readStringAt(ref.getValue());
    }

    @Override
    public long runResultFailureCount(long result) {
        LongByReference ref = new LongByReference();
        check("hegel_run_result_failure_count", lib.hegel_run_result_failure_count(ctx(), pointer(result), ref));
        return ref.getValue();
    }

    @Override
    public String failureBlob(long result, long index) {
        PointerByReference failureOut = new PointerByReference();
        check("hegel_run_result_failure", lib.hegel_run_result_failure(ctx(), pointer(result), index, failureOut));
        Pointer failure = failureOut.getValue();
        PointerByReference blobOut = new PointerByReference();
        check("hegel_failure_reproduction_blob", lib.hegel_failure_reproduction_blob(ctx(), failure, blobOut));
        String blob = readStringAt(blobOut.getValue());
        check("hegel_failure_free", lib.hegel_failure_free(ctx(), failure));
        return blob;
    }

    @Override
    public String failureOrigin(long result, long index) {
        PointerByReference failureOut = new PointerByReference();
        check("hegel_run_result_failure", lib.hegel_run_result_failure(ctx(), pointer(result), index, failureOut));
        Pointer failure = failureOut.getValue();
        HegelString originOut = new HegelString();
        check("hegel_failure_origin", lib.hegel_failure_origin(ctx(), failure, originOut));
        String origin = readString(originOut);
        check("hegel_failure_free", lib.hegel_failure_free(ctx(), failure));
        return origin;
    }

    @Override
    public String failureCaveat(long result, long index) {
        PointerByReference failureOut = new PointerByReference();
        check("hegel_run_result_failure", lib.hegel_run_result_failure(ctx(), pointer(result), index, failureOut));
        Pointer failure = failureOut.getValue();
        PointerByReference caveatOut = new PointerByReference();
        check("hegel_failure_caveat", lib.hegel_failure_caveat(ctx(), failure, caveatOut));
        String caveat = readStringAt(caveatOut.getValue());
        check("hegel_failure_free", lib.hegel_failure_free(ctx(), failure));
        return caveat;
    }

    // --- diagnostics ---

    @Override
    public String lastErrorMessage() {
        // Returned by value; the bytes borrow the context's buffer and are copied out here.
        return readString(lib.hegel_context_last_error(ctx()));
    }

    @Override
    public String version() {
        HegelString out = new HegelString();
        check("hegel_version", lib.hegel_version(ctx(), out));
        return readString(out);
    }
}
