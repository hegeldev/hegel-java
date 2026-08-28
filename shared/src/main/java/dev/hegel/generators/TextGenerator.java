package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates strings with fine-grained control over length and character selection.
 *
 * <p>Surrogate codepoints (Unicode category {@code Cs}) are excluded by default so generated
 * strings round-trip cleanly through Java; request specific categories to override the default
 * exclusion.
 *
 * <p>Lengths default to {@code [0, 100]} characters (or {@code [minSize, minSize + 100]} for a
 * larger minimum); set an explicit {@link #maxSize(int)} for longer strings.
 */
public final class TextGenerator implements Generator<String> {
    /** The default length cap when no explicit {@code maxSize} is set. */
    static final long DEFAULT_MAX_SIZE = 100;

    private final long minSize;
    private final long maxSize;
    private final Integer minCodepoint;
    private final Integer maxCodepoint;
    private final List<String> categories;
    private final List<String> excludeCategories;
    private final String includeChars;
    private final String excludeChars;
    private final HandleCache cache = new HandleCache();

    public TextGenerator(
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
        this.categories = validateCategories(categories);
        this.excludeCategories = excludeCategories;
        this.includeChars = includeChars;
        this.excludeChars = excludeChars;
    }

    private static List<String> validateCategories(List<String> categories) {
        if (categories != null) {
            for (String c : categories) {
                if (c.equals("Cs") || c.equals("C")) {
                    throw new IllegalArgumentException(
                            "text: category \"" + c + "\" includes surrogate codepoints, unsupported");
                }
            }
        }
        return categories;
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
        return new TextGenerator(minSize, maxSize, min, max, categories, excludeCategories, includeChars, excludeChars);
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
                minSize, maxSize, minCodepoint, maxCodepoint, categories, List.of(cats), includeChars, excludeChars);
    }

    /**
     * @param chars characters always eligible for inclusion
     * @return a copy with the include set
     */
    public TextGenerator includeCharacters(String chars) {
        return new TextGenerator(
                minSize, maxSize, minCodepoint, maxCodepoint, categories, excludeCategories, chars, excludeChars);
    }

    /**
     * @param chars characters never included
     * @return a copy with the exclude set
     */
    public TextGenerator excludeCharacters(String chars) {
        return new TextGenerator(
                minSize, maxSize, minCodepoint, maxCodepoint, categories, excludeCategories, includeChars, chars);
    }

    /** @hidden */
    @Override
    public String doDraw(TestCase tc) {
        return tc.generateString(cache.get(tc, this::buildHandle));
    }

    private dev.hegel.StringGeneratorHandle buildHandle(TestCase tc) {
        List<String> exclude;
        if (categories != null) {
            exclude = null;
        } else {
            // Surrogates are excluded by default so drawn strings are valid Java strings.
            exclude = new ArrayList<>(excludeCategories == null ? List.of() : excludeCategories);
            if (!exclude.contains("Cs")) {
                exclude.add("Cs");
            }
        }
        return tc.textGenerator(
                minSize,
                Sizes.resolveMax(minSize, maxSize, DEFAULT_MAX_SIZE),
                null,
                minCodepoint == null ? 0 : minCodepoint,
                maxCodepoint == null ? Abi.NO_MAX_CODEPOINT : maxCodepoint,
                categories,
                exclude,
                includeChars,
                excludeChars);
    }
}
