package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void versionLoadsFromRealEngine() {
        Libhegel lib = Engine.get();
        String v = lib.version();
        System.out.println("libhegel version: " + v);
        assertNotNull(v);
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected version: " + v);
    }
}
