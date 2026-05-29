package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates strings with fine-grained control over length and character selection. Always basic.
 *
 * <p>Surrogate codepoints (Unicode category {@code Cs}) are excluded by default so generated
 * strings round-trip cleanly through Java; request specific categories to override the default
 * exclusion.
 */
public final class TextGenerator implements Generator<String>, MaybeBasic<String> {
  private final long minSize;
  private final long maxSize;
  private final Integer minCodepoint;
  private final Integer maxCodepoint;
  private final List<String> categories;
  private final List<String> excludeCategories;
  private final String includeChars;
  private final String excludeChars;

  TextGenerator(
      long minSize,
      long maxSize,
      Integer minCodepoint,
      Integer maxCodepoint,
      List<String> categories,
      List<String> excludeCategories,
      String includeChars,
      String excludeChars) {
    Sizes.validate(minSize, maxSize, "text");
    this.minSize = minSize;
    this.maxSize = maxSize;
    this.minCodepoint = minCodepoint;
    this.maxCodepoint = maxCodepoint;
    this.categories = categories;
    this.excludeCategories = excludeCategories;
    this.includeChars = includeChars;
    this.excludeChars = excludeChars;
  }

  /**
   * @param minSize the minimum codepoint length
   * @return a copy with the minimum size set
   */
  public TextGenerator minSize(int minSize) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        categories,
        excludeCategories,
        includeChars,
        excludeChars);
  }

  /**
   * @param maxSize the maximum codepoint length
   * @return a copy with the maximum size set
   */
  public TextGenerator maxSize(int maxSize) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        categories,
        excludeCategories,
        includeChars,
        excludeChars);
  }

  /**
   * Restrict to the inclusive Unicode codepoint range {@code [min, max]}.
   *
   * @param min the minimum codepoint
   * @param max the maximum codepoint
   * @return a copy with the codepoint range set
   */
  public TextGenerator codepoints(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("text: minCodepoint > maxCodepoint");
    }
    return new TextGenerator(
        minSize, maxSize, min, max, categories, excludeCategories, includeChars, excludeChars);
  }

  /**
   * Restrict to the listed Unicode general categories (e.g. {@code "Lu"}, {@code "Nd"}).
   *
   * @param cats the allowed categories
   * @return a copy with the categories set
   */
  public TextGenerator categories(String... cats) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        List.of(cats),
        excludeCategories,
        includeChars,
        excludeChars);
  }

  /**
   * @param cats categories to exclude
   * @return a copy with excluded categories set
   */
  public TextGenerator excludeCategories(String... cats) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        categories,
        List.of(cats),
        includeChars,
        excludeChars);
  }

  /**
   * @param chars characters always eligible for inclusion
   * @return a copy with the include set
   */
  public TextGenerator includeCharacters(String chars) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        categories,
        excludeCategories,
        chars,
        excludeChars);
  }

  /**
   * @param chars characters never included
   * @return a copy with the exclude set
   */
  public TextGenerator excludeCharacters(String chars) {
    return new TextGenerator(
        minSize,
        maxSize,
        minCodepoint,
        maxCodepoint,
        categories,
        excludeCategories,
        includeChars,
        chars);
  }

  @Override
  public BasicGenerator<String> asBasic() {
    CBORObject schema = CBORObject.NewMap().Add("type", "string").Add("min_size", minSize);
    if (maxSize != Abi.UNBOUNDED) {
      schema.Add("max_size", maxSize);
    }
    if (minCodepoint != null) {
      schema.Add("min_codepoint", minCodepoint);
    }
    if (maxCodepoint != null) {
      schema.Add("max_codepoint", maxCodepoint);
    }
    if (categories != null) {
      CBORObject arr = CBORObject.NewArray();
      for (String c : categories) {
        if (c.equals("Cs") || c.equals("C")) {
          throw new IllegalArgumentException(
              "text: category \"" + c + "\" includes surrogate codepoints, unsupported");
        }
        arr.Add(c);
      }
      schema.Add("categories", arr);
    } else {
      List<String> excl =
          new ArrayList<>(excludeCategories == null ? List.of() : excludeCategories);
      if (!excl.contains("Cs")) {
        excl.add("Cs");
      }
      CBORObject arr = CBORObject.NewArray();
      for (String c : excl) {
        arr.Add(c);
      }
      schema.Add("exclude_categories", arr);
    }
    if (includeChars != null) {
      schema.Add("include_characters", includeChars);
    }
    if (excludeChars != null) {
      schema.Add("exclude_characters", excludeChars);
    }
    return new BasicGenerator<>(schema, Cbor::asString);
  }

  @Override
  public String generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}
