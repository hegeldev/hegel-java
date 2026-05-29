package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The handle a property test body uses to draw values and steer the engine.
 *
 * <p>An instance is supplied to the test body for each case the engine runs. Draw values with
 * {@link #draw(Generator)}, reject uninteresting inputs with {@link #assume(boolean)}, attach debug
 * context with {@link #note(String)}, and guide the search with {@link #target(double)}.
 *
 * <p>On the engine's final replay of a minimal failing example, each top-level {@code draw} is
 * printed as an assignment (for example {@code x = 42;}) so the counterexample is readable.
 */
public final class TestCase {
  private final DataSource source;
  private final Map<String, Object> explicit; // non-null => explicit-example replay mode
  private final boolean reporting;
  private final PrintStream out;
  private int drawDepth;
  private int drawCounter;

  TestCase(DataSource source, boolean reporting, PrintStream out) {
    this.source = source;
    this.explicit = null;
    this.reporting = reporting;
    this.out = out;
  }

  TestCase(Map<String, Object> explicit, boolean reporting, PrintStream out) {
    this.source = null;
    this.explicit = explicit;
    this.reporting = reporting;
    this.out = out;
  }

  /**
   * Draw a value from {@code generator}.
   *
   * @param generator the generator to draw from
   * @param <T> the value type
   * @return the generated value
   */
  public <T> T draw(Generator<T> generator) {
    return draw(generator, null);
  }

  /**
   * Draw a value, naming it {@code label} in the falsifying-example output.
   *
   * @param generator the generator to draw from
   * @param label the variable name to show in counterexample output
   * @param <T> the value type
   * @return the generated value
   */
  public <T> T draw(Generator<T> generator, String label) {
    if (explicit != null) {
      return drawExplicit(label);
    }
    boolean top = drawDepth == 0;
    drawDepth++;
    T value;
    try {
      value = generator.generate(this);
    } finally {
      drawDepth--;
    }
    if (top) {
      drawCounter++;
      if (reporting) {
        String name = (label != null) ? label : "draw_" + drawCounter;
        out.println(name + " = " + repr(value) + ";");
      }
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private <T> T drawExplicit(String label) {
    if (label == null) {
      throw new HegelException(
          "explicit examples require labelled draws: use draw(generator, label)");
    }
    if (!explicit.containsKey(label)) {
      throw new HegelException("explicit example has no value for label '" + label + "'");
    }
    return (T) explicit.get(label);
  }

  /**
   * Reject the current test case unless {@code condition} holds. The engine discards it without
   * counting it against the test-case budget and tries another input.
   *
   * @param condition the precondition that must hold
   */
  public void assume(boolean condition) {
    if (!condition) {
      throw new AssumeRejected();
    }
  }

  /**
   * Record a debug message, shown only on the final replay of a failing case.
   *
   * @param message the message to record
   */
  public void note(String message) {
    if (reporting) {
      out.println(message);
    }
  }

  /**
   * Provide a score for the coverage-guided search; higher is treated as more interesting.
   *
   * @param value the observation
   */
  public void target(double value) {
    target(value, "");
  }

  /**
   * Provide a labelled score for the coverage-guided search.
   *
   * @param value the observation
   * @param label groups observations for multi-objective search
   */
  public void target(double value, String label) {
    if (explicit == null) {
      source.target(value, label);
    }
  }

  // --- package-private primitives used by generators ---

  Object generateFromSchema(CBORObject schema) {
    return source.generate(schema);
  }

  void startSpan(long label) {
    source.startSpan(label);
  }

  void stopSpan(boolean discard) {
    source.stopSpan(discard);
  }

  long newCollection(long minSize, long maxSize) {
    return source.newCollection(minSize, maxSize);
  }

  boolean collectionMore(long id) {
    return source.collectionMore(id);
  }

  void collectionReject(long id, String why) {
    source.collectionReject(id, why);
  }

  static String repr(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String s) {
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    if (value instanceof byte[] b) {
      return Arrays.toString(b);
    }
    if (value instanceof List<?> list) {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(repr(list.get(i)));
      }
      return sb.append("]").toString();
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        sb.append(repr(e.getKey())).append(": ").append(repr(e.getValue()));
      }
      return sb.append("}").toString();
    }
    return String.valueOf(value);
  }
}
