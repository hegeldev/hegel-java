package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.time.LocalDate;

/**
 * Generates {@link LocalDate} values within an inclusive {@code [min, max]} range.
 *
 * <p>The default range is {@code 0001-01-01} to {@code 9999-12-31}; narrow it with the fluent
 * {@link #min(LocalDate)} / {@link #max(LocalDate)} methods. Values shrink toward 2000-01-01, or
 * the nearest bound when that is out of range.
 */
public final class DateGenerator implements Generator<LocalDate> {
    static final LocalDate DEFAULT_MIN = LocalDate.of(1, 1, 1);
    static final LocalDate DEFAULT_MAX = LocalDate.of(9999, 12, 31);
    private static final int MAX_ENGINE_YEAR = 999_999;

    private final LocalDate min;
    private final LocalDate max;

    public DateGenerator() {
        this(DEFAULT_MIN, DEFAULT_MAX);
    }

    public DateGenerator(LocalDate min, LocalDate max) {
        if (min.isAfter(max)) {
            throw new IllegalArgumentException("dates: min (" + min + ") > max (" + max + ")");
        }
        validateYear(min);
        validateYear(max);
        this.min = min;
        this.max = max;
    }

    private static void validateYear(LocalDate date) {
        if (date.getYear() < -MAX_ENGINE_YEAR || date.getYear() > MAX_ENGINE_YEAR) {
            throw new IllegalArgumentException("dates: year of " + date + " is outside [-999999, 999999]");
        }
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public DateGenerator min(LocalDate min) {
        return new DateGenerator(min, max);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public DateGenerator max(LocalDate max) {
        return new DateGenerator(min, max);
    }

    /** @hidden */
    @Override
    public LocalDate doDraw(TestCase tc) {
        return tc.generateDate(min, max);
    }
}
