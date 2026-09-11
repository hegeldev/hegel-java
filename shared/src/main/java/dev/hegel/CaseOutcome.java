package dev.hegel;

/** How a single test case concluded, as reported to the engine. */
public enum CaseOutcome {
    /** The body returned normally: the property held for this input. */
    VALID(Abi.STATUS_VALID),
    /** The body rejected the input with {@link TestCase#assume(boolean)}. */
    INVALID(Abi.STATUS_INVALID),
    /** The body drew more data than the engine allowed for this case. */
    OVERRUN(Abi.STATUS_OVERRUN),
    /** The body threw: the property failed for this input. */
    INTERESTING(Abi.STATUS_INTERESTING);

    /** The {@code hegel_status_t} value passed to {@code hegel_mark_complete}. */
    final int status;

    CaseOutcome(int status) {
        this.status = status;
    }
}
