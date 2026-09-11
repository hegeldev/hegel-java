package dev.hegel.lowlevel;

import java.nio.file.Path;

/**
 * Service provider interface for a {@code libhegel} binding.
 *
 * <p>A binding jar implements this interface with a public class that has a public no-argument
 * constructor and registers it in {@code META-INF/services/dev.hegel.lowlevel.LibhegelBackend}.
 * {@link Libhegel#load()} finds it through {@link java.util.ServiceLoader}. The two bindings Hegel
 * ships — the Foreign Function and Memory API binding in {@code dev.hegel:hegel} and the JNA
 * binding in {@code dev.hegel:hegel-jna} — are registered this way; a third-party binding (JNI,
 * a GraalVM native, ...) plugs in the same way.
 */
public interface LibhegelBackend {
    /**
     * Open the shared library at {@code library} and return a binding over it.
     *
     * @param library the resolved path of the {@code libhegel} shared object
     * @return a thread-safe binding; callers keep one per process
     * @throws LibhegelException if the library cannot be opened or lacks a required symbol
     */
    Libhegel open(Path library);
}
