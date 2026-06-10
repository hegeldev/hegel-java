package dev.hegel;

/**
 * Thrown when the engine aborts a run because a {@link HealthCheck} fired: the test itself is
 * misbehaving — rejecting almost every input, or generating data that is too large or too slow —
 * rather than the property being false. If the behaviour is intentional, disable the offending
 * check with {@link Settings#suppressHealthCheck}.
 */
public final class HealthCheckFailure extends HegelException {
    HealthCheckFailure(String message) {
        super(message);
    }
}
