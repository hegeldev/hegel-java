package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.sets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.Test;

/**
 * The bound/size-bearing generators are fluent builders: {@code integers(a, b)} is exactly {@code
 * integers().min(a).max(b)}, and each bound can be set independently. Equivalence is checked at the
 * schema level (two builders that describe the same draw produce the same CBOR schema).
 */
class FluentBuilderTest {
  private static CBORObject schema(Generator<?> g) {
    return ((MaybeBasic<?>) g).asBasic().schema;
  }

  @Test
  void integersFactoryEqualsFluent() {
    assertEquals(schema(integers(2, 7)), schema(integers().min(2).max(7)));
    assertEquals(
        schema(integers()), schema(integers().min(Integer.MIN_VALUE).max(Integer.MAX_VALUE)));
  }

  @Test
  void integersIndependentBounds() {
    // Setting one bound leaves the other at the full-range default.
    assertEquals(schema(integers(Integer.MIN_VALUE, 5)), schema(integers().max(5)));
    assertEquals(schema(integers(-3, Integer.MAX_VALUE)), schema(integers().min(-3)));
  }

  @Test
  void longsFactoryEqualsFluent() {
    assertEquals(schema(longs(2, 7)), schema(longs().min(2).max(7)));
    assertEquals(schema(longs(Long.MIN_VALUE, 5)), schema(longs().max(5)));
    assertEquals(schema(longs(-3, Long.MAX_VALUE)), schema(longs().min(-3)));
  }

  @Test
  void binaryFactoryEqualsFluent() {
    assertEquals(schema(binary(2, 4)), schema(binary().minSize(2).maxSize(4)));
    // Unbounded by default; setting only a minimum keeps it unbounded above.
    assertEquals(schema(binary(2, -1)), schema(binary().minSize(2)));
  }

  @Test
  void listsFactoryEqualsFluent() {
    assertEquals(schema(lists(integers(), 1, 3)), schema(lists(integers()).minSize(1).maxSize(3)));
    // Setting only a minimum leaves the length unbounded above (no max_size in the schema).
    CBORObject minOnly = schema(lists(integers()).minSize(1));
    assertEquals(1, minOnly.get("min_size").AsInt32());
    assertEquals(null, minOnly.get("max_size"));
  }

  @Test
  void setsFactoryEqualsFluent() {
    assertEquals(schema(sets(integers(), 1, 3)), schema(sets(integers()).minSize(1).maxSize(3)));
  }

  @Test
  void mapsFactoryEqualsFluent() {
    assertEquals(
        schema(maps(integers(), integers(), 1, 3)),
        schema(maps(integers(), integers()).minSize(1).maxSize(3)));
  }

  @Test
  void fluentBoundsValidate() {
    assertThrows(IllegalArgumentException.class, () -> integers().min(5).max(1));
    assertThrows(IllegalArgumentException.class, () -> longs().min(5).max(1));
    assertThrows(IllegalArgumentException.class, () -> binary().minSize(5).maxSize(2));
    assertThrows(IllegalArgumentException.class, () -> binary().minSize(-1));
    assertThrows(IllegalArgumentException.class, () -> lists(integers()).minSize(5).maxSize(2));
    assertThrows(IllegalArgumentException.class, () -> sets(integers()).minSize(5).maxSize(2));
    assertThrows(
        IllegalArgumentException.class, () -> maps(integers(), integers()).minSize(5).maxSize(2));
  }
}
