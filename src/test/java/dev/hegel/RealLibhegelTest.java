package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers {@link RealLibhegel} edge branches that the normal engine path does not reach. */
class RealLibhegelTest {

  static void throwsError() {
    throw new AssertionError("boom"); // an Error (Throwable, not RuntimeException)
  }

  static void throwsRuntime() {
    throw new IllegalStateException("rt");
  }

  @Test
  void invokeWrapsNonRuntimeThrowable() throws Exception {
    MethodHandle h =
        MethodHandles.lookup()
            .findStatic(RealLibhegelTest.class, "throwsError", MethodType.methodType(void.class));
    assertThrows(HegelException.class, () -> RealLibhegel.invoke(h));
  }

  @Test
  void invokeRethrowsRuntimeException() throws Exception {
    MethodHandle h =
        MethodHandles.lookup()
            .findStatic(RealLibhegelTest.class, "throwsRuntime", MethodType.methodType(void.class));
    assertThrows(IllegalStateException.class, () -> RealLibhegel.invoke(h));
  }

  @Test
  void readCStringHandlesNullAndValue() {
    assertNull(RealLibhegel.readCString(null));
    assertNull(RealLibhegel.readCString(MemorySegment.NULL));
    try (Arena a = Arena.ofConfined()) {
      assertEquals("hello", RealLibhegel.readCString(a.allocateFrom("hello")));
    }
  }

  @Test
  void findSymbolReturnsPresentAndThrowsOnMissing() {
    Path lib = LibraryLoader.fromEnvironment().resolve();
    try (Arena a = Arena.ofShared()) {
      SymbolLookup lookup = SymbolLookup.libraryLookup(lib, a);
      assertNotNull(RealLibhegel.findSymbol(lookup, "hegel_version"));
      assertThrows(
          HegelException.class, () -> RealLibhegel.findSymbol(lookup, "no_such_symbol_xyz"));
    }
  }

  @Test
  void constructorRejectsBadPath() {
    assertThrows(HegelException.class, () -> new RealLibhegel(Path.of("/nonexistent/libhegel.so")));
  }
}
