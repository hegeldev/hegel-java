package dev.hegel;

import dev.hegel.lowlevel.Libhegel;
import dev.hegel.lowlevel.LibhegelBackend;
import java.nio.file.Path;

/**
 * The Foreign Function and Memory API binding, registered as a {@link LibhegelBackend} service
 * provider so {@link Libhegel#load()} finds it on the classpath.
 *
 * @hidden
 */
public final class FfmBackend implements LibhegelBackend {
    /** Public no-argument constructor, as {@link java.util.ServiceLoader} requires. */
    public FfmBackend() {}

    @Override
    public Libhegel open(Path library) {
        return new RealLibhegel(library);
    }
}
