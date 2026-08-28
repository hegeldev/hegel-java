package dev.hegel;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.Structure;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
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
            this.lib = Native.load(
                    libraryPath.toString(), HegelNative.class, Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        } catch (UnsatisfiedLinkError e) {
            throw new HegelException("Failed to open libhegel at " + libraryPath + ": " + e.getMessage());
        }
        this.context = ThreadLocal.withInitial(lib::hegel_context_new);
    }

    interface HegelNative extends Library {
        Pointer hegel_context_new();

        Pointer hegel_context_last_error(Pointer ctx);

        int hegel_settings_new(Pointer ctx, PointerByReference out);

        int hegel_settings_free(Pointer ctx, Pointer s);

        int hegel_settings_set_mode(Pointer ctx, Pointer s, int mode);

        int hegel_settings_set_backend(Pointer ctx, Pointer s, int backend);

        int hegel_settings_set_test_cases(Pointer ctx, Pointer s, long n);

        int hegel_settings_set_verbosity(Pointer ctx, Pointer s, int v);

        int hegel_settings_set_seed(Pointer ctx, Pointer s, long seed, byte hasSeed);

        int hegel_settings_set_derandomize(Pointer ctx, Pointer s, byte derandomize);

        int hegel_settings_set_report_multiple_failures(Pointer ctx, Pointer s, byte yes);

        int hegel_settings_set_database(Pointer ctx, Pointer s, String path);

        int hegel_settings_set_database_key(Pointer ctx, Pointer s, String key);

        int hegel_settings_set_phases(Pointer ctx, Pointer s, int mask);

        int hegel_settings_set_suppress_health_check(Pointer ctx, Pointer s, int mask);

        int hegel_run_start(
                Pointer ctx, Pointer settings, LineCallback callback, Pointer userData, PointerByReference out);

        int hegel_next_test_case(Pointer ctx, Pointer run, PointerByReference out);

        int hegel_run_result(Pointer ctx, Pointer run, PointerByReference out);

        int hegel_run_result_free(Pointer ctx, Pointer result);

        int hegel_run_free(Pointer ctx, Pointer run);

        int hegel_test_case_from_blob(
                Pointer ctx,
                Pointer settings,
                String blob,
                LineCallback callback,
                Pointer userData,
                PointerByReference out);

        int hegel_test_case_free(Pointer ctx, Pointer tc);

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

        int hegel_generate_bytes(Pointer ctx, Pointer tc, long minSize, long maxSize, BufferResult out);

        int hegel_generate_bytes_result_free(Pointer ctx, BufferResult result);

        int hegel_generate_string(Pointer ctx, Pointer tc, Pointer generator, BufferResult out);

        int hegel_generate_string_result_free(Pointer ctx, BufferResult result);

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
                String codec,
                int minCodepoint,
                int maxCodepoint,
                Pointer categories,
                long categoriesLen,
                Pointer excludeCategories,
                long excludeCategoriesLen,
                Pointer includeCharacters,
                long includeCharactersLen,
                Pointer excludeCharacters,
                long excludeCharactersLen,
                PointerByReference out);

        int hegel_string_generator_regex(
                Pointer ctx, String pattern, byte fullmatch, Pointer alphabet, PointerByReference out);

        int hegel_string_generator_email(Pointer ctx, PointerByReference out);

        int hegel_string_generator_url(Pointer ctx, PointerByReference out);

        int hegel_string_generator_domain(Pointer ctx, long maxLength, PointerByReference out);

        int hegel_string_generator_free(Pointer ctx, Pointer generator);

        int hegel_start_span(Pointer ctx, Pointer tc, long label);

        int hegel_stop_span(Pointer ctx, Pointer tc, byte discard);

        int hegel_new_collection(Pointer ctx, Pointer tc, long minSize, long maxSize, LongByReference out);

        int hegel_collection_more(Pointer ctx, Pointer tc, long id, ByteByReference out);

        int hegel_collection_reject(Pointer ctx, Pointer tc, long id, String why);

        int hegel_new_pool(Pointer ctx, Pointer tc, LongByReference out);

        int hegel_pool_add(Pointer ctx, Pointer tc, long poolId, LongByReference out);

        int hegel_pool_generate(Pointer ctx, Pointer tc, long poolId, byte consume, LongByReference out);

        int hegel_new_state_machine(
                Pointer ctx,
                Pointer tc,
                Pointer ruleNames,
                long ruleNamesLen,
                Pointer invariantNames,
                long invariantNamesLen,
                LongByReference out);

        int hegel_state_machine_next_rule(Pointer ctx, Pointer tc, long stateMachineId, LongByReference out);

        int hegel_target(Pointer ctx, Pointer tc, double value, String label);

        int hegel_mark_complete(Pointer ctx, Pointer tc, int status, String origin);

        int hegel_run_result_status(Pointer ctx, Pointer result, IntByReference out);

        int hegel_run_result_error(Pointer ctx, Pointer result, PointerByReference out);

        int hegel_run_result_failure_count(Pointer ctx, Pointer result, LongByReference out);

        int hegel_run_result_failure(Pointer ctx, Pointer result, long index, PointerByReference out);

        int hegel_failure_free(Pointer ctx, Pointer failure);

        int hegel_failure_reproduction_blob(Pointer ctx, Pointer failure, PointerByReference out);

        int hegel_version(Pointer ctx, PointerByReference out);
    }

    /** {@code struct hegel_date_t { int32_t year; uint8_t month; uint8_t day; }} */
    @Structure.FieldOrder({"year", "month", "day"})
    public static class HegelDate extends Structure {
        public int year;
        public byte month;
        public byte day;

        public static class ByValue extends HegelDate implements Structure.ByValue {}
    }

    /** {@code struct hegel_time_t { uint8_t hour; uint8_t minute; uint8_t second; uint32_t microsecond; }} */
    @Structure.FieldOrder({"hour", "minute", "second", "microsecond"})
    public static class HegelTime extends Structure {
        public byte hour;
        public byte minute;
        public byte second;
        public int microsecond;

        public static class ByValue extends HegelTime implements Structure.ByValue {}
    }

    /** {@code struct hegel_datetime_t { hegel_date_t date; hegel_time_t time; }} */
    @Structure.FieldOrder({"date", "time"})
    public static class HegelDatetime extends Structure {
        public HegelDate date;
        public HegelTime time;

        public static class ByValue extends HegelDatetime implements Structure.ByValue {}
    }

    /** {@code struct hegel_generate_bytes_result_t / hegel_generate_string_result_t { T* data; size_t len; }} */
    @Structure.FieldOrder({"data", "len"})
    public static class BufferResult extends Structure {
        public Pointer data;
        public long len;
    }

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

    static String readCString(Pointer ptr) {
        return ptr == null ? null : ptr.getString(0, "UTF-8");
    }

    // --- settings ---

    @Override
    public long settingsNew() {
        PointerByReference out = new PointerByReference();
        check("hegel_settings_new", lib.hegel_settings_new(ctx(), out));
        return address(out.getValue());
    }

    @Override
    public void settingsFree(long s) {
        check("hegel_settings_free", lib.hegel_settings_free(ctx(), pointer(s)));
    }

    @Override
    public void settingsMode(long s, int mode) {
        check("hegel_settings_set_mode", lib.hegel_settings_set_mode(ctx(), pointer(s), mode));
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
        check("hegel_settings_set_database", lib.hegel_settings_set_database(ctx(), pointer(s), path));
    }

    @Override
    public void settingsDatabaseKey(long s, String key) {
        check("hegel_settings_set_database_key", lib.hegel_settings_set_database_key(ctx(), pointer(s), key));
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
        int code = lib.hegel_test_case_from_blob(ctx(), pointer(settings), blob, callback, null, outRef);
        if (code == Abi.OK) {
            out[0] = address(outRef.getValue());
        }
        return code;
    }

    @Override
    public void testCaseFree(long tc) {
        check("hegel_test_case_free", lib.hegel_test_case_free(ctx(), pointer(tc)));
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
        BufferResult result = new BufferResult();
        int code = lib.hegel_generate_bytes(ctx(), pointer(tc), minSize, maxSize, result);
        if (code == Abi.OK) {
            out[0] = copyBuffer(result);
            check("hegel_generate_bytes_result_free", lib.hegel_generate_bytes_result_free(ctx(), result));
        }
        return code;
    }

    @Override
    public int generateString(long tc, long generator, String[] out) {
        BufferResult result = new BufferResult();
        int code = lib.hegel_generate_string(ctx(), pointer(tc), pointer(generator), result);
        if (code == Abi.OK) {
            out[0] = new String(copyBuffer(result), StandardCharsets.UTF_8);
            check("hegel_generate_string_result_free", lib.hegel_generate_string_result_free(ctx(), result));
        }
        return code;
    }

    /** Copy an engine-allocated {@code {data, len}} buffer out before it is freed. */
    private static byte[] copyBuffer(BufferResult result) {
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
        value.microsecond = time.getNano() / 1_000;
    }

    private static LocalTime readTime(HegelTime value) {
        return LocalTime.of(value.hour, value.minute, value.second, value.microsecond * 1_000);
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
        Pointer categoriesArr = cstrArray(categories);
        Pointer excludeArr = cstrArray(excludeCategories);
        byte[] include = utf8OrNull(includeCharacters);
        byte[] exclude = utf8OrNull(excludeCharacters);
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_text(
                ctx(),
                minSize,
                maxSize,
                codec,
                (int) minCodepoint,
                (int) maxCodepoint,
                categoriesArr,
                categories == null ? 0L : categories.size(),
                excludeArr,
                excludeCategories == null ? 0L : excludeCategories.size(),
                bytesOrNull(include),
                include == null ? 0L : include.length,
                bytesOrNull(exclude),
                exclude == null ? 0L : exclude.length,
                outRef);
        // Read the (null-initialised) out slot unconditionally: callers check the return code
        // before using it.
        out[0] = address(outRef.getValue());
        return code;
    }

    private static byte[] utf8OrNull(String s) {
        return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
    }

    private static Pointer bytesOrNull(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        Memory buf = new Memory(Math.max(bytes.length, 1));
        buf.write(0, bytes, 0, bytes.length);
        return buf;
    }

    /** A NULL-distinct {@code char**}: {@code null} maps to NULL, an empty list to a valid pointer. */
    private static Pointer cstrArray(List<String> strings) {
        return strings == null ? null : new StringArray(strings.toArray(new String[0]), "UTF-8");
    }

    @Override
    public int stringGeneratorRegex(String pattern, boolean fullmatch, long alphabet, long[] out) {
        PointerByReference outRef = new PointerByReference();
        int code = lib.hegel_string_generator_regex(ctx(), pattern, cbool(fullmatch), pointer(alphabet), outRef);
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
        return lib.hegel_collection_reject(ctx(), pointer(tc), id, why);
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
    public int newStateMachine(long tc, List<String> ruleNames, List<String> invariantNames, long[] outId) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_new_state_machine(
                ctx(),
                pointer(tc),
                cstrArray(ruleNames),
                ruleNames.size(),
                cstrArray(invariantNames),
                invariantNames.size(),
                ref);
        outId[0] = ref.getValue();
        return code;
    }

    @Override
    public int stateMachineNextRule(long tc, long stateMachineId, long[] outRuleIndex) {
        LongByReference ref = new LongByReference();
        int code = lib.hegel_state_machine_next_rule(ctx(), pointer(tc), stateMachineId, ref);
        if (code == Abi.OK) {
            outRuleIndex[0] = ref.getValue();
        }
        return code;
    }

    @Override
    public int target(long tc, double value, String label) {
        return lib.hegel_target(ctx(), pointer(tc), value, label);
    }

    @Override
    public int markComplete(long tc, int status, String origin) {
        return lib.hegel_mark_complete(ctx(), pointer(tc), status, origin);
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
        return readCString(ref.getValue());
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
        String blob = readCString(blobOut.getValue());
        check("hegel_failure_free", lib.hegel_failure_free(ctx(), failure));
        return blob;
    }

    // --- diagnostics ---

    @Override
    public String lastErrorMessage() {
        return readCString(lib.hegel_context_last_error(ctx()));
    }

    @Override
    public String version() {
        PointerByReference ref = new PointerByReference();
        check("hegel_version", lib.hegel_version(ctx(), ref));
        return readCString(ref.getValue());
    }
}
