package dev.hegel;

import dev.hegel.lowlevel.Abi;

/**
 * Span labels for {@link TestCase#span(long, java.util.function.Supplier)} and {@link
 * TestCase#startSpan(long)}.
 *
 * <p>A label tells the engine what kind of structure a span encloses, so shrinking can apply the
 * right strategies to it. The constants here are the labels the engine reserves for its own
 * structural notions; frontends building their own composite generators mint stable labels of their
 * own with {@link #of(String)}. The label space is a full 64 bits and any value outside the reserved
 * ones is fine, but a label must be the same on every run for the engine to recognise the
 * structure.
 */
public final class Label {
    private Label() {}

    /** A variable-length list. */
    public static final long LIST = Abi.LABEL_LIST;

    /** One element of a list. */
    public static final long LIST_ELEMENT = Abi.LABEL_LIST_ELEMENT;

    /** A set. */
    public static final long SET = Abi.LABEL_SET;

    /** One element of a set. */
    public static final long SET_ELEMENT = Abi.LABEL_SET_ELEMENT;

    /** A map. */
    public static final long MAP = Abi.LABEL_MAP;

    /** One key/value entry of a map. */
    public static final long MAP_ENTRY = Abi.LABEL_MAP_ENTRY;

    /** A fixed-arity tuple. */
    public static final long TUPLE = Abi.LABEL_TUPLE;

    /** A choice between alternative generators. */
    public static final long ONE_OF = Abi.LABEL_ONE_OF;

    /** An optional value. */
    public static final long OPTIONAL = Abi.LABEL_OPTIONAL;

    /** A record-like structure with a fixed set of named fields. */
    public static final long FIXED_DICT = Abi.LABEL_FIXED_DICT;

    /** A draw whose generator depends on an earlier draw. */
    public static final long FLAT_MAP = Abi.LABEL_FLAT_MAP;

    /** A draw filtered by a predicate. */
    public static final long FILTER = Abi.LABEL_FILTER;

    /** A draw transformed by a function. */
    public static final long MAPPED = Abi.LABEL_MAPPED;

    /** A value sampled from a fixed collection. */
    public static final long SAMPLED_FROM = Abi.LABEL_SAMPLED_FROM;

    /** One variant of an enum-like type. */
    public static final long ENUM_VARIANT = Abi.LABEL_ENUM_VARIANT;

    /** One rule invocation of a stateful test. */
    public static final long STATEFUL_RULE = Abi.LABEL_STATEFUL_RULE;

    /** A value built imperatively from several draws ({@link Generators#composite}). */
    public static final long COMPOSITE = of("dev.hegel.composite");

    /**
     * Mint a stable label from a name, hashing it with 64-bit FNV-1a. The same name always yields
     * the same label, so a frontend can label its generators by fully-qualified name.
     *
     * @param name the label's name
     * @return the label
     */
    public static long of(String name) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
