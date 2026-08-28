package dev.hegel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A configurable in-memory {@link Libhegel} for exercising error paths and runner logic without the
 * native engine. Every per-case primitive's return code is a public field defaulting to {@link
 * Abi#OK}; set one to a negative code to drive a specific translation path. Draws echo their lower
 * bound (or a canned value) so generator plumbing can run end to end.
 */
final class FakeLibhegel implements Libhegel {
    // Opaque handle sentinels (non-null addresses).
    static final long SETTINGS = 0x100;
    static final long RUN = 0x200;
    static final long TC = 0x300;
    static final long RESULT = 0x400;
    static final long STRING_GEN = 0x600;

    String lastError = "fake error";
    String version = "0.0.0-fake";

    // Run-loop control.
    int caseCount = 1; // how many test cases nextTestCase yields
    private int casesServed;
    boolean runStartFails;
    boolean nextTestCaseFails;
    int runStatus = Abi.RUN_STATUS_PASSED;
    String runError = "run error";
    final List<String> failureBlobs = new ArrayList<>(); // one entry per distinct failure
    int fromBlobRc = Abi.OK;
    final List<String> replayedBlobs = new ArrayList<>();
    Consumer<String> output; // the callback runStart registered

    // Recorded outcomes and teardown.
    final List<Integer> markedStatuses = new ArrayList<>();
    final List<String> markedOrigins = new ArrayList<>();
    int markCompleteRc = Abi.OK;
    int freedTestCases;
    int freedStringGenerators;
    boolean runFreed;
    boolean runResultFreed;
    boolean settingsFreed;

    // Captured settings.
    int phasesMask = -1; // -1 means settingsPhases was never called
    int suppressMask = -1;
    Integer modeCode;
    Integer backendCode;
    Long testCases;
    String databasePath = "unset";
    String databaseKey;
    Boolean derandomize;

    // Per-primitive return codes and canned values.
    int generateBooleanRc = Abi.OK;
    boolean booleanValue;
    int generateIntegerRc = Abi.OK;
    Long integerValue; // null = echo the min bound
    Long integerMin;
    Long integerMax;
    int generateFloatRc = Abi.OK;
    Double floatValue; // null = 0.0
    int generateBytesRc = Abi.OK;
    byte[] bytesValue = {1, 2};
    Long bytesMinSize;
    Long bytesMaxSize;
    int generateStringRc = Abi.OK;
    String stringValue = "s";
    int generateDateRc = Abi.OK;
    int generateTimeRc = Abi.OK;
    int generateDatetimeRc = Abi.OK;
    int generateUuidRc = Abi.OK;
    byte[] uuidBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    int generateIpv4Rc = Abi.OK;
    byte[] ipv4Bytes = {127, 0, 0, 1};
    int generateIpv6Rc = Abi.OK;
    byte[] ipv6Bytes = new byte[16];

    int stringGeneratorTextRc = Abi.OK;
    int stringGeneratorRegexRc = Abi.OK;
    int stringGeneratorEmailRc = Abi.OK;
    int stringGeneratorUrlRc = Abi.OK;
    int stringGeneratorDomainRc = Abi.OK;
    // Captured text-generator configuration, for builder-behaviour assertions.
    Long textMinSize;
    Long textMaxSize;
    Long textMinCodepoint;
    Long textMaxCodepoint;
    List<String> textCategories;
    List<String> textExcludeCategories;
    String textIncludeCharacters;
    String textExcludeCharacters;
    Long domainMaxLength;

    int startSpanRc = Abi.OK;
    int stopSpanRc = Abi.OK;
    final List<Long> startedSpans = new ArrayList<>();
    int newCollectionRc = Abi.OK;
    long collectionId = 7;
    Long collectionMinSize;
    Long collectionMaxSize;
    int collectionMoreRc = Abi.OK;
    boolean[] moreSequence = {false};
    private int moreIndex;
    int collectionRejectRc = Abi.OK;
    int targetRc = Abi.OK;

    int newPoolRc = Abi.OK;
    long poolId = 3;
    int poolAddRc = Abi.OK;
    private long nextVariableId;
    int poolGenerateRc = Abi.OK;
    Long poolGenerateValue; // null = the first added variable id (0)
    int newStateMachineRc = Abi.OK;
    long stateMachineId = 5;
    List<String> stateMachineRules;
    List<String> stateMachineInvariants;
    int stateMachineNextRuleRc = Abi.OK;
    long[] ruleSequence = {Abi.STATE_MACHINE_DONE};
    private int ruleIndex;

    @Override
    public long settingsNew() {
        return SETTINGS;
    }

    @Override
    public void settingsFree(long s) {
        settingsFreed = true;
    }

