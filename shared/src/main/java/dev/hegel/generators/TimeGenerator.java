package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.time.LocalTime;

/**
 * Generates {@link LocalTime} values within an inclusive {@code [min, max]} range, at the engine's
 * microsecond resolution.
 *
 * <p>The default range is the whole day; narrow it with the fluent {@link #min(LocalTime)} /
 * {@link #max(LocalTime)} methods (bounds are snapped inward to whole microseconds). Values shrink
 * toward the lower bound.
 */
public final class TimeGenerator implements Generator<LocalTime> {
    static final LocalTime DEFAULT_MAX = LocalTime.of(23, 59, 59, 999_999_000);

    private final LocalTime min;
    private final LocalTime max;

    public TimeGenerator() {
        this(LocalTime.MIDNIGHT, DEFAULT_MAX);
    }

    public TimeGenerator(LocalTime min, LocalTime max) {
        LocalTime lo = roundUpToMicros(min);
        LocalTime hi = truncateToMicros(max);
        if (lo.isAfter(hi)) {
            throw new IllegalArgumentException(
                    "times: min (" + min + ") > max (" + max + ") at microsecond" + " resolution");
        }
        this.min = lo;
        this.max = hi;
    }

    private static LocalTime truncateToMicros(LocalTime t) {
        return t.withNano(t.getNano() / 1_000 * 1_000);
    }

    /** Snap a lower bound up to the next whole microsecond so drawn values never undershoot it. */
    private static LocalTime roundUpToMicros(LocalTime t) {
        LocalTime truncated = truncateToMicros(t);
        if (truncated.equals(t)) {
            return t;
        }
        if (t.toNanoOfDay() > LocalTime.MAX.toNanoOfDay() - 1_000) {
            throw new IllegalArgumentException("times: min (" + t + ") exceeds the last representable microsecond");
        }
        return truncated.plusNanos(1_000);
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
