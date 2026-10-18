package za.co.neroland.neroevents.testing;

import za.co.neroland.neroevents.event.EngineClock;

/** A settable engine clock. */
public final class FakeClock implements EngineClock {

    public long now;

    public FakeClock(long start) {
        this.now = start;
    }

    @Override
    public long now() {
        return now;
    }

    public void advance(long ticks) {
        now += ticks;
    }
}
