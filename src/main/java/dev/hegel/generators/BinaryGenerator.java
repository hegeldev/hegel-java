package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Abi;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates {@code byte[]} values with length in an inclusive {@code [minSize, maxSize]} range.
 * Always basic (one engine call).
 *
 * <p>The default is any length; narrow it with the fluent {@link #minSize(int)} / {@link
 * #maxSize(int)} methods.
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
     * @param maxSize the maximum length (inclusive, {@code >= 0}; omit the call for no bound)
     * @return a copy with the maximum size set
     */
    public BinaryGenerator maxSize(int maxSize) {
        return new BinaryGenerator(minSize, Sizes.checkedMax(maxSize, "binary"));
    }

    /** @hidden */
    @Override
    public BasicGenerator<byte[]> asBasic() {
        CBORObject schema = CBORObject.NewMap().Add("type", "binary").Add("min_size", minSize);
        if (maxSize != Abi.UNBOUNDED) {
            schema.Add("max_size", maxSize);
        }
        return new BasicGenerator<>(schema, Cbor::asBytes);
    }
}
