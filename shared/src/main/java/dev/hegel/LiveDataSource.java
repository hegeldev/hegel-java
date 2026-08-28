package dev.hegel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A {@link DataSource} backed by the real engine for one in-flight test case.
 *
 * <p>Once the engine returns {@code STOP_TEST} (or an assumption is rejected) the source is marked
 * {@code aborted}: value-producing primitives short-circuit by re-raising {@link StopTest} without
 * touching libhegel, and {@link #stopSpan} becomes a no-op so span-closing {@code finally} blocks
 * during unwinding do not call into a case that is already being torn down.
 */
final class LiveDataSource implements DataSource {
    private final Libhegel lib;
    private final long tc;
    private boolean aborted;

    LiveDataSource(Libhegel lib, long tc) {
        this.lib = lib;
        this.tc = tc;
    }

    boolean isAborted() {
        return aborted;
    }

    private void translate(int rc, String op) {
        switch (rc) {
            case Abi.OK:
                return;
            case Abi.E_STOP_TEST:
                aborted = true;
                throw new StopTest();
            case Abi.E_ASSUME:
                aborted = true;
                throw new AssumeRejected();
            case Abi.E_INVALID_ARG:
                throw new IllegalArgumentException(nullToEmpty(lib.lastErrorMessage()));
            default:
                throw new HegelException(
                        "hegel_" + op + " failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void checkLive() {
        if (aborted) {
            throw new StopTest();
        }
    }

    @Override
    public boolean generateBoolean(double p) {
        checkLive();
        boolean[] out = new boolean[1];
        translate(lib.generateBoolean(tc, p, out), "generate_boolean");
        return out[0];
    }

    @Override
    public long generateInteger(long min, long max) {
        checkLive();
        long[] out = new long[1];
        translate(lib.generateInteger(tc, min, max, out), "generate_integer");
        return out[0];
    }

    @Override
    public double generateFloat(
            int width,
            double min,
            double max,
            boolean allowNan,
            boolean allowInfinity,
            boolean excludeMin,
            boolean excludeMax,
            double smallestNonzeroMagnitude) {
        checkLive();
        double[] out = new double[1];
        translate(
                lib.generateFloat(
                        tc,
                        width,
                        min,
                        max,
                        allowNan,
                        allowInfinity,
                        excludeMin,
                        excludeMax,
                        smallestNonzeroMagnitude,
                        out),
                "generate_float");
        return out[0];
    }

    @Override
    public byte[] generateBytes(long minSize, long maxSize) {
        checkLive();
        byte[][] out = new byte[1][];
        translate(lib.generateBytes(tc, minSize, maxSize, out), "generate_bytes");
        return out[0];
    }

    @Override
    public String generateString(StringGeneratorHandle generator) {
        checkLive();
        String[] out = new String[1];
        translate(lib.generateString(tc, generator.handle, out), "generate_string");
        return out[0];
    }

    @Override
    public LocalDate generateDate(LocalDate min, LocalDate max) {
        checkLive();
        LocalDate[] out = new LocalDate[1];
        translate(lib.generateDate(tc, min, max, out), "generate_date");
        return out[0];
    }

    @Override
    public LocalTime generateTime(LocalTime min, LocalTime max) {
        checkLive();
        LocalTime[] out = new LocalTime[1];
        translate(lib.generateTime(tc, min, max, out), "generate_time");
        return out[0];
    }

    @Override
    public LocalDateTime generateDatetime(LocalDateTime min, LocalDateTime max) {
        checkLive();
        LocalDateTime[] out = new LocalDateTime[1];
        translate(lib.generateDatetime(tc, min, max, out), "generate_datetime");
        return out[0];
    }

    @Override
    public UUID generateUuid(Integer version) {
        checkLive();
        byte[] bytes = new byte[16];
        translate(lib.generateUuid(tc, version == null ? 0 : version, version != null, bytes), "generate_uuid");
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xffL);
            lsb = (lsb << 8) | (bytes[i + 8] & 0xffL);
        }
        return new UUID(msb, lsb);
    }

    @Override
    public byte[] generateIpv4() {
        checkLive();
        byte[] bytes = new byte[4];
        translate(lib.generateIpv4(tc, bytes), "generate_ipv4");
        return bytes;
    }

    @Override
    public byte[] generateIpv6() {
        checkLive();
        byte[] bytes = new byte[16];
        translate(lib.generateIpv6(tc, bytes), "generate_ipv6");
        return bytes;
    }

    @Override
    public StringGeneratorHandle textGenerator(
            long minSize,
            long maxSize,
            String codec,
            long minCodepoint,
            long maxCodepoint,
            List<String> categories,
            List<String> excludeCategories,
            String includeCharacters,
            String excludeCharacters) {
        checkLive();
        long[] out = new long[1];
        translate(
                lib.stringGeneratorText(
                        minSize,
                        maxSize,
                        codec,
                        minCodepoint,
                        maxCodepoint,
                        categories,
                        excludeCategories,
                        includeCharacters,
                        excludeCharacters,
                        out),
                "string_generator_text");
        return new StringGeneratorHandle(lib, out[0]);
    }

    @Override
    public StringGeneratorHandle regexGenerator(String pattern, boolean fullmatch, StringGeneratorHandle alphabet) {
        checkLive();
        long[] out = new long[1];
        translate(
                lib.stringGeneratorRegex(pattern, fullmatch, alphabet == null ? 0 : alphabet.handle, out),
                "string_generator_regex");
        return new StringGeneratorHandle(lib, out[0]);
    }

    @Override
    public StringGeneratorHandle emailGenerator() {
        checkLive();
        long[] out = new long[1];
        translate(lib.stringGeneratorEmail(out), "string_generator_email");
        return new StringGeneratorHandle(lib, out[0]);
    }

    @Override
    public StringGeneratorHandle urlGenerator() {
        checkLive();
        long[] out = new long[1];
        translate(lib.stringGeneratorUrl(out), "string_generator_url");
        return new StringGeneratorHandle(lib, out[0]);
    }

    @Override
    public StringGeneratorHandle domainGenerator(long maxLength) {
        checkLive();
        long[] out = new long[1];
        translate(lib.stringGeneratorDomain(maxLength, out), "string_generator_domain");
        return new StringGeneratorHandle(lib, out[0]);
    }

    @Override
    public boolean ownsStringGenerator(StringGeneratorHandle generator) {
        return generator.lib == lib;
    }

    @Override
    public void startSpan(long label) {
        checkLive();
        translate(lib.startSpan(tc, label), "start_span");
    }

    @Override
    public void stopSpan(boolean discard) {
        if (aborted) {
            return;
        }
        translate(lib.stopSpan(tc, discard), "stop_span");
    }

    @Override
    public long newCollection(long minSize, long maxSize) {
        checkLive();
        long[] id = new long[1];
        translate(lib.newCollection(tc, minSize, maxSize, id), "new_collection");
        return id[0];
    }

    @Override
    public boolean collectionMore(long id) {
        checkLive();
        boolean[] more = new boolean[1];
        translate(lib.collectionMore(tc, id, more), "collection_more");
        return more[0];
    }

    @Override
    public void collectionReject(long id, String why) {
        checkLive();
        translate(lib.collectionReject(tc, id, why), "collection_reject");
    }

    @Override
    public long newPool() {
        checkLive();
        long[] id = new long[1];
        translate(lib.newPool(tc, id), "new_pool");
        return id[0];
    }

    @Override
    public long poolAdd(long poolId) {
        checkLive();
        long[] id = new long[1];
        translate(lib.poolAdd(tc, poolId, id), "pool_add");
        return id[0];
    }

    @Override
    public long poolGenerate(long poolId, boolean consume) {
        checkLive();
        long[] id = new long[1];
        translate(lib.poolGenerate(tc, poolId, consume, id), "pool_generate");
        return id[0];
    }

    @Override
    public long newStateMachine(List<String> ruleNames, List<String> invariantNames) {
        checkLive();
        long[] id = new long[1];
        translate(lib.newStateMachine(tc, ruleNames, invariantNames, id), "new_state_machine");
        return id[0];
    }

    @Override
    public long stateMachineNextRule(long stateMachineId) {
        checkLive();
        long[] index = new long[1];
        translate(lib.stateMachineNextRule(tc, stateMachineId, index), "state_machine_next_rule");
        return index[0];
    }

    @Override
    public void target(double value, String label) {
        checkLive();
        translate(lib.target(tc, value, label), "target");
    }
}
