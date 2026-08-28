package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates {@code byte[]} values with length in an inclusive {@code [minSize, maxSize]} range.
 *
 * <p>Lengths default to {@code [0, 100]} (or {@code [minSize, minSize + 100]} for a larger
 * minimum); set an explicit {@link #maxSize(int)} for longer arrays.
 */
public final class BinaryGenerator implements Generator<byte[]> {
    private final long minSize;
    private final long maxSize;

    public BinaryGenerator(long minSize, long maxSize) {
        Sizes.validate(minSize, maxSize, "binary");
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * @param minSize the minimum length (inclusive)
     * @return a copy with the minimum size set
     */
    public BinaryGenerator minSize(int minSize) {
        return new BinaryGenerator(minSize, maxSize);
    }

    /**
     * @param maxSize the maximum length (inclusive)
     * @return a copy with the maximum size set
     */
    public BinaryGenerator maxSize(int maxSize) {
        return new BinaryGenerator(minSize, maxSize);
    }

    /** @hidden */
    @Override
    public byte[] doDraw(TestCase tc) {
        return tc.generateBytes(minSize, Sizes.resolveMax(minSize, maxSize, TextGenerator.DEFAULT_MAX_SIZE));
    }
}
