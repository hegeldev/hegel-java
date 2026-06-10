package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Abi;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Generates fixed-length heterogeneous tuples. The element values are drawn in order and handed to
 * an {@code assembler} that packs them into the user-facing type {@code T} (a {@code TupleN} record,
 * or the raw {@code List<Object>} for the variadic factory). Basic when every element generator is
 * basic; otherwise generates each element in order inside a TUPLE span.
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
    public BasicGenerator<T> asBasic() {
        List<BasicGenerator<?>> basics = new ArrayList<>(elements.size());
        CBORObject schemas = CBORObject.NewArray();
        for (Generator<?> g : elements) {
            BasicGenerator<?> b = g.asBasic();
            if (b == null) {
                return null;
            }
            basics.add(b);
            schemas.Add(b.schema);
        }
        CBORObject schema = CBORObject.NewMap().Add("type", "tuple").Add("elements", schemas);
        return new BasicGenerator<>(schema, raw -> {
            List<Object> rawList = Cbor.asList(raw);
            List<Object> out = new ArrayList<>(basics.size());
            for (int i = 0; i < basics.size(); i++) {
                out.add(basics.get(i).parseRaw(rawList.get(i)));
            }
            return assembler.apply(out);
        });
    }

    @Override
    public T doDraw(TestCase tc) {
        BasicGenerator<T> basic = asBasic();
        if (basic != null) {
            return basic.doDraw(tc);
        }
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
