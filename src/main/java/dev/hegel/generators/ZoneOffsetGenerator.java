package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.time.ZoneOffset;

/**
 * Generates {@link ZoneOffset} values (fixed UTC offsets) within an inclusive {@code [min, max]}
 * range.
 *
 * <p>Offsets are drawn at one-second granularity. The default range is the whole legal {@code
 * ZoneOffset} span ({@code -18:00} to {@code +18:00}); narrow it with the fluent {@link
 * #min(ZoneOffset)} / {@link #max(ZoneOffset)} methods.
 */
public final class ZoneOffsetGenerator implements Generator<ZoneOffset> {
    private final int minSeconds;
    private final int maxSeconds;

    public ZoneOffsetGenerator(int minSeconds, int maxSeconds) {
        if (minSeconds > maxSeconds) {
            throw new IllegalArgumentException("zoneOffsets: min (" + minSeconds + "s) > max (" + maxSeconds + "s)");
        }
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public ZoneOffsetGenerator min(ZoneOffset min) {
        return new ZoneOffsetGenerator(min.getTotalSeconds(), maxSeconds);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public ZoneOffsetGenerator max(ZoneOffset max) {
        return new ZoneOffsetGenerator(minSeconds, max.getTotalSeconds());
    }

    /** @hidden */
    @Override
    public ZoneOffset doDraw(TestCase tc) {
        return ZoneOffset.ofTotalSeconds((int) tc.generateInteger(minSeconds, maxSeconds));
    }
}