    @Override
    public void settingsMode(long s, int mode) {
        modeCode = mode;
    }

    @Override
    public void settingsBackend(long s, int backend) {
        backendCode = backend;
    }

    @Override
    public void settingsTestCases(long s, long n) {
        testCases = n;
    }

    @Override
    public void settingsVerbosity(long s, int v) {}

    @Override
    public void settingsSeed(long s, long seed, boolean hasSeed) {}

    @Override
    public void settingsDerandomize(long s, boolean derandomize) {
        this.derandomize = derandomize;
    }

    @Override
    public void settingsReportMultipleFailures(long s, boolean yes) {}

    @Override
    public void settingsDatabase(long s, String path) {
        databasePath = path;
    }

    @Override
    public void settingsDatabaseKey(long s, String key) {
        databaseKey = key;
    }

    @Override
    public void settingsPhases(long s, int mask) {
        phasesMask = mask;
    }

    @Override
    public void settingsSuppressHealthCheck(long s, int mask) {
        suppressMask = mask;
    }

    @Override
    public long runStart(long settings, Consumer<String> output) {
        if (runStartFails) {
            throw new HegelException("hegel_run_start failed: " + lastError);
        }
        this.output = output;
        return RUN;
    }

    @Override
    public long nextTestCase(long run) {
        if (nextTestCaseFails) {
            throw new HegelException("hegel_next_test_case failed: " + lastError);
        }
        if (casesServed >= caseCount) {
            return 0;
        }
        casesServed++;
        return TC;
    }

    @Override
    public long runResult(long run) {
        return RESULT;
    }

    @Override
    public void runResultFree(long result) {
        runResultFreed = true;
    }

    @Override
    public void runFree(long run) {
        runFreed = true;
    }

    @Override
    public int testCaseFromBlob(long settings, String blob, Consumer<String> output, long[] out) {
        if (fromBlobRc == Abi.OK) {
            replayedBlobs.add(blob);
            out[0] = TC;
        }
        return fromBlobRc;
    }

    @Override
    public void testCaseFree(long tc) {
        freedTestCases++;
    }

    @Override
    public int generateBoolean(long tc, double p, boolean[] out) {
        if (generateBooleanRc == Abi.OK) {
            out[0] = booleanValue;
        }
        return generateBooleanRc;
    }

    @Override
    public int generateInteger(long tc, long min, long max, long[] out) {
        if (generateIntegerRc == Abi.OK) {
            integerMin = min;
            integerMax = max;
            out[0] = integerValue == null ? min : integerValue;
        }
        return generateIntegerRc;
    }

    @Override
    public int generateFloat(
            long tc,
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude,
            double[] out) {
        if (generateFloatRc == Abi.OK) {
            out[0] = floatValue == null ? 0.0 : floatValue;
        }
        return generateFloatRc;
    }

    @Override
    public int generateBytes(long tc, long minSize, long maxSize, byte[][] out) {
        if (generateBytesRc == Abi.OK) {
            bytesMinSize = minSize;
            bytesMaxSize = maxSize;
            out[0] = bytesValue;
        }
        return generateBytesRc;
    }

    @Override
    public int generateString(long tc, long generator, String[] out) {
        if (generateStringRc == Abi.OK) {
            out[0] = stringValue;
        }
        return generateStringRc;
    }

    @Override
    public int generateDate(long tc, LocalDate min, LocalDate max, LocalDate[] out) {
        if (generateDateRc == Abi.OK) {
            out[0] = min;
        }
        return generateDateRc;
    }

    @Override
    public int generateTime(long tc, LocalTime min, LocalTime max, LocalTime[] out) {
        if (generateTimeRc == Abi.OK) {
            out[0] = min;
        }
        return generateTimeRc;
    }

    @Override
    public int generateDatetime(long tc, LocalDateTime min, LocalDateTime max, LocalDateTime[] out) {
        if (generateDatetimeRc == Abi.OK) {
            out[0] = min;
        }
        return generateDatetimeRc;
    }

    @Override
    public int generateUuid(long tc, int version, boolean hasVersion, byte[] out16) {
        if (generateUuidRc == Abi.OK) {
            System.arraycopy(uuidBytes, 0, out16, 0, 16);
        }
        return generateUuidRc;
    }

    @Override
    public int generateIpv4(long tc, byte[] out4) {
        if (generateIpv4Rc == Abi.OK) {
            System.arraycopy(ipv4Bytes, 0, out4, 0, 4);
        }
        return generateIpv4Rc;
    }

    @Override
    public int generateIpv6(long tc, byte[] out16) {
        if (generateIpv6Rc == Abi.OK) {
            System.arraycopy(ipv6Bytes, 0, out16, 0, 16);
        }
        return generateIpv6Rc;
    }

