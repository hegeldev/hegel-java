package dev.hegel;

import java.lang.foreign.MemorySegment;

/**
 * The libhegel binding surface, as a table of operations.
 *
 * <p>Modelled as an interface so tests can substitute a fake binding that returns chosen return
 * codes, exercising every error path without the real engine. The production implementation is
 * {@link RealLibhegel}, which drives libhegel over the Foreign Function and Memory API.
 *
 * <p>Opaque handles ({@code hegel_settings_t*}, {@code hegel_run_t*}, etc.) are passed as {@link
 * MemorySegment}; callers treat them as opaque and never dereference them.
 *
 * <p>Functions returning {@code int} return the raw libhegel return code; the caller translates it
 * (see {@link Translate}) and reads {@link #lastErrorMessage()} immediately on a non-OK code.
 * Strings the engine returns are copied out before this method returns, so they remain valid.
 */
interface Libhegel {
  // Settings.
  MemorySegment settingsNew();

  void settingsFree(MemorySegment s);

  void settingsMode(MemorySegment s, int mode);

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
  MemorySegment runStart(MemorySegment settings);

  MemorySegment nextTestCase(MemorySegment run);

  MemorySegment runResult(MemorySegment run);

  void runFree(MemorySegment run);

  // Per-test-case primitives. Each returns the raw rc.

  /**
   * Draw a value. On {@link Abi#OK} the decoded value bytes are copied into {@code out[0]}. On any
   * non-OK code {@code out[0]} is left untouched.
   */
  int generate(MemorySegment tc, byte[] schema, byte[][] out);

  int startSpan(MemorySegment tc, long label);

  int stopSpan(MemorySegment tc, boolean discard);

  int newCollection(MemorySegment tc, long minSize, long maxSize, long[] outId);

  int collectionMore(MemorySegment tc, long id, boolean[] outMore);

  int collectionReject(MemorySegment tc, long id, String why);

  int target(MemorySegment tc, double value, String label);

  int markComplete(MemorySegment tc, int status, String origin);

  boolean isFinalReplay(MemorySegment tc);

  // Results.
  boolean resultPassed(MemorySegment result);

  long resultFailureCount(MemorySegment result);

  MemorySegment resultFailure(MemorySegment result, long index);

  String failurePanicMessage(MemorySegment failure);

  String failureDiagnostic(MemorySegment failure);

  String failureOrigin(MemorySegment failure);

  // Diagnostics.
  String lastErrorMessage();

  String version();
}
