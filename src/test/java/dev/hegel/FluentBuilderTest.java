package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Bounds and sizes are configured exclusively through fluent builder methods. Each bound can be set
 * independently, and an unset bound stays at its full-range default. Equivalence is checked at the
 * engine boundary: the parameters a draw hands the (fake) engine.
 */
class FluentBuilderTest {
    private FakeLibhegel fake;

    private TestCase testCase() {
        fake = new FakeLibhegel();
        return new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @Test
    void integersDefaultIsFullRange() {
        testCase().draw(integers());
        assertEquals(Integer.MIN_VALUE, fake.integerMin);
        assertEquals(Integer.MAX_VALUE, fake.integerMax);
    }

    @Test
    void integersIndependentBounds() {
        // Setting one bound leaves the other at the full-range default.
        testCase().draw(integers().max(5));
        assertEquals(Integer.MIN_VALUE, fake.integerMin);
        assertEquals(5, fake.integerMax);
        testCase().draw(integers().min(-3));
        assertEquals(-3, fake.integerMin);
        assertEquals(Integer.MAX_VALUE, fake.integerMax);
    }

    @Test
    void longsIndependentBounds() {
        testCase().draw(longs().max(5));
        assertEquals(Long.MIN_VALUE, fake.integerMin);
        assertEquals(5, fake.integerMax);
        testCase().draw(longs().min(-3));
        assertEquals(-3, fake.integerMin);
        assertEquals(Long.MAX_VALUE, fake.integerMax);
    }

    @Test
    void binarySizeDefaults() {
        // With no explicit maxSize the length is capped at the default (100), shifted up when the
        // minimum exceeds it.
        testCase().draw(binary().minSize(2));
        assertEquals(2, fake.bytesMinSize);
        assertEquals(100, fake.bytesMaxSize);
        testCase().draw(binary().minSize(150));
        assertEquals(150, fake.bytesMinSize);
        assertEquals(250, fake.bytesMaxSize);
        testCase().draw(binary().maxSize(7));
        assertEquals(0, fake.bytesMinSize);
        assertEquals(7, fake.bytesMaxSize);
    }

    @Test
    void textSizeDefaultsAndCharacterConfig() {
        testCase().draw(text().minSize(3));
        assertEquals(3, fake.textMinSize);
        assertEquals(100, fake.textMaxSize);
        // Surrogates are excluded by default.
        assertEquals(java.util.List.of("Cs"), fake.textExcludeCategories);
        assertEquals(null, fake.textCategories);

        testCase().draw(text().minSize(200));
        assertEquals(200, fake.textMinSize);
        assertEquals(300, fake.textMaxSize);

        testCase()
                .draw(text().codepoints('a', 'z')
                        .categories("Nd")
                        .includeCharacters("x")
                        .excludeCharacters("y"));
        assertEquals((long) 'a', fake.textMinCodepoint);
        assertEquals((long) 'z', fake.textMaxCodepoint);
        assertEquals(java.util.List.of("Nd"), fake.textCategories);
        assertEquals(null, fake.textExcludeCategories);
        assertEquals("x", fake.textIncludeCharacters);
        assertEquals("y", fake.textExcludeCharacters);

        // An explicit exclusion keeps Cs appended exactly once.
        testCase().draw(text().excludeCategories("Cc"));
        assertEquals(java.util.List.of("Cc", "Cs"), fake.textExcludeCategories);
        testCase().draw(text().excludeCategories("Cs"));
        assertEquals(java.util.List.of("Cs"), fake.textExcludeCategories);
    }

    @Test
    void listsMinOnlyStaysUnbounded() {
        // Setting only a minimum leaves the collection unbounded above (UINT64_MAX sentinel).
        testCase().draw(lists(integers()).minSize(1));
        assertEquals(1, fake.collectionMinSize);
        assertEquals(Abi.UNBOUNDED, fake.collectionMaxSize);
    }
}
