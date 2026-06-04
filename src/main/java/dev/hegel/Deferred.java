package dev.hegel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A forward reference to a generator whose definition is not yet known, enabling self-recursive and
 * mutually recursive generators.
 *
 * <p>A {@code Deferred<T>} is itself a {@link Generator}: pass it into other generators while
 * building a recursive structure, then call {@link #set} once to supply the real implementation.
 * Drawing before {@code set} has been called fails.
 *
 * <pre>{@code
 * // A binary tree of integers: a leaf, or a branch of two subtrees.
 * Deferred<Tree> tree = Generators.deferred();
 * Generator<Tree> leaf = integers().map(Leaf::new);
 * Generator<Tree> branch = tuples(tree, tree).map(t -> new Branch((Tree) t.get(0), (Tree) t.get(1)));
 * tree.set(oneOf(leaf, branch));
 * Tree t = tc.draw(tree);
 * }</pre>
 *
 * <p>Obtain one from {@link Generators#deferred()}.
 *
 * @param <T> the type of value produced
 */
public final class Deferred<T> implements Generator<T> {
  private final AtomicReference<Generator<T>> delegate = new AtomicReference<>();

  Deferred() {}

  /**
   * Supply the implementation this reference delegates to. May be called only once.
   *
   * @param generator the generator to delegate to
   * @throws IllegalStateException if this deferred generator has already been set
   */
  public void set(Generator<T> generator) {
    if (!delegate.compareAndSet(null, generator)) {
      throw new IllegalStateException("deferred generator has already been set");
    }
  }

  @Override
  public T generate(TestCase tc) {
    Generator<T> g = delegate.get();
    if (g == null) {
      throw new IllegalStateException("deferred generator has not been set");
    }
    return g.generate(tc);
  }
}
