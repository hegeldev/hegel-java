package dev.hegel;

import static dev.hegel.Generators.uuids;
import static dev.hegel.Utils.assertAllExamples;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidGeneratorTest {
    @Test
    void defaultUuidsAreVersion4() {
        assertAllExamples(uuids(), u -> UUID.fromString(u.toString()).equals(u) && u.version() == 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void configuredUuidVersionsAreRespected(int version) {
        assertAllExamples(
                uuids().version(version), u -> UUID.fromString(u.toString()).equals(u) && u.version() == version);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void invalidVersionsAreRejected(int version) {
        assertThrows(IllegalArgumentException.class, () -> uuids().version(version));
    }
}
