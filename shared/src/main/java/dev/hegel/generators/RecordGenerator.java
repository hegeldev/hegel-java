package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.HegelException;
import dev.hegel.TestCase;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates instances of a record type by drawing each component from its derived (or overridden)
 * generator and invoking the canonical constructor.
 *
 * @param <T> the record type
 */
public final class RecordGenerator<T> implements Generator<T> {
    private final Class<T> type;
    private final Map<String, Generator<?>> overrides;

    public RecordGenerator(Class<T> type, Map<String, Generator<?>> overrides) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }
        this.type = type;
        this.overrides = overrides;
    }

    /**
     * Override the generator used for one component.
     *
     * @param component the component (field) name
     * @param generator the generator to use for it
     * @return a new record generator with the override applied
     */
    public RecordGenerator<T> with(String component, Generator<?> generator) {
        boolean known = false;
        for (RecordComponent rc : type.getRecordComponents()) {
            if (rc.getName().equals(component)) {
                known = true;
                break;
            }
        }
        if (!known) {
            throw new IllegalArgumentException(type.getName() + " has no record component named '" + component + "'");
        }
        Map<String, Generator<?>> next = new HashMap<>(overrides);
        next.put(component, generator);
        return new RecordGenerator<>(type, next);
    }

    @Override
    public T doDraw(TestCase tc) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];
        tc.startSpan(Abi.LABEL_FIXED_DICT);
        try {
            for (int i = 0; i < components.length; i++) {
                RecordComponent rc = components[i];
                paramTypes[i] = rc.getType();
                Generator<?> g = overrides.get(rc.getName());
                if (g == null) {
                    g = Derive.fromType(rc.getGenericType());
                }
                values[i] = g.doDraw(tc);
            }
        } finally {
            tc.stopSpan(false);
        }
        return construct(paramTypes, values);
    }

    private T construct(Class<?>[] paramTypes, Object[] values) {
        try {
            var ctor = type.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new HegelException("Failed to construct " + type.getName(), e);
        }
    }
}
