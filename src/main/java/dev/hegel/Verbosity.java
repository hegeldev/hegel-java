package dev.hegel;

/** Engine output verbosity. */
public enum Verbosity {
    /** Nothing besides the final result. */
    QUIET(Abi.VERBOSITY_QUIET),
    /** A short summary per run (the default). */
    NORMAL(Abi.VERBOSITY_NORMAL),
    /** Per-test-case progress and final replay draws. */
    VERBOSE(Abi.VERBOSITY_VERBOSE),
    /** As verbose, plus shrinker trace output. */
    DEBUG(Abi.VERBOSITY_DEBUG);

    final int code;

    Verbosity(int code) {
        this.code = code;
    }
}
