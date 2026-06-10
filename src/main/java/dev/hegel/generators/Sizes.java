package dev.hegel.generators;

import dev.hegel.Abi;

/** Validation helper for collection size bounds. */
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
}
