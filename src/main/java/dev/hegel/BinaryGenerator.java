package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * Generates {@code byte[]} values with length in an inclusive {@code [minSize, maxSize]} range.
 * Always basic (one engine call).
 *
 * <p>The default is any length; narrow it with the fluent {@link #minSize(int)} / {@link
 * #maxSize(int)} methods. {@code binary(a, b)} is exactly {@code binary().minSize(a).maxSize(b)}.
 */
public final class BinaryGenerator implements Generator<byte[]>, MaybeBasic<byte[]> {
  private final long minSize;
  private final long maxSize;

  BinaryGenerator(long minSize, long maxSize) {
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

  @Override
  public BasicGenerator<byte[]> asBasic() {
    CBORObject schema = CBORObject.NewMap().Add("type", "binary").Add("min_size", minSize);
    if (maxSize != Abi.UNBOUNDED) {
      schema.Add("max_size", maxSize);
    }
    return new BasicGenerator<>(schema, Cbor::asBytes);
  }

  @Override
  public byte[] generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}
