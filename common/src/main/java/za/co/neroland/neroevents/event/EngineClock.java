package za.co.neroland.neroevents.event;

/** The engine's time source: overworld game time in production, settable in tests. */
@FunctionalInterface
public interface EngineClock {
    long now();
}
