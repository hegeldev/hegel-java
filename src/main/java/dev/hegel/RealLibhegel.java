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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real libhegel binding, driving the C ABI over the Foreign Function and Memory API.
 *
 * <p>Resolves every C symbol once and caches the resulting {@link MethodHandle}s, which are
 * immutable after construction and safe to share across threads. Every call routes through {@link
 * #invoke} so error translation and the one-place FFI try/catch live in a single method.
 */
final class RealLibhegel implements Libhegel {
  private final Arena libArena;

  // libhegel borrows the database / database_key C-strings until hegel_run_start consumes the
  // settings, so they must outlive their setter call. Each settings handle gets a confined arena
  // (created in settingsNew, closed in settingsFree) that owns those buffers for its whole life.
  private final Map<Long, Arena> settingsArenas = new ConcurrentHashMap<>();

  private final MethodHandle settingsNew;
  private final MethodHandle settingsFree;
  private final MethodHandle settingsMode;
  private final MethodHandle settingsTestCases;
  private final MethodHandle settingsVerbosity;
  private final MethodHandle settingsSeed;
  private final MethodHandle settingsDerandomize;
  private final MethodHandle settingsReportMultipleFailures;
  private final MethodHandle settingsDatabase;
  private final MethodHandle settingsDatabaseKey;
  private final MethodHandle settingsPhases;
  private final MethodHandle settingsSuppressHealthCheck;
  private final MethodHandle runStart;
  private final MethodHandle nextTestCase;
  private final MethodHandle runResult;
  private final MethodHandle runFree;
  private final MethodHandle generate;
  private final MethodHandle startSpan;
  private final MethodHandle stopSpan;
  private final MethodHandle newCollection;
  private final MethodHandle collectionMore;
  private final MethodHandle collectionReject;
  private final MethodHandle target;
  private final MethodHandle markComplete;
  private final MethodHandle isFinalReplay;
  private final MethodHandle resultPassed;
  private final MethodHandle resultFailureCount;
  private final MethodHandle resultFailure;
  private final MethodHandle failurePanicMessage;
  private final MethodHandle failureDiagnostic;
  private final MethodHandle failureOrigin;
  private final MethodHandle lastErrorMessage;
  private final MethodHandle version;

  RealLibhegel(Path libraryPath) {
    this.libArena = Arena.ofShared();
    Linker linker = Linker.nativeLinker();
    SymbolLookup lookup;
    try {
      lookup = SymbolLookup.libraryLookup(libraryPath, libArena);
    } catch (IllegalArgumentException e) {
      libArena.close();
      throw new HegelException("Failed to open libhegel at " + libraryPath + ": " + e.getMessage());
    }

    this.settingsNew = h(linker, lookup, "hegel_settings_new", FunctionDescriptor.of(ADDRESS));
    this.settingsFree =
        h(linker, lookup, "hegel_settings_free", FunctionDescriptor.ofVoid(ADDRESS));
    this.settingsMode =
        h(linker, lookup, "hegel_settings_mode", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    this.settingsTestCases =
        h(
            linker,
            lookup,
            "hegel_settings_test_cases",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
    this.settingsVerbosity =
        h(linker, lookup, "hegel_settings_verbosity", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    this.settingsSeed =
        h(
            linker,
            lookup,
            "hegel_settings_seed",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_BOOLEAN));
    this.settingsDerandomize =
        h(
            linker,
            lookup,
            "hegel_settings_derandomize",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));
    this.settingsReportMultipleFailures =
        h(
            linker,
            lookup,
            "hegel_settings_report_multiple_failures",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));
    this.settingsDatabase =
        h(linker, lookup, "hegel_settings_database", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    this.settingsDatabaseKey =
        h(
            linker,
            lookup,
            "hegel_settings_database_key",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    this.settingsPhases =
        h(linker, lookup, "hegel_settings_phases", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    this.settingsSuppressHealthCheck =
        h(
            linker,
            lookup,
            "hegel_settings_suppress_health_check",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    this.runStart = h(linker, lookup, "hegel_run_start", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.nextTestCase =
        h(linker, lookup, "hegel_next_test_case", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.runResult = h(linker, lookup, "hegel_run_result", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.runFree = h(linker, lookup, "hegel_run_free", FunctionDescriptor.ofVoid(ADDRESS));
    this.generate =
        h(
            linker,
            lookup,
            "hegel_generate",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));
    this.startSpan =
        h(linker, lookup, "hegel_start_span", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    this.stopSpan =
        h(
            linker,
            lookup,
            "hegel_stop_span",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_BOOLEAN));
    this.newCollection =
        h(
            linker,
            lookup,
            "hegel_new_collection",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));
    this.collectionMore =
        h(
            linker,
            lookup,
            "hegel_collection_more",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    this.collectionReject =
        h(
            linker,
            lookup,
            "hegel_collection_reject",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    this.target =
        h(
            linker,
            lookup,
            "hegel_target",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, ADDRESS));
    this.markComplete =
        h(
            linker,
            lookup,
            "hegel_mark_complete",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    this.isFinalReplay =
        h(
            linker,
            lookup,
            "hegel_test_case_is_final_replay",
            FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));
    this.resultPassed =
        h(linker, lookup, "hegel_run_result_passed", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));
    this.resultFailureCount =
        h(
            linker,
            lookup,
            "hegel_run_result_failure_count",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    this.resultFailure =
        h(
            linker,
            lookup,
            "hegel_run_result_failure",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG));
    this.failurePanicMessage =
        h(linker, lookup, "hegel_failure_panic_message", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.failureDiagnostic =
        h(linker, lookup, "hegel_failure_diagnostic", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.failureOrigin =
        h(linker, lookup, "hegel_failure_origin", FunctionDescriptor.of(ADDRESS, ADDRESS));
    this.lastErrorMessage =
        h(linker, lookup, "hegel_last_error_message", FunctionDescriptor.of(ADDRESS));
    this.version = h(linker, lookup, "hegel_version", FunctionDescriptor.of(ADDRESS));
  }

  private static MethodHandle h(
      Linker linker, SymbolLookup lookup, String symbol, FunctionDescriptor desc) {
    return linker.downcallHandle(findSymbol(lookup, symbol), desc);
  }

  static MemorySegment findSymbol(SymbolLookup lookup, String symbol) {
    return lookup
        .find(symbol)
        .orElseThrow(
            () ->
                new HegelException(
                    "libhegel is missing symbol '"
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

  private static MemorySegment cstr(Arena a, String s) {
    return s == null ? MemorySegment.NULL : a.allocateFrom(s);
  }

  static String readCString(MemorySegment ptr) {
    if (ptr == null || ptr.address() == 0) {
      return null;
    }
    return ptr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
  }

  @Override
  public MemorySegment settingsNew() {
    MemorySegment s = (MemorySegment) invoke(settingsNew);
    settingsArenas.put(s.address(), Arena.ofConfined());
    return s;
  }

  @Override
  public void settingsFree(MemorySegment s) {
    Arena arena = settingsArenas.remove(s.address());
    invoke(settingsFree, s);
    arena.close();
  }

  @Override
  public void settingsMode(MemorySegment s, int mode) {
    invoke(settingsMode, s, mode);
  }

  @Override
  public void settingsTestCases(MemorySegment s, long n) {
    invoke(settingsTestCases, s, n);
  }

  @Override
  public void settingsVerbosity(MemorySegment s, int v) {
    invoke(settingsVerbosity, s, v);
  }

  @Override
  public void settingsSeed(MemorySegment s, long seed, boolean hasSeed) {
    invoke(settingsSeed, s, seed, hasSeed);
  }

  @Override
  public void settingsDerandomize(MemorySegment s, boolean derandomize) {
    invoke(settingsDerandomize, s, derandomize);
  }

  @Override
  public void settingsReportMultipleFailures(MemorySegment s, boolean yes) {
    invoke(settingsReportMultipleFailures, s, yes);
  }

  @Override
  public void settingsDatabase(MemorySegment s, String path) {
    invoke(settingsDatabase, s, cstr(settingsArenas.get(s.address()), path));
  }

  @Override
  public void settingsDatabaseKey(MemorySegment s, String key) {
    invoke(settingsDatabaseKey, s, cstr(settingsArenas.get(s.address()), key));
  }

  @Override
  public void settingsPhases(MemorySegment s, int mask) {
    invoke(settingsPhases, s, mask);
  }

  @Override
  public void settingsSuppressHealthCheck(MemorySegment s, int mask) {
    invoke(settingsSuppressHealthCheck, s, mask);
  }

  @Override
  public MemorySegment runStart(MemorySegment settings) {
    return (MemorySegment) invoke(runStart, settings);
  }

  @Override
  public MemorySegment nextTestCase(MemorySegment run) {
    return (MemorySegment) invoke(nextTestCase, run);
  }

  @Override
  public MemorySegment runResult(MemorySegment run) {
    return (MemorySegment) invoke(runResult, run);
  }

  @Override
  public void runFree(MemorySegment run) {
    invoke(runFree, run);
  }

  @Override
  public int generate(MemorySegment tc, byte[] schema, byte[][] out) {
    Arena a = Arena.ofAuto();
    MemorySegment schemaSeg = a.allocate(schema.length);
    MemorySegment.copy(schema, 0, schemaSeg, JAVA_BYTE, 0, schema.length);
    MemorySegment outPtr = a.allocate(ADDRESS);
    MemorySegment outLen = a.allocate(JAVA_LONG);
    int rc = (Integer) invoke(generate, tc, schemaSeg, (long) schema.length, outPtr, outLen);
    if (rc == Abi.OK) {
      MemorySegment valPtr = outPtr.get(ADDRESS, 0);
      long len = outLen.get(JAVA_LONG, 0);
      out[0] = valPtr.reinterpret(len).toArray(JAVA_BYTE);
    }
    return rc;
  }

  @Override
  public int startSpan(MemorySegment tc, long label) {
    return (Integer) invoke(startSpan, tc, label);
  }

  @Override
  public int stopSpan(MemorySegment tc, boolean discard) {
    return (Integer) invoke(stopSpan, tc, discard);
  }

  @Override
  public int newCollection(MemorySegment tc, long minSize, long maxSize, long[] outId) {
    MemorySegment idSeg = Arena.ofAuto().allocate(JAVA_LONG);
    int rc = (Integer) invoke(newCollection, tc, minSize, maxSize, idSeg);
    outId[0] = idSeg.get(JAVA_LONG, 0); // safe on error: callers check rc before use
    return rc;
  }

  @Override
  public int collectionMore(MemorySegment tc, long id, boolean[] outMore) {
    MemorySegment moreSeg = Arena.ofAuto().allocate(JAVA_BOOLEAN);
    int rc = (Integer) invoke(collectionMore, tc, id, moreSeg);
    // Read the (zero-initialised) out slot unconditionally: callers check the return code
    // before using it, and the engine signals exhaustion on the following draw, not here.
    outMore[0] = moreSeg.get(JAVA_BOOLEAN, 0);
    return rc;
  }

  @Override
  public int collectionReject(MemorySegment tc, long id, String why) {
    return (Integer) invoke(collectionReject, tc, id, cstr(Arena.ofAuto(), why));
  }

  @Override
  public int target(MemorySegment tc, double value, String label) {
    return (Integer) invoke(target, tc, value, cstr(Arena.ofAuto(), label));
  }

  @Override
  public int markComplete(MemorySegment tc, int status, String origin) {
    return (Integer) invoke(markComplete, tc, status, cstr(Arena.ofAuto(), origin));
  }

  @Override
  public boolean isFinalReplay(MemorySegment tc) {
    return (Boolean) invoke(isFinalReplay, tc);
  }

  @Override
  public boolean resultPassed(MemorySegment result) {
    return (Boolean) invoke(resultPassed, result);
  }

  @Override
  public long resultFailureCount(MemorySegment result) {
    return (Long) invoke(resultFailureCount, result);
  }

  @Override
  public MemorySegment resultFailure(MemorySegment result, long index) {
    return (MemorySegment) invoke(resultFailure, result, index);
  }

  @Override
  public String failurePanicMessage(MemorySegment failure) {
    return readCString((MemorySegment) invoke(failurePanicMessage, failure));
  }

  @Override
  public String failureDiagnostic(MemorySegment failure) {
    return readCString((MemorySegment) invoke(failureDiagnostic, failure));
  }

  @Override
  public String failureOrigin(MemorySegment failure) {
    return readCString((MemorySegment) invoke(failureOrigin, failure));
  }

  @Override
  public String lastErrorMessage() {
    return readCString((MemorySegment) invoke(lastErrorMessage));
  }

  @Override
  public String version() {
    return readCString((MemorySegment) invoke(version));
  }
}
