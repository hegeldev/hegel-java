package dev.hegel;

/**
 * Health checks the engine runs to catch tests that are misbehaving rather than buggy. Suppress one
 * via {@link Settings#suppressHealthCheck} when its behaviour is intentional.
 */
public enum HealthCheck {
  /** Too many draws rejected via {@code assume}/{@code filter}. */
  FILTER_TOO_MUCH(Abi.HC_FILTER_TOO_MUCH),
  /** Individual test cases take so long the run is impractical. */
  TOO_SLOW(Abi.HC_TOO_SLOW),
  /** Generated values are too large to retain for shrinking. */
  TEST_CASES_TOO_LARGE(Abi.HC_TEST_CASES_TOO_LARGE),
  /** The first generated test case is already disproportionately large. */
  LARGE_INITIAL_TEST_CASE(Abi.HC_LARGE_INITIAL_TEST_CASE);

  final int bit;

  HealthCheck(int bit) {
    this.bit = bit;
  }
}
