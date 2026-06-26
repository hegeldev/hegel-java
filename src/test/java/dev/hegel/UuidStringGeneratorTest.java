package dev.hegel;

import static dev.hegel.Generators.uuidStrings;
import static dev.hegel.Utils.assertAllExamples;
import static dev.hegel.Utils.findAny;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidStringGeneratorTest {
    @Test
    void defaultUuidStringsAreVersion4() {
        assertAllExamples(uuidStrings(), s -> UUID.fromString(s).version() == 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void configuredUuidStringVersionsAreRespected(int version) {
        assertAllExamples(
                uuidStrings().version(version), s -> UUID.fromString(s).version() == version);
    }

    /**
     * This test reflects the current behavior of hegel-core:
     * it generates uuids with these versions
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 7, 8})
    void anyVersionGeneratesUuidStringsWithDifferentVersions(int version) {
        findAny(uuidStrings().anyVersion(), u -> UUID.fromString(u).version() == version);
    }

    /**
     * Just reflects the current behavior - uuids with the 6th version are not generated.
     */
    @Test
    void hegelCoreDoesntGenerateUuidStringsWithVersion6() {
        assertAllExamples(uuidStrings().anyVersion(), u -> UUID.fromString(u).version() != 6);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void invalidVersionsAreRejected(int version) {
        assertThrows(IllegalArgumentException.class, () -> uuidStrings().version(version));
    }
}
