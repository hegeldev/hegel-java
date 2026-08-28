package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers {@link LiveDataSource} return-code translation and the abort short-circuit. */
class BindingErrorPathsTest {
    private static final LocalDate DATE = LocalDate.of(2000, 1, 1);
    private static final LocalTime TIME = LocalTime.NOON;
    private static final LocalDateTime DATETIME = LocalDateTime.of(DATE, TIME);

    private LiveDataSource source(FakeLibhegel fake) {
        return new LiveDataSource(fake, FakeLibhegel.TC);
    }

    @Test
    void okPathsReturnTheEngineValues() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.booleanValue = true;
        fake.integerValue = 9L;
        fake.floatValue = 2.5;
        fake.stringValue = "drawn";
        LiveDataSource ds = source(fake);
        assertTrue(ds.generateBoolean(0.5));
        assertEquals(9, ds.generateInteger(0, 10));
        assertEquals(2.5, ds.generateFloat(64, 0, 10, false, false, false, false, Double.MIN_VALUE));
        assertArrayEquals(new byte[] {1, 2}, ds.generateBytes(0, 4));
        assertEquals("drawn", ds.generateString(ds.emailGenerator()));
        assertEquals(DATE, ds.generateDate(DATE, DATE));
        assertEquals(TIME, ds.generateTime(TIME, TIME));
        assertEquals(DATETIME, ds.generateDatetime(DATETIME, DATETIME));
        assertEquals(new UUID(0, 1), ds.generateUuid(null));
        assertArrayEquals(new byte[] {127, 0, 0, 1}, ds.generateIpv4());
        assertArrayEquals(new byte[16], ds.generateIpv6());
        assertEquals(7, ds.newCollection(0, 5));
        assertFalse(ds.collectionMore(7));
        ds.collectionReject(7, "dup");
        ds.startSpan(Abi.LABEL_LIST);
        ds.stopSpan(false);
        ds.target(1.0, "l");
        assertEquals(3, ds.newPool());
        assertEquals(0, ds.poolAdd(3));
        assertEquals(0, ds.poolGenerate(3, true));
        assertEquals(5, ds.newStateMachine(List.of("r"), List.of()));
        assertEquals(Abi.STATE_MACHINE_DONE, ds.stateMachineNextRule(5));
    }

    @Test
    void stringGeneratorConstructionAndOwnership() {
        FakeLibhegel fake = new FakeLibhegel();
        LiveDataSource ds = source(fake);
        StringGeneratorHandle text = ds.textGenerator(0, 10, null, 0, Abi.NO_MAX_CODEPOINT, null, null, null, null);
        StringGeneratorHandle regex = ds.regexGenerator("[a-z]", true, text);
        StringGeneratorHandle regexNoAlphabet = ds.regexGenerator("[a-z]", true, null);
        StringGeneratorHandle email = ds.emailGenerator();
        StringGeneratorHandle url = ds.urlGenerator();
        StringGeneratorHandle domain = ds.domainGenerator(255);
        for (StringGeneratorHandle h : List.of(text, regex, regexNoAlphabet, email, url, domain)) {
            assertNotNull(h);
            assertTrue(ds.ownsStringGenerator(h));
        }
        // A handle built by a different binding is not owned, so caches rebuild it.
        assertFalse(source(new FakeLibhegel()).ownsStringGenerator(text));
    }

    @Test
    void stopTestUnwindsAndAbortsEverything() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateIntegerRc = Abi.E_STOP_TEST;
        LiveDataSource ds = source(fake);
        assertThrows(StopTest.class, () -> ds.generateInteger(0, 1));
        assertTrue(ds.isAborted());
        // Subsequent value-producing primitives short-circuit to StopTest without touching libhegel.
        assertThrows(StopTest.class, () -> ds.generateBoolean(0.5));
        assertThrows(StopTest.class, () -> ds.generateInteger(0, 1));
        assertThrows(StopTest.class, () -> ds.generateFloat(64, 0, 1, false, false, false, false, 1e-300));
        assertThrows(StopTest.class, () -> ds.generateBytes(0, 1));
        assertThrows(StopTest.class, () -> ds.generateString(null));
        assertThrows(StopTest.class, () -> ds.generateDate(DATE, DATE));
        assertThrows(StopTest.class, () -> ds.generateTime(TIME, TIME));
        assertThrows(StopTest.class, () -> ds.generateDatetime(DATETIME, DATETIME));
        assertThrows(StopTest.class, () -> ds.generateUuid(4));
        assertThrows(StopTest.class, () -> ds.generateIpv4());
        assertThrows(StopTest.class, () -> ds.generateIpv6());
        assertThrows(
                StopTest.class, () -> ds.textGenerator(0, 1, null, 0, Abi.NO_MAX_CODEPOINT, null, null, null, null));
        assertThrows(StopTest.class, () -> ds.regexGenerator("x", true, null));
        assertThrows(StopTest.class, () -> ds.emailGenerator());
        assertThrows(StopTest.class, () -> ds.urlGenerator());
        assertThrows(StopTest.class, () -> ds.domainGenerator(10));
        assertThrows(StopTest.class, () -> ds.startSpan(Abi.LABEL_LIST));
        assertThrows(StopTest.class, () -> ds.newCollection(0, 1));
        assertThrows(StopTest.class, () -> ds.collectionMore(1));
        assertThrows(StopTest.class, () -> ds.collectionReject(1, "x"));
        assertThrows(StopTest.class, () -> ds.newPool());
        assertThrows(StopTest.class, () -> ds.poolAdd(1));
        assertThrows(StopTest.class, () -> ds.poolGenerate(1, false));
        assertThrows(StopTest.class, () -> ds.newStateMachine(List.of("r"), List.of()));
        assertThrows(StopTest.class, () -> ds.stateMachineNextRule(1));
        assertThrows(StopTest.class, () -> ds.target(1.0, "l"));
        // stopSpan is a no-op once aborted (used by span-closing finally blocks).
        ds.stopSpan(false);
    }

    @Test
    void assumeUnwindsAndAborts() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateStringRc = Abi.E_ASSUME;
        LiveDataSource ds = source(fake);
        StringGeneratorHandle email = ds.emailGenerator();
        assertThrows(AssumeRejected.class, () -> ds.generateString(email));
        assertTrue(ds.isAborted());
    }

    @Test
    void invalidArgBecomesIllegalArgumentException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.stringGeneratorTextRc = Abi.E_INVALID_ARG;
        fake.lastError = "empty alphabet";
        LiveDataSource ds = source(fake);
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> ds.textGenerator(0, 1, null, 0, Abi.NO_MAX_CODEPOINT, List.of(), null, null, null));
        assertTrue(e.getMessage().contains("empty alphabet"));
        assertFalse(ds.isAborted());
    }

    @Test
    void backendErrorBecomesHegelException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateBooleanRc = Abi.E_BACKEND;
        fake.lastError = "boom";
        LiveDataSource ds = source(fake);
        HegelException e = assertThrows(HegelException.class, () -> ds.generateBoolean(0.5));
        assertTrue(e.getMessage().contains("boom"));
        assertFalse(ds.isAborted());
    }

    @Test
    void backendErrorWithNullMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateBooleanRc = Abi.E_BACKEND;
        fake.lastError = null;
        assertThrows(HegelException.class, () -> source(fake).generateBoolean(0.5));
    }

    @Test
    void spanAndCollectionErrorsPropagate() {
        FakeLibhegel startSpan = new FakeLibhegel();
        startSpan.startSpanRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(startSpan).startSpan(Abi.LABEL_LIST));

        FakeLibhegel stopSpan = new FakeLibhegel();
        stopSpan.stopSpanRc = Abi.E_INVALID_HANDLE;
        assertThrows(HegelException.class, () -> source(stopSpan).stopSpan(false));

        FakeLibhegel newCollection = new FakeLibhegel();
        newCollection.newCollectionRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(newCollection).newCollection(0, 5));

        FakeLibhegel more = new FakeLibhegel();
        more.moreSequence = new boolean[] {true, false};
        LiveDataSource ds = source(more);
        assertTrue(ds.collectionMore(1));
        assertFalse(ds.collectionMore(1));

        FakeLibhegel moreError = new FakeLibhegel();
        moreError.collectionMoreRc = Abi.E_BACKEND;
        assertThrows(HegelException.class, () -> source(moreError).collectionMore(1));

        FakeLibhegel reject = new FakeLibhegel();
        reject.collectionRejectRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(reject).collectionReject(1, "dup"));

        FakeLibhegel target = new FakeLibhegel();
        target.targetRc = Abi.E_ASSUME;
        assertThrows(AssumeRejected.class, () -> source(target).target(1.0, "l"));
    }

    @Test
    void poolAndStateMachineErrorsPropagate() {
        FakeLibhegel pool = new FakeLibhegel();
        pool.newPoolRc = Abi.E_BACKEND;
        assertThrows(HegelException.class, () -> source(pool).newPool());

        FakeLibhegel add = new FakeLibhegel();
        add.poolAddRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(add).poolAdd(1));

        FakeLibhegel gen = new FakeLibhegel();
        gen.poolGenerateRc = Abi.E_ASSUME;
        assertThrows(AssumeRejected.class, () -> source(gen).poolGenerate(1, true));

        FakeLibhegel sm = new FakeLibhegel();
        sm.newStateMachineRc = Abi.E_INVALID_ARG;
        assertThrows(IllegalArgumentException.class, () -> source(sm).newStateMachine(List.of("r"), List.of()));

        FakeLibhegel next = new FakeLibhegel();
        next.stateMachineNextRuleRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(next).stateMachineNextRule(1));
    }
}
