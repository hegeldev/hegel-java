package dev.hegel.lowlevel;

/**
 * Constants from the libhegel C ABI ({@code hegel-c/include/hegel.h}): return codes, run and case
 * statuses, phase and health-check bit masks, and the sentinels the structured primitives use.
 * Kept in sync with the engine header.
 */
public final class Abi {
    private Abi() {}

    // Return codes (hegel_result_t).
    public static final int OK = 0;
    public static final int E_STOP_TEST = -1;
    public static final int E_ASSUME = -2;
    public static final int E_BACKEND = -3;
    public static final int E_INVALID_HANDLE = -4;
    public static final int E_INVALID_ARG = -5;
    public static final int E_ALREADY_COMPLETE = -6;
    public static final int E_NOT_COMPLETE = -7;
    public static final int E_INTERNAL = -8;
    public static final int E_CONCURRENT_USE = -9;
    public static final int E_RETRY = -10;

    // Aggregate run outcome (hegel_run_status_t).
    public static final int RUN_STATUS_PASSED = 0;
    public static final int RUN_STATUS_FAILED = 1;
    public static final int RUN_STATUS_ERROR = 2;
    public static final int RUN_STATUS_FAILED_NONDETERMINISTIC = 3;

    // Phases (bitmask for hegel_settings_set_phases).
    public static final int PHASE_EXPLICIT = 1 << 0;
    public static final int PHASE_REUSE = 1 << 1;
    public static final int PHASE_GENERATE = 1 << 2;
    public static final int PHASE_TARGET = 1 << 3;
    public static final int PHASE_SHRINK = 1 << 4;
    public static final int PHASE_ALL = 31;

    // Health-check suppression bitmask (hegel_settings_set_suppress_health_check).
    public static final int HC_FILTER_TOO_MUCH = 1 << 0;
    public static final int HC_TOO_SLOW = 1 << 1;
    public static final int HC_TEST_CASES_TOO_LARGE = 1 << 2;
    public static final int HC_LARGE_INITIAL_TEST_CASE = 1 << 3;

    // hegel_backend_t. There is no automatic value: leaving the backend unset lets the engine's
    // settings profile choose (the shipped `workload` profile, selected inside Antithesis, uses
    // urandom).
    public static final int BACKEND_DEFAULT = 1;
    public static final int BACKEND_URANDOM = 2;

    // hegel_verbosity_t.
    public static final int VERBOSITY_NORMAL = 0;
    public static final int VERBOSITY_QUIET = 1;
    public static final int VERBOSITY_VERBOSE = 2;
    public static final int VERBOSITY_DEBUG = 3;

    // hegel_status_t (argument to hegel_mark_complete).
    public static final int STATUS_VALID = 0;
    public static final int STATUS_INVALID = 1;
    public static final int STATUS_OVERRUN = 2;
    public static final int STATUS_INTERESTING = 3;

    // Sentinel written by hegel_state_machine_next_group / hegel_state_machine_next_rule (INT64_MIN).
    public static final long STATE_MACHINE_DONE = Long.MIN_VALUE;
    public static final long UNBOUNDED = -1L; // 0xFFFFFFFFFFFFFFFF as a Java long
    public static final long NO_MAX_CODEPOINT = 0xFFFFFFFFL;
}
