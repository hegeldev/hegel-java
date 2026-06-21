package dev.hegel;

import static dev.hegel.Generators.uuidStrings;
import static dev.hegel.Utils.assertAllExamples;
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

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void invalidVersionsAreRejected(int version) {
        assertThrows(IllegalArgumentException.class, () -> uuidStrings().version(version));
    }
}
