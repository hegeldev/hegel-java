package dev.hegel.generators;

import dev.hegel.Abi;

/** Validation helper for collection size bounds. */
final class Sizes {
    private Sizes() {}

    /**
     * Validate a user-supplied maximum size from a public fluent setter. Rejects every negative
     * value so the engine's internal "unbounded" sentinel ({@code -1}) can never be smuggled in
     * through the public API.
     */
    static long checkedMax(long maxSize, String what) {
        if (maxSize < 0) {
            throw new IllegalArgumentException(
                    what + ": maxSize must be >= 0, got " + maxSize + "; omit the maxSize call for no upper bound");
        }
        return maxSize;
    }

    static void validate(long minSize, long maxSize, String what) {
        if (minSize < 0) {
            throw new IllegalArgumentException(what + ": minSize must be >= 0, got " + minSize);
        }
        if (maxSize != Abi.UNBOUNDED && maxSize < minSize) {
            throw new IllegalArgumentException(
                    what + ": maxSize (" + maxSize + ") must be >= minSize (" + minSize + ")");
        }
    }
}
