package dev.hegel.lowlevel;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

/**
 * The test-classpath {@link LibhegelBackend} service provider (registered under {@code
 * META-INF/services}): opens nothing and hands back a {@link Libhegel} proxy that remembers the path
 * it was "opened" with.
 */
public final class StubBackend implements LibhegelBackend {
    /** The path most recently passed to {@link #open}. */
    static Path lastOpened;

    @Override
    public Libhegel open(Path library) {
        lastOpened = library;
        return stub(library.toString());
    }

    /** A {@link Libhegel} whose {@code version()} is {@code version} and whose other methods return null. */
    static Libhegel stub(String version) {
        return (Libhegel) Proxy.newProxyInstance(
                Libhegel.class.getClassLoader(),
                new Class<?>[] {Libhegel.class},
                (proxy, method, args) -> method.getName().equals("version") ? version : null);
    }
}
