package dev.hegel;

/**
 * The source of randomness the engine draws from.
 *
 * <p>Mirrors Hypothesis's {@code backend} setting. The default, {@link #AUTO}, selects {@link
 * #URANDOM} automatically when running inside <a href="https://antithesis.com/">Antithesis</a> and
 * {@link #DEFAULT} otherwise; an explicit choice always wins over the automatic one.
 */
public enum Backend {
    /** Choose automatically: {@code URANDOM} under Antithesis, otherwise {@code DEFAULT}. */
    AUTO(Abi.BACKEND_AUTO),
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

    final int code;

    Backend(int code) {
        this.code = code;
    }
}
