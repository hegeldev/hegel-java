package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCaseTest {
    /** The captured output with platform line endings normalised, so exact comparisons hold on Windows. */
    private static String text(ByteArrayOutputStream buf) {
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** A TestCase over a fake binding; only the reporting/target plumbing is under test here. */
    private TestCase newCase(FakeLibhegel fake, boolean reporting, ByteArrayOutputStream buf) {
        return new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                reporting,
                Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8)));
    }

    @Test
    void drawReportsTopLevelWithLabelAndDefaultName() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), true, buf);
        tc.draw(constant(1), "x");
        tc.draw(constant(2));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("x = 1;"), out);
        assertTrue(out.contains("draw_1 = 2;"), out);
    }

    @Test
    void drawDoesNotReportWhenNotReporting() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), false, buf);
        assertEquals(5, tc.draw(constant(5)));
        assertEquals("", buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void nestedDrawsAreNotReported() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), true, buf);
        Generator<Integer> nested = new Generator<>() {
            @Override
            public Integer doDraw(TestCase inner) {
                int a = inner.draw(constant(10)); // nested: should not be printed
                return a + 1;
            }
        };
        tc.draw(nested, "top");
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("top = 11;"), out);
        assertEquals(1, out.lines().count());
    }

    @Test
    void noteRespectsReporting() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase reporting = newCase(new FakeLibhegel(), true, buf);
        reporting.note("hello");
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("hello"));

        ByteArrayOutputStream quiet = new ByteArrayOutputStream();
        newCase(new FakeLibhegel(), false, quiet).note("nope");
        assertEquals("", quiet.toString(StandardCharsets.UTF_8));
    }

    @Test
    void finalReplayRecordsDrawsAndNotes() {
        TestCase tc = newCase(new FakeLibhegel(), true, new ByteArrayOutputStream());
        assertTrue(tc.isFinal());
        tc.draw(constant(1), "x");
        tc.note("first");
        tc.draw(constant(2));
        java.util.LinkedHashMap<String, Object> want = new java.util.LinkedHashMap<>();
        want.put("x", 1);
        want.put("draw_1", 2);
        assertEquals(want, tc.draws());
        assertEquals(List.of("x", "draw_1"), List.copyOf(tc.draws().keySet()));
        assertEquals(List.of("first"), tc.notes());

        TestCase exploring = newCase(new FakeLibhegel(), false, new ByteArrayOutputStream());
        assertTrue(!exploring.isFinal());
        exploring.draw(constant(1), "x");
        exploring.note("ignored");
        assertTrue(exploring.draws().isEmpty());
        assertTrue(exploring.notes().isEmpty());
    }

    @Test
    void repeatedNamesAreNumberedFromTheirSecondUse() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), true, buf);
        for (int i = 1; i <= 3; i++) {
            tc.draw(constant(i), "x");
        }
        tc.draw(constant(8));
        tc.draw(constant(9));
        assertEquals(
                List.of("x", "x_2", "x_3", "draw_1", "draw_2"),
                List.copyOf(tc.draws().keySet()));
        assertEquals(3, tc.draws().get("x_3"));
        assertEquals("x = 1;\nx_2 = 2;\nx_3 = 3;\ndraw_1 = 8;\ndraw_2 = 9;\n", text(buf));
    }

    @Test
    void notesMadeMidDrawFollowTheDrawLine() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), true, buf);
        Generator<Integer> noisy = new Generator<>() {
            @Override
            public Integer doDraw(TestCase inner) {
                inner.note("inside");
                return 7;
            }
        };
        tc.note("before");
        tc.draw(noisy, "v");
        tc.note("after");
        assertEquals("before\nv = 7;\ninside\nafter\n", text(buf));
        assertEquals(List.of("before", "inside", "after"), tc.notes());
    }

    @Test
    void notesMadeBeforeAFailingDrawAreNotLost() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = newCase(new FakeLibhegel(), true, buf);
        Generator<Integer> failing = new Generator<>() {
            @Override
            public Integer doDraw(TestCase inner) {
                inner.note("about to fail");
                throw new IllegalStateException("inside");
            }
        };
        assertThrows(IllegalStateException.class, () -> tc.draw(failing, "v"));
        assertEquals("about to fail\n", text(buf));
    }

    @Test
    void verboseCasesReportWithoutRecording() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = new TestCase(
                new LiveDataSource(new FakeLibhegel(), FakeLibhegel.TC),
                false,
                true,
                Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        assertTrue(!tc.isFinal());
        tc.draw(constant(1), "x");
        tc.note("n");
        assertEquals("x = 1;\nn\n", text(buf));
        assertTrue(tc.draws().isEmpty());
        assertTrue(tc.notes().isEmpty());
    }

    @Test
    void spanOpensAndClosesAroundTheBody() {
        FakeLibhegel fake = new FakeLibhegel();
        TestCase tc = newCase(fake, false, new ByteArrayOutputStream());
        assertEquals(7, tc.span(Label.of("test.pair"), () -> 7));
        assertEquals(List.of(Label.of("test.pair")), fake.startedSpans);
        assertEquals(1, fake.stoppedSpans);
        // The span is closed on the exceptional path too.
        assertThrows(
                IllegalStateException.class,
                () -> tc.span(Label.TUPLE, () -> {
                    throw new IllegalStateException("inside");
                }));
        assertEquals(2, fake.stoppedSpans);
    }

    @Test
    void assumeAndTarget() {
        FakeLibhegel fake = new FakeLibhegel();
        TestCase tc = newCase(fake, false, new ByteArrayOutputStream());
        tc.assume(true);
        assertThrows(AssumeRejected.class, () -> tc.assume(false));
        tc.target(3.5);
        tc.target(9.0, "score");
        fake.targetRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> tc.target(1.0));
    }

    @Test
    void reprCoversAllShapes() {
        assertEquals("null", TestCase.repr(null));
        assertEquals("\"a\\\\b\\\"c\"", TestCase.repr("a\\b\"c"));
        assertEquals("[1, 2]", TestCase.repr(new byte[] {1, 2}));
        assertEquals("[1, \"x\"]", TestCase.repr(List.of(1, "x")));
        assertEquals("[]", TestCase.repr(List.of()));
        assertEquals("{1: 2}", TestCase.repr(Map.of(1, 2)));
        java.util.LinkedHashMap<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", 2);
        assertEquals("{\"a\": 1, \"b\": 2}", TestCase.repr(m));
        assertEquals("42", TestCase.repr(42));
    }

    private static Generator<Integer> constant(int v) {
        return new Generator<>() {
            @Override
            public Integer doDraw(TestCase tc) {
                return v;
            }
        };
    }
}
