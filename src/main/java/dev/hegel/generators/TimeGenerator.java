package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.time.LocalTime;

/**
 * Generates {@link LocalTime} values (the engine's {@code HH:MM:SS[.ffffff]} output). Always basic
 * (one engine call).
 */
public final class TimeGenerator implements Generator<LocalTime> {
    /** @hidden */
    @Override
    public BasicGenerator<LocalTime> asBasic() {
        return new BasicGenerator<>(
                CBORObject.NewMap().Add("type", "time"), raw -> LocalTime.parse(Cbor.asString(raw)));
    }
}
