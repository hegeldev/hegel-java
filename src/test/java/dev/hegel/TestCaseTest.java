package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.upokecenter.cbor.CBORObject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCaseTest {
  /** A DataSource that returns a fixed value and records target calls. */
  private static final class StubSource implements DataSource {
    double lastTarget = Double.NaN;
    String lastLabel;

    @Override
    public Object generate(CBORObject schema) {
      return 7;
    }

    @Override
    public void startSpan(long label) {}

    @Override
    public void stopSpan(boolean discard) {}

    @Override
    public long newCollection(long minSize, long maxSize) {
      return 0;
    }

    @Override
    public boolean collectionMore(long id) {
      return false;
    }

    @Override
    public void collectionReject(long id, String why) {}

    @Override
    public void target(double value, String label) {
      lastTarget = value;
      lastLabel = label;
    }
  }

  private TestCase newCase(StubSource s, boolean reporting, ByteArrayOutputStream buf) {
    return new TestCase(s, reporting, new PrintStream(buf, true, StandardCharsets.UTF_8));
  }

  @Test
  void drawReportsTopLevelWithLabelAndDefaultName() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    TestCase tc = newCase(new StubSource(), true, buf);
    tc.draw(constant(1), "x");
    tc.draw(constant(2));
    String out = buf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("x = 1;"), out);
    assertTrue(out.contains("draw_2 = 2;"), out);
  }

  @Test
  void drawDoesNotReportWhenNotReporting() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    TestCase tc = newCase(new StubSource(), false, buf);
    assertEquals(5, tc.draw(constant(5)));
    assertEquals("", buf.toString(StandardCharsets.UTF_8));
  }

  @Test
  void nestedDrawsAreNotReported() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    TestCase tc = newCase(new StubSource(), true, buf);
    Generator<Integer> nested =
        inner -> {
          int a = inner.draw(constant(10)); // nested: should not be printed
          return a + 1;
        };
    tc.draw(nested, "top");
    String out = buf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("top = 11;"), out);
    assertEquals(1, out.lines().count());
  }

  @Test
  void noteRespectsReporting() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    TestCase reporting = newCase(new StubSource(), true, buf);
    reporting.note("hello");
    assertTrue(buf.toString(StandardCharsets.UTF_8).contains("hello"));

    ByteArrayOutputStream quiet = new ByteArrayOutputStream();
    newCase(new StubSource(), false, quiet).note("nope");
    assertEquals("", quiet.toString(StandardCharsets.UTF_8));
  }

  @Test
  void assumeAndTarget() {
    StubSource s = new StubSource();
    TestCase tc = newCase(s, false, new ByteArrayOutputStream());
    tc.assume(true);
    assertThrows(AssumeRejected.class, () -> tc.assume(false));
    tc.target(3.5);
    assertEquals(3.5, s.lastTarget, 0.0);
    assertEquals("", s.lastLabel);
    tc.target(9.0, "score");
    assertEquals("score", s.lastLabel);
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
    return tc -> v;
  }
}
