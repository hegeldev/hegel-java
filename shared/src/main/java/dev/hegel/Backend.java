package dev.hegel;

import dev.hegel.lowlevel.Abi;

/**
 * The source of randomness the engine draws from.
 *
 * <p>Mirrors Hypothesis's {@code backend} setting. The default, {@link #AUTO}, leaves the choice to
 * the engine's settings profile: the shipped {@code workload} profile, which the engine selects when
 * running inside <a href="https://antithesis.com/">Antithesis</a>, uses {@link #URANDOM}, and every
 * other profile uses {@link #DEFAULT}. An explicit choice always wins over the profile's.
 */
public enum Backend {
    /** Leave the choice to the engine's profile: {@code URANDOM} under Antithesis, else {@code DEFAULT}. */
    AUTO(null),
    /**
     * Expand a single seeded PRNG. Runs are reproducible from the seed and shrinking and replay
     * work as usual.
     */
    DEFAULT(Abi.BACKEND_DEFAULT),
    /**
     * Read fresh entropy from {@code /dev/urandom} on every draw. Intended for running under
     * Antithesis, whose fuzzer controls {@code /dev/urandom}, handing it control over the entire
     * test case; you almost certainly don't want it otherwise.
     */
    URANDOM(Abi.BACKEND_URANDOM);

    /** The {@code hegel_backend_t} value to send, or {@code null} to leave the profile's choice. */
    final Integer code;

    Backend(Integer code) {
        this.code = code;
    }
}