    @Override
    public int stringGeneratorText(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters,
            long[] out) {
        if (stringGeneratorTextRc == Abi.OK) {
            textMinSize = minSize;
            textMaxSize = maxSize;
            textMinCodepoint = minCodepoint;
            textMaxCodepoint = maxCodepoint;
            textCategories = categories;
            textExcludeCategories = excludeCategories;
            textIncludeCharacters = includeCharacters;
            textExcludeCharacters = excludeCharacters;
            out[0] = STRING_GEN;
        }
        return stringGeneratorTextRc;
    }

    @Override
    public int stringGeneratorRegex(String pattern, boolean fullmatch, long alphabet, long[] out) {
        if (stringGeneratorRegexRc == Abi.OK) {
            out[0] = STRING_GEN;
        }
        return stringGeneratorRegexRc;
    }

    @Override
    public int stringGeneratorEmail(long[] out) {
        if (stringGeneratorEmailRc == Abi.OK) {
            out[0] = STRING_GEN;
        }
        return stringGeneratorEmailRc;
    }

    @Override
    public int stringGeneratorUrl(long[] out) {
        if (stringGeneratorUrlRc == Abi.OK) {
            out[0] = STRING_GEN;
        }
        return stringGeneratorUrlRc;
    }

    @Override
    public int stringGeneratorDomain(long maxLength, long[] out) {
        if (stringGeneratorDomainRc == Abi.OK) {
            domainMaxLength = maxLength;
            out[0] = STRING_GEN;
        }
        return stringGeneratorDomainRc;
    }

    @Override
    public void stringGeneratorFree(long generator) {
        freedStringGenerators++;
    }

    @Override
    public int startSpan(long tc, long label) {
        if (startSpanRc == Abi.OK) {
            startedSpans.add(label);
        }
        return startSpanRc;
    }

    @Override
    public int stopSpan(long tc, boolean discard) {
        return stopSpanRc;
    }

    @Override
    public int newCollection(long tc, long minSize, long maxSize, long[] outId) {
        if (newCollectionRc == Abi.OK) {
            collectionMinSize = minSize;
            collectionMaxSize = maxSize;
            outId[0] = collectionId;
        }
        return newCollectionRc;
    }

    @Override
    public int collectionMore(long tc, long id, boolean[] outMore) {
        if (collectionMoreRc == Abi.OK) {
            outMore[0] = moreIndex < moreSequence.length && moreSequence[moreIndex++];
        }
        return collectionMoreRc;
    }

    @Override
    public int collectionReject(long tc, long id, String why) {
        return collectionRejectRc;
    }

    @Override
    public int newPool(long tc, long[] outId) {
        if (newPoolRc == Abi.OK) {
            outId[0] = poolId;
        }
        return newPoolRc;
    }

    @Override
    public int poolAdd(long tc, long poolId, long[] outVariableId) {
        if (poolAddRc == Abi.OK) {
            outVariableId[0] = nextVariableId++;
        }
        return poolAddRc;
    }

    @Override
    public int poolGenerate(long tc, long poolId, boolean consume, long[] outVariableId) {
        if (poolGenerateRc == Abi.OK) {
            outVariableId[0] = poolGenerateValue == null ? 0 : poolGenerateValue;
        }
        return poolGenerateRc;
    }

    @Override
    public int newStateMachine(long tc, List<String> ruleNames, List<String> invariantNames, long[] outId) {
        if (newStateMachineRc == Abi.OK) {
            stateMachineRules = ruleNames;
            stateMachineInvariants = invariantNames;
            outId[0] = stateMachineId;
        }
        return newStateMachineRc;
    }

    @Override
    public int stateMachineNextRule(long tc, long stateMachineId, long[] outRuleIndex) {
        if (stateMachineNextRuleRc == Abi.OK) {
            outRuleIndex[0] = ruleIndex < ruleSequence.length ? ruleSequence[ruleIndex++] : Abi.STATE_MACHINE_DONE;
        }
        return stateMachineNextRuleRc;
    }

    @Override
    public int target(long tc, double value, String label) {
        return targetRc;
    }

    @Override
    public int markComplete(long tc, int status, String origin) {
        markedStatuses.add(status);
        markedOrigins.add(origin);
        return markCompleteRc;
    }

    @Override
    public int runResultStatus(long result) {
        return runStatus;
    }

    @Override
    public String runResultError(long result) {
        return runError;
    }

    @Override
    public long runResultFailureCount(long result) {
        return failureBlobs.size();
    }

    @Override
    public String failureBlob(long result, long index) {
        return failureBlobs.get((int) index);
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
