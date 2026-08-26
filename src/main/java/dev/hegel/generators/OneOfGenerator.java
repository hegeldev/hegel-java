package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.List;

/**
 * Chooses among alternative generators of the same type: an index is drawn and the selected
 * alternative is generated inside a ONE_OF span, so the shrinker can swap which branch is taken.
 */
public final class OneOfGenerator<T> implements Generator<T> {
    private final List<Generator<T>> options;

    public OneOfGenerator(List<Generator<T>> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("oneOf requires at least one generator");
        }
        this.options = List.copyOf(options);
    }

    /** @hidden */
    @Override
    public T doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_ONE_OF);
        try {
            int index = (int) tc.generateInteger(0, options.size() - 1);
            return options.get(index).doDraw(tc);
        } finally {
            tc.stopSpan(false);
        }
    }
}
