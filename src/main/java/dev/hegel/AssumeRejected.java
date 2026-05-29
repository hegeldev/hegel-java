package dev.hegel;

/**
 * Control-flow signal: a precondition ({@code assume}) or a {@code filter} predicate rejected the
 * current test case. Unwinds the test body; the runner marks the case INVALID and the engine
 * discards it without counting it against the test-case budget. Never a property failure.
 */
final class AssumeRejected extends RuntimeException {
  AssumeRejected() {
    super(null, null, false, false);
  }
}
