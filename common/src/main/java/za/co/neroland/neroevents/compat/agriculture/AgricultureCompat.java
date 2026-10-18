package za.co.neroland.neroevents.compat.agriculture;

import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.storm.ActiveStorms;
import za.co.neroland.neroevents.storm.ActiveStorms.StormStatus;

/**
 * The NeroAgriculture façade. Registers one cycle provider with NeroAgriculture in common init slot 5, and only when
 * NeroAgriculture is loaded and this jar carries its bridge: while a storm is active in a dimension, every farm in
 * that dimension grows by the storm variant's {@code (growth, yield, environment)} triple — greenhouses included,
 * by design. Nothing here imports NeroAgriculture; the modifier reads {@link ActiveStorms} only.
 */
public final class AgricultureCompat {

    public static final String MOD_ID = "neroagriculture";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile boolean hooked;
    private static boolean initialised;

    private AgricultureCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("storm crop modifiers");
            return;
        }
        install(presence, HEALTH.loadBridge(AgricultureBridge.class), true);
    }

    private static void install(ModPresence presence, @Nullable AgricultureBridge found, boolean fromServices) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("storm crop modifiers");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        try {
            found.registerCycleProvider(AgricultureCompat::stormModifier);
            hooked = true;
            HEALTH.logHooked("storm crop modifiers");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether the cycle provider is registered and nothing has failed this session. */
    public static boolean available() {
        return hooked && !HEALTH.failing();
    }

    /**
     * The crop modifier for a dimension at a time: the active storm's agriculture triple when the storm's
     * {@code agriculture_enabled} is set, else {@code null} (identity). Called by NeroAgriculture; never throws.
     */
    public static @Nullable float[] stormModifier(Identifier dimension, Long time) {
        if (dimension == null) {
            return null;
        }
        try {
            Optional<StormStatus> storm = ActiveStorms.active(dimension);
            if (storm.isEmpty() || !storm.get().agricultureEnabled()) {
                return null;
            }
            float[] triple = storm.get().variant().agriculture();
            return triple == null || triple.length < 3 ? null : triple.clone();
        } catch (RuntimeException failure) {
            HEALTH.fail(failure);
            return null;
        }
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        hooked = false;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). */
    public static synchronized void initForTests(ModPresence presence, @Nullable AgricultureBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false);
    }
}
