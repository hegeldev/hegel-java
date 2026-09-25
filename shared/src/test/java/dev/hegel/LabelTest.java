package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link Label#of} and {@link Label#combine} must agree with libhegel's {@code
 * hegel_label_from_name} / {@code hegel_label_combine}: the 64-bit FNV-1a hash of the name's UTF-8
 * bytes, and FNV-1a over the little-endian bytes of each label in turn. The expected values were
 * computed independently from that definition.
 */
class LabelTest {
    private static final long LIST = 0x588C222984E69C23L;
    private static final long INTEGER = -0x57664BBC5FB7D3A5L;

    @Test
    void ofIsTheFnv1aHashOfTheName() {
        assertEquals(LIST, Label.of("dev.hegel.list"));
        assertEquals(INTEGER, Label.of("dev.hegel.integer"));
        assertEquals(LIST, Label.LIST);
    }

    @Test
    void combineHashesTheLabelsInOrder() {
        assertEquals(-0x6BA08FBF26A1271AL, Label.combine(LIST, INTEGER));
        assertEquals(-0x717EA0EE91B61276L, Label.combine(INTEGER, LIST));
        assertEquals(0x47D7283BA12CCBC7L, Label.combine(LIST));
        assertEquals(-0x340D631B7BDDDCDBL, Label.combine());
        // A single label does not combine to itself, so lists(x) never collides with x.
        assertNotEquals(LIST, Label.combine(LIST));
    }

    @Test
    void frontendConstantsAreDistinct() {
        long[] all = {
            Label.LIST, Label.LIST_ELEMENT, Label.SET, Label.SET_ELEMENT, Label.MAP, Label.MAP_ENTRY,
            Label.TUPLE, Label.ONE_OF, Label.OPTIONAL, Label.FIXED_DICT, Label.FLAT_MAP, Label.FILTER,
            Label.MAPPED, Label.SAMPLED_FROM, Label.ENUM_VARIANT, Label.STATEFUL_RULE, Label.COMPOSITE,
        };
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                assertNotEquals(all[i], all[j], i + " vs " + j);
            }
        }
    }
}
