package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.lang.foreign.MemorySegment;

/**
 * A {@link DataSource} backed by the real engine for one in-flight test case.
 *
 * <p>Once the engine returns {@code STOP_TEST} (or an assumption is rejected) the source is marked
 * {@code aborted}: value-producing primitives short-circuit by re-raising {@link StopTest} without
 * touching libhegel, and {@link #stopSpan} becomes a no-op so span-closing {@code finally} blocks
 * during unwinding do not call into a case that is already being torn down.
 */
final class LiveDataSource implements DataSource {
  private final Libhegel lib;
  private final MemorySegment tc;
  private boolean aborted;

  LiveDataSource(Libhegel lib, MemorySegment tc) {
    this.lib = lib;
    this.tc = tc;
  }

  boolean isAborted() {
    return aborted;
  }

  private void translate(int rc, String op) {
    switch (rc) {
      case Abi.OK:
        return;
      case Abi.E_STOP_TEST:
        aborted = true;
        throw new StopTest();
      case Abi.E_ASSUME:
        aborted = true;
        throw new AssumeRejected();
      default:
        String msg = lib.lastErrorMessage();
        throw new HegelException(
            "hegel_" + op + " failed (rc=" + rc + "): " + (msg == null ? "" : msg));
    }
  }

  @Override
  public Object generate(CBORObject schema) {
    if (aborted) {
      throw new StopTest();
    }
    byte[][] out = new byte[1][];
    translate(lib.generate(tc, Cbor.encode(schema), out), "generate");
    return Cbor.decode(out[0]);
  }

  @Override
  public void startSpan(long label) {
    if (aborted) {
      throw new StopTest();
    }
    translate(lib.startSpan(tc, label), "start_span");
  }

  @Override
  public void stopSpan(boolean discard) {
    if (aborted) {
      return;
    }
    translate(lib.stopSpan(tc, discard), "stop_span");
  }

  @Override
  public long newCollection(long minSize, long maxSize) {
    if (aborted) {
      throw new StopTest();
    }
    long[] id = new long[1];
    translate(lib.newCollection(tc, minSize, maxSize, id), "new_collection");
    return id[0];
  }

  @Override
  public boolean collectionMore(long id) {
    if (aborted) {
      throw new StopTest();
    }
    boolean[] more = new boolean[1];
    translate(lib.collectionMore(tc, id, more), "collection_more");
    return more[0];
  }

  @Override
  public void collectionReject(long id, String why) {
    if (aborted) {
      throw new StopTest();
    }
    translate(lib.collectionReject(tc, id, why), "collection_reject");
  }

  @Override
  public void target(double value, String label) {
    if (aborted) {
      throw new StopTest();
    }
    translate(lib.target(tc, value, label), "target");
  }
}
