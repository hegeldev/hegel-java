package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * A configurable in-memory {@link Libhegel} for exercising error paths and runner logic without the
 * native engine. Every per-case primitive's return code is a public field defaulting to {@link
 * Abi#OK}; set one to a negative code to drive a specific translation path.
 */
final class FakeLibhegel implements Libhegel {
    // Opaque handle sentinels (non-null addresses).
    static final MemorySegment SETTINGS = MemorySegment.ofAddress(0x100);
    static final MemorySegment RUN = MemorySegment.ofAddress(0x200);
    static final MemorySegment TC = MemorySegment.ofAddress(0x300);
    static final MemorySegment RESULT = MemorySegment.ofAddress(0x400);

    String lastError = "fake error";
    String version = "0.0.0-fake";

    // Run-loop control.
    int caseCount = 1; // how many test cases nextTestCase yields
    private int casesServed;
    String doneLastError = ""; // lastError reported on normal completion
    boolean nextTestCaseError; // if true, nextTestCase returns NULL with lastError set
    boolean runStartNull;
    boolean runResultNull;
    boolean finalReplay;
    boolean passed = true;

    // Recorded outcomes.
    final List<Integer> markedStatuses = new ArrayList<>();
    final List<String> markedOrigins = new ArrayList<>();
    int markCompleteRc = Abi.OK;

    // Per-primitive return codes.
    int generateRc = Abi.OK;
    byte[] generateValue = CBORObject.FromObject(0).EncodeToBytes();
    int startSpanRc = Abi.OK;
    int stopSpanRc = Abi.OK;
    int newCollectionRc = Abi.OK;
    long collectionId = 7;
    int collectionMoreRc = Abi.OK;
    boolean[] moreSequence = {false};
    private int moreIndex;
    int collectionRejectRc = Abi.OK;
    int targetRc = Abi.OK;

    // Failure list for the result.
    static final class Failure {
        String panic = "panic";
        String diagnostic = "diagnostic";
        String origin = "origin";
    }

    final List<Failure> failures = new ArrayList<>();

    @Override
    public MemorySegment settingsNew() {
        return SETTINGS;
    }

    @Override
    public void settingsFree(MemorySegment s) {}

    @Override
    public void settingsMode(MemorySegment s, int mode) {}

    @Override
    public void settingsTestCases(MemorySegment s, long n) {}

    @Override
    public void settingsVerbosity(MemorySegment s, int v) {}

    @Override
    public void settingsSeed(MemorySegment s, long seed, boolean hasSeed) {}

    @Override
    public void settingsDerandomize(MemorySegment s, boolean derandomize) {}

    @Override
    public void settingsReportMultipleFailures(MemorySegment s, boolean yes) {}

    @Override
    public void settingsDatabase(MemorySegment s, String path) {}

    @Override
    public void settingsDatabaseKey(MemorySegment s, String key) {}

    int phasesMask = -1; // captured; -1 means settingsPhases was never called

    @Override
    public void settingsPhases(MemorySegment s, int mask) {
        phasesMask = mask;
    }

    @Override
    public void settingsSuppressHealthCheck(MemorySegment s, int mask) {}

    @Override
    public MemorySegment runStart(MemorySegment settings) {
        return runStartNull ? MemorySegment.NULL : RUN;
    }

    @Override
    public MemorySegment nextTestCase(MemorySegment run) {
        if (nextTestCaseError) {
            return MemorySegment.NULL;
        }
        if (casesServed >= caseCount) {
            lastError = doneLastError;
            return MemorySegment.NULL;
        }
        casesServed++;
        return TC;
    }

    @Override
    public MemorySegment runResult(MemorySegment run) {
        return runResultNull ? MemorySegment.NULL : RESULT;
    }

    @Override
    public void runFree(MemorySegment run) {}

    @Override
    public int generate(MemorySegment tc, byte[] schema, byte[][] out) {
        if (generateRc == Abi.OK) {
            out[0] = generateValue;
        }
        return generateRc;
    }

    @Override
    public int startSpan(MemorySegment tc, long label) {
        return startSpanRc;
    }

    @Override
    public int stopSpan(MemorySegment tc, boolean discard) {
        return stopSpanRc;
    }

    @Override
    public int newCollection(MemorySegment tc, long minSize, long maxSize, long[] outId) {
        if (newCollectionRc == Abi.OK) {
            outId[0] = collectionId;
        }
        return newCollectionRc;
    }

    @Override
    public int collectionMore(MemorySegment tc, long id, boolean[] outMore) {
        if (collectionMoreRc == Abi.OK) {
            outMore[0] = moreIndex < moreSequence.length && moreSequence[moreIndex++];
        }
        return collectionMoreRc;
    }

    @Override
    public int collectionReject(MemorySegment tc, long id, String why) {
        return collectionRejectRc;
    }

    @Override
    public int target(MemorySegment tc, double value, String label) {
        return targetRc;
    }

    @Override
    public int markComplete(MemorySegment tc, int status, String origin) {
        markedStatuses.add(status);
        markedOrigins.add(origin);
        return markCompleteRc;
    }

    @Override
    public boolean isFinalReplay(MemorySegment tc) {
        return finalReplay;
    }

    @Override
    public boolean resultPassed(MemorySegment result) {
        return passed;
    }

    @Override
    public long resultFailureCount(MemorySegment result) {
        return failures.size();
    }

    @Override
    public MemorySegment resultFailure(MemorySegment result, long index) {
        return MemorySegment.ofAddress(0x500 + index);
    }

    @Override
    public String failurePanicMessage(MemorySegment failure) {
        return failures.get((int) (failure.address() - 0x500)).panic;
    }

    @Override
    public String failureDiagnostic(MemorySegment failure) {
        return failures.get((int) (failure.address() - 0x500)).diagnostic;
    }

    @Override
    public String failureOrigin(MemorySegment failure) {
        return failures.get((int) (failure.address() - 0x500)).origin;
    }

    @Override
    public String lastErrorMessage() {
        return lastError;
    }

    @Override
    public String version() {
        return version;
    }
}
