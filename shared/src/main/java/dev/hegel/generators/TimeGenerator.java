package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.time.LocalTime;

/**
 * Generates {@link LocalTime} values within an inclusive {@code [min, max]} range, at nanosecond
 * resolution.
 *
 * <p>The default range is the whole day; narrow it with the fluent {@link #min(LocalTime)} /
 * {@link #max(LocalTime)} methods. Values shrink toward the lower bound.
 */
public final class TimeGenerator implements Generator<LocalTime> {
    private final LocalTime min;
    private final LocalTime max;

    public TimeGenerator() {
        this(LocalTime.MIN, LocalTime.MAX);
    }

    public TimeGenerator(LocalTime min, LocalTime max) {
        if (min.isAfter(max)) {
            throw new IllegalArgumentException("times: min (" + min + ") > max (" + max + ")");
        }
        this.min = min;
        this.max = max;
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public TimeGenerator min(LocalTime min) {
        return new TimeGenerator(min, max);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public TimeGenerator max(LocalTime max) {
        return new TimeGenerator(min, max);
    }

    /** @hidden */
    @Override
    public LocalTime doDraw(TestCase tc) {
        return tc.generateTime(min, max);
    }
}
