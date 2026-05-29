package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Covers {@link Runner} branches with a fake binding (no engine). */
class RunnerTest {
  private static final Map<String, String> NO_CI = Map.of();
  private static final Map<String, String> CI = Map.of("CI", "true");

  private static PrintStream capture(ByteArrayOutputStream buf) {
    return new PrintStream(buf, true, StandardCharsets.UTF_8);
  }

  private static void run(FakeLibhegel fake, Settings s, Consumer<TestCase> body) {
    Runner.run(fake, s, body, NO_CI, capture(new ByteArrayOutputStream()));
  }

  @Test
  void happyPathMarksValid() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.caseCount = 3;
    run(fake, Settings.defaults().noDatabase(), tc -> tc.draw(integers()));
    assertEquals(
        List.of(Abi.STATUS_VALID, Abi.STATUS_VALID, Abi.STATUS_VALID), fake.markedStatuses);
  }

  @Test
  void runStartNullThrows() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.runStartNull = true;
    fake.lastError = "no start";
    HegelException e =
        assertThrows(HegelException.class, () -> run(fake, Settings.defaults(), tc -> {}));
    assertTrue(e.getMessage().contains("no start"));
  }

  @Test
  void nextTestCaseErrorThrows() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.nextTestCaseError = true;
    fake.lastError = "explode";
    assertThrows(HegelException.class, () -> run(fake, Settings.defaults().noDatabase(), tc -> {}));
  }

  @Test
  void runResultNullThrows() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.caseCount = 0;
    fake.runResultNull = true;
    assertThrows(HegelException.class, () -> run(fake, Settings.defaults().noDatabase(), tc -> {}));
  }

  @Test
  void markCompleteErrorThrows() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.markCompleteRc = Abi.E_ALREADY_COMPLETE;
    assertThrows(HegelException.class, () -> run(fake, Settings.defaults().noDatabase(), tc -> {}));
  }

  @Test
  void assumeMapsToInvalid() {
    FakeLibhegel fake = new FakeLibhegel();
    run(fake, Settings.defaults().noDatabase(), tc -> tc.assume(false));
    assertEquals(List.of(Abi.STATUS_INVALID), fake.markedStatuses);
  }

  @Test
  void stopTestMapsToOverrun() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.generateRc = Abi.E_STOP_TEST;
    run(fake, Settings.defaults().noDatabase(), tc -> tc.draw(integers()));
    assertEquals(List.of(Abi.STATUS_OVERRUN), fake.markedStatuses);
  }

  @Test
  void assertionFailureMapsToInterestingAndRecordsOrigin() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.finalReplay = true;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    Runner.run(
        fake,
        Settings.defaults().noDatabase(),
        tc -> {
          throw new AssertionError("nope");
        },
        NO_CI,
        capture(buf));
    assertEquals(List.of(Abi.STATUS_INTERESTING), fake.markedStatuses);
    assertTrue(fake.markedOrigins.get(0) != null);
    assertTrue(buf.toString(StandardCharsets.UTF_8).contains("nope"));
  }

  @Test
  void hegelExceptionFromBodyPropagates() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.generateRc = Abi.E_BACKEND;
    assertThrows(
        HegelException.class,
        () -> run(fake, Settings.defaults().noDatabase(), tc -> tc.draw(integers())));
    // The case was not marked complete; run_free drains it.
    assertTrue(fake.markedStatuses.isEmpty());
  }

  @Test
  void singleFailureProducesAssertionError() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.passed = false;
    FakeLibhegel.Failure f = new FakeLibhegel.Failure();
    f.diagnostic = "the bug";
    // Align the engine-reported origin with what the runner computes so the captured
    // message is stitched back in.
    f.origin = Runner.originOf(new AssertionError("boom"));
    fake.failures.add(f);
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                run(
                    fake,
                    Settings.defaults().noDatabase(),
                    tc -> {
                      throw new AssertionError("boom");
                    }));
    assertTrue(e.getMessage().contains("1 failing example"));
    assertTrue(e.getMessage().contains("the bug"));
    assertTrue(e.getMessage().contains("boom"), e.getMessage());
  }

  @Test
  void multipleFailuresUsePluralAndPanicFallback() {
    FakeLibhegel fake = new FakeLibhegel();
    fake.passed = false;
    FakeLibhegel.Failure f1 = new FakeLibhegel.Failure();
    f1.diagnostic = "";
    f1.panic = "panic-1";
    FakeLibhegel.Failure f2 = new FakeLibhegel.Failure();
    f2.diagnostic = "diag-2";
    fake.failures.add(f1);
    fake.failures.add(f2);
    AssertionError e =
        assertThrows(
            AssertionError.class, () -> run(fake, Settings.defaults().noDatabase(), tc -> {}));
    assertTrue(e.getMessage().contains("2 distinct failing examples"));
    assertTrue(e.getMessage().contains("panic-1"));
    assertTrue(e.getMessage().contains("diag-2"));
  }

  @Test
  void settingsBranchesAllApplied() {
    FakeLibhegel fake = new FakeLibhegel();
    Settings s =
        Settings.defaults()
            .testCases(10)
            .seed(7)
            .derandomize(true)
            .reportMultipleFailures(false)
            .singleTestCase(true)
            .suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH, HealthCheck.TOO_SLOW)
            .verbosity(Verbosity.VERBOSE)
            .database("/tmp/hegel-db")
            .name("myTest");
    run(fake, s, tc -> {});
    assertEquals(List.of(Abi.STATUS_VALID), fake.markedStatuses);
  }

  @Test
  void databaseDisabledAndCiDefaults() {
    run(new FakeLibhegel(), Settings.defaults().noDatabase(), tc -> {});
    // CI default disables the database and derandomizes.
    Runner.run(
        new FakeLibhegel(),
        Settings.defaults(),
        tc -> {},
        CI,
        capture(new ByteArrayOutputStream()));
    // Non-CI default leaves the engine database enabled; a name derives a key.
    Runner.run(
        new FakeLibhegel(),
        Settings.defaults().name("t"),
        tc -> {},
        NO_CI,
        capture(new ByteArrayOutputStream()));
  }

  @Test
  void databaseKeyIsTestName() {
    assertEquals("myProp", Runner.databaseKey("myProp"));
  }

  @Test
  void originFallsBackToClassNameWithoutUserFrame() {
    Throwable t = new RuntimeException("x");
    t.setStackTrace(new StackTraceElement[] {});
    assertEquals(RuntimeException.class.getName(), Runner.originOf(t));
  }
}
