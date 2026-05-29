package dev.hegel;

/**
 * Constants from the libhegel C ABI (hegel-c/include/hegel.h).
 *
 * <p>Kept in sync with the engine header. These are implementation details and never exposed to
 * users.
 */
final class Abi {
  private Abi() {}

  // Return codes (hegel_error_t).
  static final int OK = 0;
  static final int E_STOP_TEST = -1;
  static final int E_ASSUME = -2;
  static final int E_BACKEND = -3;
  static final int E_INVALID_HANDLE = -4;
  static final int E_INVALID_ARG = -5;
  static final int E_ALREADY_COMPLETE = -6;
  static final int E_NOT_COMPLETE = -7;
  static final int E_INTERNAL = -8;

  // Phases (bitmask for hegel_settings_phases).
  static final int PHASE_EXPLICIT = 1 << 0;
  static final int PHASE_REUSE = 1 << 1;
  static final int PHASE_GENERATE = 1 << 2;
  static final int PHASE_TARGET = 1 << 3;
  static final int PHASE_SHRINK = 1 << 4;
  static final int PHASE_ALL = 31;

  // Health-check suppression bitmask (hegel_settings_suppress_health_check).
  static final int HC_FILTER_TOO_MUCH = 1 << 0;
  static final int HC_TOO_SLOW = 1 << 1;
  static final int HC_TEST_CASES_TOO_LARGE = 1 << 2;
  static final int HC_LARGE_INITIAL_TEST_CASE = 1 << 3;

  // Span labels (argument to hegel_start_span).
  static final long LABEL_LIST = 1;
  static final long LABEL_LIST_ELEMENT = 2;
  static final long LABEL_SET = 3;
  static final long LABEL_SET_ELEMENT = 4;
  static final long LABEL_MAP = 5;
  static final long LABEL_MAP_ENTRY = 6;
  static final long LABEL_TUPLE = 7;
  static final long LABEL_ONE_OF = 8;
  static final long LABEL_OPTIONAL = 9;
  static final long LABEL_FIXED_DICT = 10;
  static final long LABEL_FLAT_MAP = 11;
  static final long LABEL_FILTER = 12;
  static final long LABEL_MAPPED = 13;
  static final long LABEL_SAMPLED_FROM = 14;
  static final long LABEL_ENUM_VARIANT = 15;
  static final long LABEL_STATEFUL = 16;
  static final long LABEL_COMPOSITE = 17;

  // hegel_mode_t.
  static final int MODE_TEST_RUN = 0;
  static final int MODE_SINGLE_TEST_CASE = 1;

  // hegel_verbosity_t.
  static final int VERBOSITY_QUIET = 0;
  static final int VERBOSITY_NORMAL = 1;
  static final int VERBOSITY_VERBOSE = 2;
  static final int VERBOSITY_DEBUG = 3;

  // hegel_status_t (argument to hegel_mark_complete).
  static final int STATUS_VALID = 0;
  static final int STATUS_INVALID = 1;
  static final int STATUS_OVERRUN = 2;
  static final int STATUS_INTERESTING = 3;

  // UINT64_MAX sentinel for "unbounded" collection size.
  static final long UNBOUNDED = -1L; // 0xFFFFFFFFFFFFFFFF as a Java long
}
