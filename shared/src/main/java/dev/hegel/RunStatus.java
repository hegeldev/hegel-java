package dev.hegel;

/** The aggregate verdict of a run, as carried by {@link RunReport#status()}. */
public enum RunStatus {
    /** The property held for every generated input. */
    PASSED,
    /** The property failed; see {@link RunReport#failures()}. */
    FAILED,
    /**
     * The run produced no verdict on the property: a failed health check, or an engine error. See
     * {@link RunReport#error()}.
     */
    ERROR
}
