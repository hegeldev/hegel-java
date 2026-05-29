package dev.hegel;

import java.nio.file.Path;

/**
 * Process-wide lazy singleton for the resolved libhegel binding.
 *
 * <p>The library is located and opened exactly once on first use and shared across all threads (the
 * resolved function pointers are immutable). Tests may install a fake binding via {@link
 * #setForTesting} and restore the real one with {@link #reset}.
 */
final class Engine {
  private Engine() {}

  private static Libhegel instance;

  static synchronized Libhegel get() {
    if (instance == null) {
      Path path = LibraryLoader.fromEnvironment().resolve();
      instance = new RealLibhegel(path);
    }
    return instance;
  }

  /** Install a binding (real or fake) for the rest of the process or until {@link #reset}. */
  static synchronized void setForTesting(Libhegel binding) {
    instance = binding;
  }

  /** Forget the current binding so the next {@link #get()} re-resolves it. */
  static synchronized void reset() {
    instance = null;
  }
}
