package dev.hegel;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Internal combinator implementations and the basicness dispatch helper. */
final class Gen {
  private Gen() {}

  /**
   * A lazily-resolved generator for forward references and recursion. It is intentionally not
   * {@link MaybeBasic}: resolving eagerly would recurse forever, so a deferred generator always
   * takes the composite path.
   */
  static final class Deferred<T> implements Generator<T> {
    private final Supplier<Generator<T>> supplier;
    private Generator<T> resolved;

    Deferred(Supplier<Generator<T>> supplier) {
      this.supplier = supplier;
    }

    @Override
    public T generate(TestCase tc) {
      if (resolved == null) {
        resolved = supplier.get();
      }
      return resolved.generate(tc);
    }
  }

  /** Conventional retry limit before a filter rejects the whole case. */
  static final int FILTER_RETRIES = 3;

  @SuppressWarnings("unchecked")
  static <T> BasicGenerator<T> asBasic(Generator<T> g) {
    if (g instanceof MaybeBasic<?> m) {
      return ((MaybeBasic<T>) m).asBasic();
    }
    return null;
  }

  /** A non-basic generator that brackets a draw function in one span. */
  static final class Composite<T> implements Generator<T> {
    private final long label;
    private final Function<TestCase, T> draw;

    Composite(long label, Function<TestCase, T> draw) {
      this.label = label;
      this.draw = draw;
    }

    @Override
    public T generate(TestCase tc) {
      tc.startSpan(label);
      try {
        return draw.apply(tc);
      } finally {
        tc.stopSpan(false);
      }
    }
  }

  static <T> Generator<T> filter(Generator<T> gen, Predicate<? super T> predicate) {
    return tc -> {
      for (int attempt = 0; attempt < FILTER_RETRIES; attempt++) {
        tc.startSpan(Abi.LABEL_FILTER);
        boolean discard = true;
        try {
          T value = gen.generate(tc);
          if (predicate.test(value)) {
            discard = false;
            return value;
          }
        } finally {
          tc.stopSpan(discard);
        }
      }
      throw new AssumeRejected();
    };
  }

  static <T, U> Generator<U> flatMap(
      Generator<T> gen, Function<? super T, ? extends Generator<U>> f) {
    return tc -> {
      tc.startSpan(Abi.LABEL_FLAT_MAP);
      try {
        T value = gen.generate(tc);
        Generator<U> next = f.apply(value);
        return next.generate(tc);
      } finally {
        tc.stopSpan(false);
      }
    };
  }
}
