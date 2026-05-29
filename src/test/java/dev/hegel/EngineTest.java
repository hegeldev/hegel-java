package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class EngineTest {
  @Test
  void setResetAndLazyResolve() {
    try {
      FakeLibhegel fake = new FakeLibhegel();
      Engine.setForTesting(fake);
      assertSame(fake, Engine.get()); // cached (non-null) branch
      assertSame(fake, Engine.get());

      Engine.reset();
      Libhegel real = Engine.get(); // null branch: resolves the real engine
      assertNotSame(fake, real);
      assertSame(real, Engine.get());
    } finally {
      Engine.reset();
    }
  }
}
