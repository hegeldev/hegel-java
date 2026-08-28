package dev.hegel.generators;

import dev.hegel.StringGeneratorHandle;
import dev.hegel.TestCase;
import java.util.function.Function;

/**
 * Caches an engine string-generator handle for one generator configuration, so the alphabet or
 * pattern work happens once, not per draw.
 *
 * <p>The handle is rebuilt if a cached one was created by a different binding (a test swapped the
 * engine between draws). Benign racing is fine: two threads may both build, and every built handle
 * is valid and eventually freed by its own {@link StringGeneratorHandle} cleaner.
 */
final class HandleCache {
    private volatile StringGeneratorHandle handle;

    StringGeneratorHandle get(TestCase tc, Function<TestCase, StringGeneratorHandle> build) {
        StringGeneratorHandle h = handle;
        if (h == null || !tc.ownsStringGenerator(h)) {
            h = build.apply(tc);
            handle = h;
        }
        return h;
    }
}
