package dev.hegel;

import java.nio.file.Path;

final class LibhegelBackend {
    private LibhegelBackend() {}

    static Libhegel open(Path libraryPath) {
        return new RealLibhegel(libraryPath);
    }
}
