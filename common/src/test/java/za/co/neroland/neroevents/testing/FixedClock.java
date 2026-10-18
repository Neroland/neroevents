package za.co.neroland.neroevents.testing;

import java.util.function.LongSupplier;

/** A settable wall clock (epoch milliseconds). */
public final class FixedClock implements LongSupplier {

    public long millis;

    public FixedClock(long millis) {
        this.millis = millis;
    }

    @Override
    public long getAsLong() {
        return millis;
    }
}
