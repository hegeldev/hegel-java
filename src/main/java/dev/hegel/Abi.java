package dev.hegel;

/**
 * Constants from the libhegel C ABI (hegel-c/include/hegel.h).
 *
 * <p>Kept in sync with the engine header. These are implementation details: {@code public} only so
 * the generators in {@code dev.hegel.generators} can reach them, not part of the user-facing API.
 *
 * @hidden
 */
public final class Abi {
    private Abi() {}

    // Return codes (hegel_error_t).
    public static final int OK = 0;
    public static final int E_STOP_TEST = -1;
    public static final int E_ASSUME = -2;
    public static final int E_BACKEND = -3;
    public static final int E_INVALID_HANDLE = -4;
    public static final int E_INVALID_ARG = -5;
    public static final int E_ALREADY_COMPLETE = -6;
    public static final int E_NOT_COMPLETE = -7;
    public static final int E_INTERNAL = -8;

    // Phases (bitmask for hegel_settings_phases).
    public static final int PHASE_EXPLICIT = 1 << 0;
    public static final int PHASE_REUSE = 1 << 1;
    public static final int PHASE_GENERATE = 1 << 2;
    public static final int PHASE_TARGET = 1 << 3;
    public static final int PHASE_SHRINK = 1 << 4;
    public static final int PHASE_ALL = 31;

    // Health-check suppression bitmask (hegel_settings_suppress_health_check).
    public static final int HC_FILTER_TOO_MUCH = 1 << 0;
    public static final int HC_TOO_SLOW = 1 << 1;
    public static final int HC_TEST_CASES_TOO_LARGE = 1 << 2;
    public static final int HC_LARGE_INITIAL_TEST_CASE = 1 << 3;

    // Span labels (argument to hegel_start_span).
    public static final long LABEL_LIST = 1;
    public static final long LABEL_LIST_ELEMENT = 2;
    public static final long LABEL_SET = 3;
    public static final long LABEL_SET_ELEMENT = 4;
    public static final long LABEL_MAP = 5;
    public static final long LABEL_MAP_ENTRY = 6;
    public static final long LABEL_TUPLE = 7;
    public static final long LABEL_ONE_OF = 8;
    public static final long LABEL_OPTIONAL = 9;
    public static final long LABEL_FIXED_DICT = 10;
    public static final long LABEL_FLAT_MAP = 11;
    public static final long LABEL_FILTER = 12;
    public static final long LABEL_MAPPED = 13;
    public static final long LABEL_SAMPLED_FROM = 14;
    public static final long LABEL_ENUM_VARIANT = 15;
    public static final long LABEL_STATEFUL = 16;
    public static final long LABEL_COMPOSITE = 17;

    // hegel_mode_t.
    public static final int MODE_TEST_RUN = 0;
    public static final int MODE_SINGLE_TEST_CASE = 1;

    // hegel_verbosity_t.
    public static final int VERBOSITY_QUIET = 0;
    public static final int VERBOSITY_NORMAL = 1;
    public static final int VERBOSITY_VERBOSE = 2;
    public static final int VERBOSITY_DEBUG = 3;

    // hegel_status_t (argument to hegel_mark_complete).
    public static final int STATUS_VALID = 0;
    public static final int STATUS_INVALID = 1;
    public static final int STATUS_OVERRUN = 2;
    public static final int STATUS_INTERESTING = 3;

    // UINT64_MAX sentinel for "unbounded" collection size.
    public static final long UNBOUNDED = -1L; // 0xFFFFFFFFFFFFFFFF as a Java long
}
