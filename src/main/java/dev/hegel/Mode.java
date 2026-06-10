package dev.hegel;

/** Controls the test execution mode. */
public enum Mode {
    /** Run a full test: multiple test cases with shrinking and replay (the default). */
    TEST_RUN(Abi.MODE_TEST_RUN),
    /**
     * Run a single test case with no shrinking, replay, or database — pure data generation without
     * property-testing overhead (an exploratory probe, useful for Antithesis-style workloads).
     */
    SINGLE_TEST_CASE(Abi.MODE_SINGLE_TEST_CASE);

    final int code;

    Mode(int code) {
        this.code = code;
    }
}
