package dev.hegel;

/**
 * A phase of a Hegel run. Pass a subset to {@link Settings#phases} to enable only those phases;
 * phases not listed are disabled. The default is all phases.
 *
 * <p>For example, {@code phases(EXPLICIT, REUSE, GENERATE, TARGET)} runs everything except
 * shrinking (useful to see an unshrunk failure quickly), and {@code phases()} runs nothing.
 */
public enum Phase {
  /** Run hard-coded explicit examples (reserved for future use). */
  EXPLICIT(Abi.PHASE_EXPLICIT),
  /** Replay counterexamples persisted from previous runs (requires a database + key). */
  REUSE(Abi.PHASE_REUSE),
  /** Randomly generate fresh test cases up to the test-case budget. */
  GENERATE(Abi.PHASE_GENERATE),
  /** Hill-climb toward observed {@code target} scores between generation rounds. */
  TARGET(Abi.PHASE_TARGET),
  /** Shrink discovered failing examples toward minimal counterexamples. */
  SHRINK(Abi.PHASE_SHRINK);

  final int bit;

  Phase(int bit) {
    this.bit = bit;
  }
}
