package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.time.ZoneOffset;

/**
 * Generates {@link ZoneOffset} values (fixed UTC offsets) within an inclusive {@code [min, max]}
 * range. Always basic (one engine call).
 *
 * <p>Offsets are drawn at one-second granularity. The default range is the whole legal {@code
 * ZoneOffset} span ({@code -18:00} to {@code +18:00}); narrow it with the fluent {@link
 * #min(ZoneOffset)} / {@link #max(ZoneOffset)} methods.
 */
public final class ZoneOffsetGenerator implements Generator<ZoneOffset> {
    private final int minSeconds;
    private final int maxSeconds;

    public ZoneOffsetGenerator(int minSeconds, int maxSeconds) {
        if (minSeconds > maxSeconds) {
            throw new IllegalArgumentException("zoneOffsets: min (" + minSeconds + "s) > max (" + maxSeconds + "s)");
        }
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public ZoneOffsetGenerator min(ZoneOffset min) {
        return new ZoneOffsetGenerator(min.getTotalSeconds(), maxSeconds);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public ZoneOffsetGenerator max(ZoneOffset max) {
        return new ZoneOffsetGenerator(minSeconds, max.getTotalSeconds());
    }

    /** @hidden */
    @Override
    public BasicGenerator<ZoneOffset> asBasic() {
        CBORObject schema = CBORObject.NewMap()
                .Add("type", "integer")
                .Add("min_value", minSeconds)
                .Add("max_value", maxSeconds);
        return new BasicGenerator<>(schema, raw -> ZoneOffset.ofTotalSeconds(Cbor.asIndex(raw)));
    }
}
