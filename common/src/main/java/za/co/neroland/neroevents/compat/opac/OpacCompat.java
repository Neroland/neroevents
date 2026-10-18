package za.co.neroland.neroevents.compat.opac;

import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.CompatServer;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.protect.ProtectionSources;

/**
 * The Open Parties and Claims façade. Registers the {@link OpacProtectionSource} (order 300) in common init slot 5,
 * and only when OPAC is loaded and this jar carries its bridge. Nothing here imports OPAC.
 */
public final class OpacCompat {

    public static final String MOD_ID = "openpartiesandclaims";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile @Nullable OpacBridge bridge;
    private static boolean initialised;

    private OpacCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("claim protection");
            return;
        }
        install(presence, HEALTH.loadBridge(OpacBridge.class), true, CompatServer::require);
    }

    private static void install(ModPresence presence, @Nullable OpacBridge found, boolean fromServices,
            Supplier<MinecraftServer> server) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("claim protection");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        OpacBridge guarded = new Guarded(found);
        bridge = guarded;
        try {
            ProtectionSources.register(ProtectionSources.ORDER_OPAC, new OpacProtectionSource(
                    (dimension, chunkX, chunkZ) -> guarded.isChunkClaimed(server.get(), dimension, chunkX, chunkZ),
                    OpacCompat::marginBlocks));
            HEALTH.logHooked("claim protection");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    private static int marginBlocks() {
        EventEngine engine = EventEngine.current();
        return (engine != null ? engine.settings() : EngineSettings.defaults()).protectedNamespaceMarginBlocks;
    }

    /** Whether OPAC is loaded, its bridge is present and it has not failed this session. */
    public static boolean available() {
        return bridge != null && !HEALTH.failing();
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        bridge = null;
        initialised = false;
        HEALTH.reset();
    }

    /**
     * Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). A plain
     * JVM has no server, so the fake bridge is asked with a {@code null} one.
     */
    public static synchronized void initForTests(ModPresence presence, @Nullable OpacBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false, () -> null);
    }

    /** Marks the integration failing on the first throw and rethrows, so the protection source fails closed. */
    private static final class Guarded implements OpacBridge {

        private final OpacBridge delegate;

        Guarded(OpacBridge delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isChunkClaimed(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ) {
            try {
                return delegate.isChunkClaimed(server, dimension, chunkX, chunkZ);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }
    }
}
