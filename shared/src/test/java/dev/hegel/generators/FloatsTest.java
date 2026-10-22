package dev.hegel.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the subnormal default of {@link Floats#resolve} for every shape of bounds. The property
 * tests that draw arbitrary bounds ({@code GenerationQualityTest}) reach these branches only when
 * the engine happens to draw equal or one-sided bounds on the right side of the smallest normal,
 * which depends on the random stream; this test covers them deterministically.
 */
class FloatsTest {
    private static final double SMALLEST_NORMAL = Double.MIN_NORMAL;

    private static double smallestNonzero(Double min, Double max) {
        return Floats.resolve("doubles", 64, min, max, null, null, null, false, false)
                .smallestNonzeroMagnitude();
    }

    @Test
    void subnormalDefaultFollowsTheBounds() {
        // Unbounded, or bounded on one side: subnormals unless that bound excludes them all.
        assertEquals(Double.MIN_VALUE, smallestNonzero(null, null));
        assertEquals(Double.MIN_VALUE, smallestNonzero(-1.0, null));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(1.0, null));
        assertEquals(Double.MIN_VALUE, smallestNonzero(null, 1.0));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(null, -1.0));
        // Distinct bounds: subnormals whenever the range reaches into (-smallestNormal, smallestNormal).
        assertEquals(Double.MIN_VALUE, smallestNonzero(-1.0, 1.0));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(1.0, 2.0));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(-2.0, -1.0));
        // Equal bounds: subnormals only when the one value lies strictly inside that interval.
        assertEquals(Double.MIN_VALUE, smallestNonzero(0.0, 0.0));
        assertEquals(Double.MIN_VALUE, smallestNonzero(Double.MIN_VALUE, Double.MIN_VALUE));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(1.0, 1.0));
        assertEquals(SMALLEST_NORMAL, smallestNonzero(-1.0, -1.0));
    }

    @Test
    void widthPicksTheSubnormalMagnitude() {
        assertEquals(
                Float.MIN_VALUE,
                Floats.resolve("floats", 32, null, null, null, null, null, false, false)
                        .smallestNonzeroMagnitude());
        assertEquals(
                Float.MIN_NORMAL,
                Floats.resolve("floats", 32, 1.0, 2.0, null, null, null, false, false)
                        .smallestNonzeroMagnitude());
    }
}
