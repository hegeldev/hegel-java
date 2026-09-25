package dev.hegel;

import java.nio.charset.StandardCharsets;

/**
 * Span labels for {@link TestCase#span(long, java.util.function.Supplier)} and {@link
 * TestCase#startSpan(long)}.
 *
 * <p>A label identifies the generator that opened a span, and has no meaning beyond identity: the
 * engine treats two spans with the same label as coming from the same generator — candidates for
 * swapping, duplicating and reordering with each other when it shrinks and mutates test cases — and
 * does nothing else with it. Any 64-bit value works as long as the same generator always uses the
 * same one. Derive a label from a name with {@link #of(String)}, and give a generator built from
 * other generators a label {@link #combine(long...) combined} from its own and its components', so
 * that a list of integers and a list of strings get different labels while every list of integers
 * gets the same one. Both match the engine's {@code hegel_label_from_name} and {@code
 * hegel_label_combine}, so labels minted here agree with those of every other Hegel frontend.
 *
 * <p>The constants below are the labels Hegel's own composite generators open their spans with,
 * derived from names of the form {@code dev.hegel.<kind>}. The engine labels the spans around its
 * own draws from {@code hegel.<kind>} names; prefix custom names with your library's to keep clear
 * of both.
 */
public final class Label {
    private Label() {}

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** A variable-length list. */
    public static final long LIST = of("dev.hegel.list");

    /** One element of a list. */
    public static final long LIST_ELEMENT = of("dev.hegel.list_element");

    /** A set. */
    public static final long SET = of("dev.hegel.set");

    /** One element of a set. */
    public static final long SET_ELEMENT = of("dev.hegel.set_element");

    /** A map. */
    public static final long MAP = of("dev.hegel.map");

    /** One key/value entry of a map. */
    public static final long MAP_ENTRY = of("dev.hegel.map_entry");

    /** A fixed-arity tuple. */
    public static final long TUPLE = of("dev.hegel.tuple");

    /** A choice between alternative generators. */
    public static final long ONE_OF = of("dev.hegel.one_of");

    /** An optional value. */
    public static final long OPTIONAL = of("dev.hegel.optional");

    /** A record-like structure with a fixed set of named fields. */
    public static final long FIXED_DICT = of("dev.hegel.fixed_dict");

    /** A draw whose generator depends on an earlier draw. */
    public static final long FLAT_MAP = of("dev.hegel.flat_map");

    /** A draw filtered by a predicate. */
    public static final long FILTER = of("dev.hegel.filter");

    /** A draw transformed by a function. */
    public static final long MAPPED = of("dev.hegel.mapped");

    /** A value sampled from a fixed collection. */
    public static final long SAMPLED_FROM = of("dev.hegel.sampled_from");

    /** One variant of an enum-like type. */
    public static final long ENUM_VARIANT = of("dev.hegel.enum_variant");

    /** One rule invocation of a stateful test. */
    public static final long STATEFUL_RULE = of("dev.hegel.stateful_rule");

    /** A value built imperatively from several draws ({@link Generators#composite}). */
    public static final long COMPOSITE = of("dev.hegel.composite");

    /**
     * Mint a stable label from a name: the 64-bit FNV-1a hash of its UTF-8 bytes. The same name
     * always yields the same label, so a frontend can label its generators by fully-qualified name.
     *
     * @param name the label's name
     * @return the label
     */
    public static long of(String name) {
        return fnv1a(FNV_OFFSET_BASIS, name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The label for a generator built from other generators: a hash of the given labels, in order.
     * Pass the generator's own label (from {@link #of(String)}) first and its components' labels
     * after it. Combining is order-sensitive, and combining a single label does not return it
     * unchanged.
     *
     * @param labels the generator's own label followed by its components'
     * @return the combined label
     */
    public static long combine(long... labels) {
        long hash = FNV_OFFSET_BASIS;
        byte[] bytes = new byte[8];
        for (long label : labels) {
            for (int i = 0; i < 8; i++) {
                bytes[i] = (byte) (label >>> (8 * i));
            }
            hash = fnv1a(hash, bytes);
        }
        return hash;
    }

    private static long fnv1a(long hash, byte[] bytes) {
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
