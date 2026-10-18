package za.co.neroland.neroevents.compat.economy;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.economy.MarketShockPayload;

/**
 * Registers {@code neroevents:market_shock} in common init slot 5 when NeroEconomy is loaded. No bridge of its own:
 * NeroEconomy is already reached through the economy package's single bridge class, and the payload checks
 * {@code EventsEconomy.isActive()} when an instance starts.
 */
public final class EconomyCompat {

    public static final String MOD_ID = "neroeconomy";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile boolean registered;
    private static boolean initialised;

    private EconomyCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        install(presence);
    }

    private static void install(ModPresence presence) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("market shocks");
            return;
        }
        try {
            MarketShockPayload.register();
            registered = true;
            HEALTH.logHooked("market shocks");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether the market-shock payload type is registered. */
    public static boolean available() {
        return registered && !HEALTH.failing();
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        registered = false;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence. */
    public static synchronized void initForTests(ModPresence presence) {
        resetForTests();
        initialised = true;
        install(presence);
    }
}
