package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.Test;

/**
 * Bounds and sizes are configured exclusively through fluent builder methods. Each bound can be set
 * independently, and an unset bound stays at its full-range / unbounded default. Equivalence is
 * checked at the schema level (two builders that describe the same draw produce the same CBOR
 * schema).
 */
class FluentBuilderTest {
    private static CBORObject schema(Generator<?> g) {
        return g.asBasic().schema;
    }

    @Test
    void integersDefaultIsFullRange() {
        assertEquals(
                schema(integers()), schema(integers().min(Integer.MIN_VALUE).max(Integer.MAX_VALUE)));
    }

    @Test
    void integersIndependentBounds() {
        // Setting one bound leaves the other at the full-range default.
        assertEquals(
                schema(integers().max(5)),
                schema(integers().min(Integer.MIN_VALUE).max(5)));
        assertEquals(schema(integers().min(-3)), schema(integers().min(-3).max(Integer.MAX_VALUE)));
    }

    @Test
    void longsIndependentBounds() {
        assertEquals(schema(longs().max(5)), schema(longs().min(Long.MIN_VALUE).max(5)));
        assertEquals(schema(longs().min(-3)), schema(longs().min(-3).max(Long.MAX_VALUE)));
    }

    @Test
    void binaryMinOnlyStaysUnbounded() {
        // Setting only a minimum keeps the length unbounded above (no max_size in the schema).
        CBORObject minOnly = schema(binary().minSize(2));
        assertEquals(2, minOnly.get("min_size").AsInt32());
        assertEquals(null, minOnly.get("max_size"));
    }

    @Test
    void listsMinOnlyStaysUnbounded() {
        // Setting only a minimum leaves the length unbounded above (no max_size in the schema).
        CBORObject minOnly = schema(lists(integers()).minSize(1));
        assertEquals(1, minOnly.get("min_size").AsInt32());
        assertEquals(null, minOnly.get("max_size"));
    }
}
