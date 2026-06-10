package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.Test;

/** Covers {@link LiveDataSource} return-code translation and the abort short-circuit. */
class BindingErrorPathsTest {
    private static final CBORObject SCHEMA = CBORObject.NewMap().Add("type", "boolean");

    private LiveDataSource source(FakeLibhegel fake) {
        return new LiveDataSource(fake, FakeLibhegel.TC);
    }

    @Test
    void generateStopTestUnwindsAndAborts() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateRc = Abi.E_STOP_TEST;
        LiveDataSource ds = source(fake);
        assertThrows(StopTest.class, () -> ds.generate(SCHEMA));
        assertTrue(ds.isAborted());
        // Subsequent value-producing primitives short-circuit to StopTest without touching libhegel.
        assertThrows(StopTest.class, () -> ds.generate(SCHEMA));
        assertThrows(StopTest.class, () -> ds.startSpan(Abi.LABEL_LIST));
        assertThrows(StopTest.class, () -> ds.newCollection(0, 1));
        assertThrows(StopTest.class, () -> ds.collectionMore(1));
        assertThrows(StopTest.class, () -> ds.collectionReject(1, "x"));
        assertThrows(StopTest.class, () -> ds.target(1.0, "l"));
        // stopSpan is a no-op once aborted (used by span-closing finally blocks).
        ds.stopSpan(false);
    }

    @Test
    void generateAssumeUnwinds() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateRc = Abi.E_ASSUME;
        LiveDataSource ds = source(fake);
        assertThrows(AssumeRejected.class, () -> ds.generate(SCHEMA));
        assertTrue(ds.isAborted());
    }

    @Test
    void backendErrorBecomesHegelException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateRc = Abi.E_BACKEND;
        fake.lastError = "boom";
        LiveDataSource ds = source(fake);
        HegelException e = assertThrows(HegelException.class, () -> ds.generate(SCHEMA));
        assertTrue(e.getMessage().contains("boom"));
        assertFalse(ds.isAborted());
    }

    @Test
    void startSpanStopTest() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.startSpanRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(fake).startSpan(Abi.LABEL_LIST));
    }

    @Test
    void stopSpanBackendError() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.stopSpanRc = Abi.E_INVALID_HANDLE;
        assertThrows(HegelException.class, () -> source(fake).stopSpan(false));
    }

    @Test
    void newCollectionReturnsIdThenStopTest() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.collectionId = 42;
        assertEquals(42, source(fake).newCollection(0, 5));

        fake.newCollectionRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(fake).newCollection(0, 5));
    }

    @Test
    void collectionMoreReturnsValueThenError() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.moreSequence = new boolean[] {true, false};
        LiveDataSource ds = source(fake);
        assertTrue(ds.collectionMore(1));
        assertFalse(ds.collectionMore(1));

        FakeLibhegel bad = new FakeLibhegel();
        bad.collectionMoreRc = Abi.E_BACKEND;
        assertThrows(HegelException.class, () -> source(bad).collectionMore(1));
    }

    @Test
    void collectionRejectAndTargetPropagate() {
        FakeLibhegel reject = new FakeLibhegel();
        reject.collectionRejectRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> source(reject).collectionReject(1, "dup"));

        FakeLibhegel okReject = new FakeLibhegel();
        source(okReject).collectionReject(1, "dup"); // OK path

        FakeLibhegel target = new FakeLibhegel();
        target.targetRc = Abi.E_ASSUME;
        assertThrows(AssumeRejected.class, () -> source(target).target(1.0, "l"));

        FakeLibhegel okTarget = new FakeLibhegel();
        source(okTarget).target(2.0, "l"); // OK path
    }
}
