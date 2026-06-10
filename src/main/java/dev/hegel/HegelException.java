package dev.hegel;

/**
 * Thrown for engine, configuration, and binding errors — anything that is not a property failure.
 *
 * <p>A failing property is reported as an {@link AssertionError} carrying the minimal falsifying
 * example, so it integrates with test frameworks; this exception signals that something went wrong
 * with Hegel itself (a malformed generator, a missing engine library, an internal backend error).
 * {@link HealthCheckFailure} is the only subtype, raised when a {@link HealthCheck} aborts the run.
 */
public sealed class HegelException extends RuntimeException permits HealthCheckFailure {
    public HegelException(String message) {
        super(message);
    }

    public HegelException(String message, Throwable cause) {
        super(message, cause);
    }
}
