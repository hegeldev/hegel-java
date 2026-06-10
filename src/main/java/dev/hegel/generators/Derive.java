package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.Generators;
import dev.hegel.HegelException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Reflection-based generator derivation. Maps a Java type to a default generator: scalar wrappers
 * to their primitive generators, enums to a choice over their constants, records to a generator
 * over their components, and {@code List}/{@code Set}/{@code Optional}/{@code Map} to the matching
 * collection generators (using the declared element types).
 */
public final class Derive {
    private Derive() {}

    /** Erase a generator's element type; the derived value is consumed as {@code Object}. */
    @SuppressWarnings("unchecked")
    private static Generator<Object> erase(Generator<?> g) {
        return (Generator<Object>) g;
    }

    public static Generator<Object> fromType(Type type) {
        if (type instanceof ParameterizedType pt) {
            return parameterized(pt);
        }
        if (type instanceof Class<?> cls) {
            return fromClass(cls);
        }
        throw new HegelException("Cannot derive a generator for type: " + type);
    }

    private static Generator<Object> fromClass(Class<?> cls) {
        Generator<?> g = scalar(cls);
        if (g != null) {
            return erase(g);
        }
        if (cls.isEnum()) {
            return erase(Generators.sampledFrom(List.of(cls.getEnumConstants())));
        }
        if (cls.isRecord()) {
            return erase(new RecordGenerator<>(cls, java.util.Map.of()));
        }
        throw new HegelException("No default generator for "
                + cls.getName()
                + "; supply one explicitly (e.g. a Generator parameter or a record override).");
    }

    private static Generator<?> scalar(Class<?> cls) {
        if (cls == int.class || cls == Integer.class) {
            return Generators.integers();
        }
        if (cls == long.class || cls == Long.class) {
            return Generators.longs();
        }
        if (cls == boolean.class || cls == Boolean.class) {
            return Generators.booleans();
        }
        if (cls == float.class || cls == Float.class) {
            return Generators.floats();
        }
        if (cls == double.class || cls == Double.class) {
            return Generators.doubles();
        }
        if (cls == String.class) {
            return Generators.text();
        }
        if (cls == byte[].class) {
            return Generators.binary();
        }
        if (cls == java.time.Duration.class) {
            return Generators.durations();
        }
        if (cls == java.time.LocalDate.class) {
            return Generators.dates();
        }
        if (cls == java.time.LocalTime.class) {
            return Generators.times();
        }
        if (cls == java.time.LocalDateTime.class) {
            return Generators.datetimes();
        }
        if (cls == java.time.OffsetDateTime.class) {
            return Generators.datetimes().offsets(Generators.zoneOffsets());
        }
        if (cls == java.time.ZonedDateTime.class) {
            return Generators.datetimes().timezones(Generators.zoneIds());
        }
        if (cls == java.time.ZoneOffset.class) {
            return Generators.zoneOffsets();
        }
        if (cls == java.time.ZoneId.class) {
            return Generators.zoneIds();
        }
        return null;
    }

    private static Generator<Object> parameterized(ParameterizedType pt) {
        Class<?> raw = (Class<?>) pt.getRawType();
        Type[] args = pt.getActualTypeArguments();
        if (raw == List.class) {
            return erase(Generators.lists(fromType(args[0])));
        }
        if (raw == java.util.Set.class) {
            return erase(Generators.sets(fromType(args[0])));
        }
        if (raw == java.util.Optional.class) {
            return erase(Generators.optional(fromType(args[0])));
        }
        if (raw == java.util.Map.class) {
            return erase(Generators.maps(fromType(args[0]), fromType(args[1])));
        }
        throw new HegelException("Cannot derive a generator for generic type: " + pt);
    }
}
