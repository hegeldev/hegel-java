package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.upokecenter.cbor.CBORObject;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CborTest {
  @Test
  void decodesIntegersAsBigIntegerBothSigns() {
    assertEquals(BigInteger.valueOf(42), Cbor.convert(CBORObject.FromObject(42)));
    assertEquals(BigInteger.valueOf(-7), Cbor.convert(CBORObject.FromObject(-7)));
  }

  @Test
  void decodesFloatsBooleansStringsBytes() {
    assertEquals(1.5, (Double) Cbor.convert(CBORObject.FromObject(1.5)), 0.0);
    assertEquals(true, Cbor.convert(CBORObject.FromObject(true)));
    assertEquals("hi", Cbor.convert(CBORObject.FromObject("hi")));
    assertArrayEquals(
        new byte[] {1, 2}, (byte[]) Cbor.convert(CBORObject.FromObject(new byte[] {1, 2})));
  }

  @Test
  void decodesNull() {
    assertNull(Cbor.convert(CBORObject.Null));
  }

  @Test
  void decodesArraysAndMapsRecursively() {
    Object arr = Cbor.convert(CBORObject.NewArray().Add(1).Add("x"));
    assertEquals(List.of(BigInteger.ONE, "x"), arr);

    Object map = Cbor.convert(CBORObject.NewMap().Add("k", 2));
    assertEquals(Map.of("k", BigInteger.TWO), map);
  }

  @Test
  void decodesTag91AsString() {
    CBORObject tagged =
        CBORObject.FromObjectAndTag(
            CBORObject.FromObject("wtf8".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 91);
    assertEquals("wtf8", Cbor.convert(tagged));
  }

  @Test
  void rejectsUnexpectedSimpleValue() {
    // 0xF7 = CBOR "undefined" — neither null nor a value we expect from the engine.
    CBORObject undefined = CBORObject.DecodeFromBytes(new byte[] {(byte) 0xF7});
    assertThrows(HegelException.class, () -> Cbor.convert(undefined));
  }

  @Test
  void encodeAndDecodeRoundTrip() {
    byte[] bytes = Cbor.encode(CBORObject.NewArray().Add(1).Add(2));
    assertEquals(List.of(BigInteger.ONE, BigInteger.TWO), Cbor.decode(bytes));
  }

  @Test
  void typedHelpers() {
    assertEquals(5L, Cbor.asLong(BigInteger.valueOf(5)));
    assertEquals(3, Cbor.asIndex(BigInteger.valueOf(3)));
    assertEquals(BigInteger.TEN, Cbor.asBigInteger(BigInteger.TEN));
    assertEquals(2.0, Cbor.asDouble(BigInteger.valueOf(2)), 0.0);
    assertEquals(2.5, Cbor.asDouble(2.5), 0.0);
    assertTrue(Cbor.asBoolean(Boolean.TRUE));
    assertEquals("s", Cbor.asString("s"));
    assertArrayEquals(new byte[] {9}, Cbor.asBytes(new byte[] {9}));
    assertEquals(List.of(1), Cbor.asList(List.of(1)));
  }
}
