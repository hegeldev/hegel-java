package dev.hegel;

import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.floats;
import static dev.hegel.Utils.findAny;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

/**
 * {@code floats()} and {@code doubles()} are distinct: the former is a true 32-bit {@code float}
 * (IEEE single, schema width 32), the latter a 64-bit {@code double} (width 64).
 */
class FloatVsDoubleTest {
    @Test
    void schemasCarryTheRightWidth() {
        assertEquals(32, floats().asBasic().schema.get("width").AsInt32());
        assertEquals(64, doubles().asBasic().schema.get("width").AsInt32());
    }

    @HegelTest(testCases = 20, database = Database.DISABLED)
    void drawsAreTheRightJavaType(TestCase tc) {
        assertInstanceOf(Float.class, tc.draw(floats()));
        assertInstanceOf(Double.class, tc.draw(doubles()));
    }

    @Test
    void doublesProduceValuesBeyondFloatPrecision() {
        // A value in [0, 1] whose f32 narrowing differs from itself can only come from a true 64-bit
        // draw — proof that doubles() really uses the full mantissa rather than f32 rounding.
        double v = findAny(doubles().min(0).max(1), d -> (float) (double) d != d);
        assertEquals(true, (float) v != v);
    }
}
