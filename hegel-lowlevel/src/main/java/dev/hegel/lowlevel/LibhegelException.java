package dev.hegel.lowlevel;

/**
 * Thrown by a binding for anything that is not a test outcome: the library could not be resolved
 * or opened, an infrastructure call returned an unexpected code, an engine invariant was violated.
 *
 * <p>Per-test-case primitives report their expected non-OK codes (stop-test, assumption rejected,
 * invalid argument) as return values, not as this exception; see {@link Libhegel}. The Java
 * frontend's {@code dev.hegel.HegelException} extends this class.
 */
public class LibhegelException extends RuntimeException {
    /**
     * @param message the diagnostic
     */
    public LibhegelException(String message) {
        super(message);
    }

    /**
     * @param message the diagnostic
     * @param cause the underlying failure
     */
    public LibhegelException(String message, Throwable cause) {
        super(message, cause);
    }
}
