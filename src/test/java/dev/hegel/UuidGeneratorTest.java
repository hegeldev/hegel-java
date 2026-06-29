package dev.hegel;

import static dev.hegel.Generators.uuids;
import static dev.hegel.Utils.assertAllExamples;
import static dev.hegel.Utils.findAny;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidGeneratorTest {
    @Test
    void defaultGeneratesValidUuids() {
        assertAllExamples(uuids(), u -> UUID.fromString(u.toString()).equals(u));
    }

    /** By default the version is unconstrained, so the engine emits non-RFC versions too. */
    @Test
    void defaultIsNotPinnedToAnRfcVersion() {
        findAny(uuids(), u -> u.version() < 1 || u.version() > 5);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void configuredUuidVersionsAreRespected(int version) {
        assertAllExamples(
                uuids().version(version), u -> UUID.fromString(u.toString()).equals(u) && u.version() == version);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, 7, 8, 9})
    void invalidVersionsAreRejected(int version) {
        assertThrows(IllegalArgumentException.class, () -> uuids().version(version));
    }
}
