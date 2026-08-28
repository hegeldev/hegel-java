package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.AssumeRejected;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.function.Predicate;

/**
 * Result of {@link Generator#filter}. Always uses the composite draw path: it retries the source up
 * to {@link #FILTER_RETRIES} times, bracketing each attempt in a {@code filter} span, and rejects the
 * whole case if none satisfy the predicate.
 *
 * @param <T> the value type
 */
public final class FilteredGenerator<T> implements Generator<T> {
    /** Conventional retry limit before a filter rejects the whole case. */
    static final int FILTER_RETRIES = 3;

    private final Generator<T> source;
    private final Predicate<? super T> predicate;

    public FilteredGenerator(Generator<T> source, Predicate<? super T> predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @Override
    public T doDraw(TestCase tc) {
        for (int attempt = 0; attempt < FILTER_RETRIES; attempt++) {
            tc.startSpan(Abi.LABEL_FILTER);
            boolean discard = true;
            try {
                T value = source.doDraw(tc);
                if (predicate.test(value)) {
                    discard = false;
                    return value;
                }
            } finally {
                tc.stopSpan(discard);
            }
        }
        throw new AssumeRejected();
    }
}
