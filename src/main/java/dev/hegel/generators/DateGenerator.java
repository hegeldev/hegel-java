package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.time.LocalDate;

/**
 * Generates {@link LocalDate} values (the engine's {@code YYYY-MM-DD} output). Always basic (one
 * engine call).
 */
public final class DateGenerator implements Generator<LocalDate> {
    /** @hidden */
    @Override
    public BasicGenerator<LocalDate> asBasic() {
        return new BasicGenerator<>(
                CBORObject.NewMap().Add("type", "date"), raw -> LocalDate.parse(Cbor.asString(raw)));
    }
}
