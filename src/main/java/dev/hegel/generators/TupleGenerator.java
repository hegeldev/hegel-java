package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Generates fixed-length heterogeneous tuples. The element values are drawn in order inside a
 * TUPLE span and handed to an {@code assembler} that packs them into the user-facing type {@code
 * T} (a {@code TupleN} record, or the raw {@code List<Object>} for the variadic factory).
 *
 * @param <T> the assembled tuple type
 */
public final class TupleGenerator<T> implements Generator<T> {
    private final List<Generator<?>> elements;
    private final Function<List<Object>, T> assembler;

    public TupleGenerator(List<Generator<?>> elements, Function<List<Object>, T> assembler) {
        this.elements = List.copyOf(elements);
        this.assembler = assembler;
    }

    /** @hidden */
    @Override
    public T doDraw(TestCase tc) {
        tc.startSpan(Abi.LABEL_TUPLE);
        try {
            List<Object> out = new ArrayList<>(elements.size());
            for (Generator<?> g : elements) {
                out.add(g.doDraw(tc));
            }
            return assembler.apply(out);
        } finally {
            tc.stopSpan(false);
        }
    }
}
