package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes generator schemas to CBOR and decodes engine-produced values to native Java values.
 *
 * <p>Schemas are built as {@link CBORObject} maps and encoded directly. Decoded values are
 * normalised to a small set of Java types so generator parse functions can rely on them:
 *
 * <ul>
 *   <li>CBOR integer → {@link BigInteger} (engine integers can exceed {@code long})
 *   <li>floating point (incl. half precision) → {@link Double}
 *   <li>boolean → {@link Boolean}, text → {@link String}, byte string → {@code byte[]}
 *   <li>array → {@link List}, map → {@link LinkedHashMap}, null → {@code null}
 *   <li>CBOR Tag 91 (WTF-8) → {@link String} (decode-only)
 * </ul>
 *
 * @hidden
 */
public final class Cbor {
    private Cbor() {}

    /** WTF-8 string tag the engine may wrap string values in. Decode-only; never sent. */
    private static final int TAG_WTF8 = 91;

    static byte[] encode(CBORObject schema) {
        return schema.EncodeToBytes();
    }

    static Object decode(byte[] bytes) {
        return convert(CBORObject.DecodeFromBytes(bytes));
    }

    static Object convert(CBORObject o) {
        if (o.HasMostOuterTag(TAG_WTF8)) {
            return new String(o.Untag().GetByteString(), StandardCharsets.UTF_8);
        }
        switch (o.getType()) {
            case Integer:
                return o.ToObject(BigInteger.class);
            case FloatingPoint:
                return o.AsDoubleValue();
            case Boolean:
                return o.AsBoolean();
            case ByteString:
                return o.GetByteString();
            case TextString:
                return o.AsString();
            case Array: {
                List<Object> list = new ArrayList<>();
                for (CBORObject e : o.getValues()) {
                    list.add(convert(e));
                }
                return list;
            }
            case Map: {
                Map<Object, Object> map = new LinkedHashMap<>();
                for (Map.Entry<CBORObject, CBORObject> e : o.getEntries()) {
                    map.put(convert(e.getKey()), convert(e.getValue()));
                }
                return map;
            }
            default:
                if (o.isNull()) {
                    return null;
                }
                throw new HegelException("Unexpected CBOR value from engine: " + o.getType());
        }
    }

    // --- typed extraction helpers used by generator parse functions ---

    public static long asLong(Object raw) {
        return ((BigInteger) raw).longValueExact();
    }

    public static int asIndex(Object raw) {
        return ((BigInteger) raw).intValueExact();
    }

    public static double asDouble(Object raw) {
        return ((Number) raw).doubleValue();
    }

    public static float asFloat(Object raw) {
        // The engine emits f32 draws as an f64 already rounded to f32 precision, so this cast is
        // lossless and the same bit pattern round-trips.
        return (float) ((Number) raw).doubleValue();
    }

    public static boolean asBoolean(Object raw) {
        return (Boolean) raw;
    }

    public static String asString(Object raw) {
        return (String) raw;
    }

    public static byte[] asBytes(Object raw) {
        return (byte[]) raw;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object raw) {
        return (List<Object>) raw;
    }
}
