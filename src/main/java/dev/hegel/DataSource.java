package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * The per-test-case primitive surface that generators draw against.
 *
 * <p>Generators depend on this interface rather than {@link Libhegel} directly, so they can be
 * tested against a fake data source. Every method translates engine return codes: {@code STOP_TEST}
 * becomes {@link StopTest}, an assumption rejection becomes {@link AssumeRejected}, and any other
 * non-OK code becomes a {@link HegelException} carrying the engine's diagnostic.
 */
interface DataSource {
  /** Draw one value described by {@code schema}, returning the decoded native value. */
  Object generate(CBORObject schema);

  void startSpan(long label);

  void stopSpan(boolean discard);

  long newCollection(long minSize, long maxSize);

  boolean collectionMore(long id);

  void collectionReject(long id, String why);

  void target(double value, String label);
}
