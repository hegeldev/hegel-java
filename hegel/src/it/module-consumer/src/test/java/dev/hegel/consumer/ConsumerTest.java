package dev.hegel.consumer;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hegel.HegelTest;
import dev.hegel.TestCase;

class ConsumerTest {
    @HegelTest
    void additionCommutes(TestCase tc) {
        int x = tc.draw(integers().min(0).max(100));
        int y = tc.draw(integers().min(0).max(100));
        assertEquals(x + y, y + x);
    }
}
