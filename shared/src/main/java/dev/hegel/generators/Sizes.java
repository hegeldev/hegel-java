package dev.hegel.generators;

import dev.hegel.Abi;

/** Validation and defaulting helpers for size bounds. */
final class Sizes {
    private Sizes() {}

    static void validate(long minSize, long maxSize, String what) {
        if (minSize < 0) {
            throw new IllegalArgumentException(what + ": minSize must be >= 0, got " + minSize);
        }
        if (maxSize != Abi.UNBOUNDED && maxSize < minSize) {
            throw new IllegalArgumentException(
                    what + ": maxSize (" + maxSize + ") must be >= minSize (" + minSize + ")");
        }
    }

    /**
     * The effective maximum for a length-bounded draw: the explicit {@code maxSize} when one was
     * set, otherwise {@code defaultMax} (shifted up when {@code minSize} exceeds it).
     */
    static long resolveMax(long minSize, long maxSize, long defaultMax) {
        if (maxSize != Abi.UNBOUNDED) {
            return maxSize;
        }
        return minSize > defaultMax ? minSize + defaultMax : defaultMax;
    }
}
