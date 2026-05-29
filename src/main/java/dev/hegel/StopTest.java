package dev.hegel;

/**
 * Control-flow signal: the engine has abandoned the current test case (exhausted its choice
 * budget). Unwinds the test body; the runner marks the case OVERRUN. Never a property failure.
 */
final class StopTest extends RuntimeException {
  StopTest() {
    super(null, null, false, false);
  }
}
