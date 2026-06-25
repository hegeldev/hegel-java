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
    void defaultUuidsAreVersion4() {
        assertAllExamples(uuids(), u -> UUID.fromString(u.toString()).equals(u) && u.version() == 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void configuredUuidVersionsAreRespected(int version) {
        assertAllExamples(
                uuids().version(version), u -> UUID.fromString(u.toString()).equals(u) && u.version() == version);
    }

    /**
     * This test reflects the current behavior of hegel-core:
     * it generates uuids with these versions
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 7, 8, 9, 12, 14, 15})
    void anyVersionGeneratesDifferentUuids(int version) {
        findAny(uuids().anyVersion(), u -> u.version() == version);
    }

    /**
     * Just reflects the current behavior - uuids with the 6th version are not generated.
     */
    @ValueSource(ints = {6, 11, 13})
    @ParameterizedTest
    void hegelCoreDoesntGenerateUuidWithSpecificVersions(int unsupportedVersion) {
        assertAllExamples(uuids().anyVersion(), u -> u.version() != unsupportedVersion);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void invalidVersionsAreRejected(int version) {
        assertThrows(IllegalArgumentException.class, () -> uuids().version(version));
    }
}
