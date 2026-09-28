package dev.hegel;

import dev.hegel.lowlevel.Abi;

/**
 * How a run reacts when it detects a nondeterministic test: one whose structure or outcome changes
 * when the same generated choices are replayed, because it depends on something other than its
 * generated data (hidden global state, time, an outside service, thread scheduling).
 *
 * <p>Under {@link #QUIET} and {@link #WARN} the engine switches to nondeterministic handling: a
 * failure is confirmed by repeated replay before it is shrunk or persisted, and reported with a
 * {@linkplain Failure#caveat() caveat} quoting the run's replay evidence. An unconfirmed failure
 * still fails the run, with a caveat instead of a reproduce blob. {@link #ERROR} aborts the run
 * with a flaky-test error instead, for suites that use determinism as a lint.
 */
public enum NondeterminismStrictness {
    /**
     * Leave the choice to the engine's settings profile and the {@code HEGEL_NONDETERMINISM_STRICTNESS}
     * environment variable ({@code quiet} unless configured otherwise).
     */
    DEFAULT(null),
    /** Switch to nondeterministic handling silently. The engine's default. */
    QUIET(Abi.NONDETERMINISM_QUIET),
    /** Switch to nondeterministic handling, printing a one-line notice once per run. */
    WARN(Abi.NONDETERMINISM_WARN),
    /** Abort the run with a flaky-test error ({@link HegelException}). */
    ERROR(Abi.NONDETERMINISM_ERROR);

    /** The {@code hegel_nondeterminism_strictness_t} value to send, or {@code null} to leave the profile's choice. */
    final Integer code;

    NondeterminismStrictness(Integer code) {
        this.code = code;
    }

    /** The strictness the engine reports back for {@code code}. */
    static NondeterminismStrictness fromCode(int code) {
        for (NondeterminismStrictness s : values()) {
            if (s.code != null && s.code == code) {
                return s;
            }
        }
        throw new HegelException("unknown hegel_nondeterminism_strictness_t value " + code);
    }
}
