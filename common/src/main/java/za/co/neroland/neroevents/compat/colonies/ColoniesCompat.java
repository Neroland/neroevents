package za.co.neroland.neroevents.compat.colonies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.CompatServer;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.protect.ProtectionSources;

/**
 * The NeroColonies façade. Registers the {@link ColonyProtectionSource} (order 400) in common init slot 5, and only
 * when NeroColonies is loaded and this jar carries its bridge. Nothing here imports NeroColonies.
 */
public final class ColoniesCompat {

    public static final String MOD_ID = "nerocolonies";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile @Nullable ColoniesBridge bridge;
    private static boolean initialised;

    private ColoniesCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("colony protection");
            return;
        }
        install(presence, HEALTH.loadBridge(ColoniesBridge.class), true);
    }

    private static void install(ModPresence presence, @Nullable ColoniesBridge found, boolean fromServices) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("colony protection");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        ColoniesBridge guarded = new Guarded(found);
        bridge = guarded;
        registerSources(guarded);
    }

    private static void registerSources(ColoniesBridge guarded) {
        try {
            ProtectionSources.register(ProtectionSources.ORDER_COLONY, new ColonyProtectionSource((dimension, pos) -> {
                ServerLevel level = CompatServer.require().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
                return level == null ? null : guarded.isClaimed(level, pos);
            }));
            HEALTH.logHooked("colony protection");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether NeroColonies is loaded, its bridge is present and it has not failed this session. */
    public static boolean available() {
        return bridge != null && !HEALTH.failing();
    }

    /** Whether a colony claims a position; {@code false} when absent or failing. Not a protection check. */
    public static boolean isClaimed(ServerLevel level, BlockPos pos) {
        ColoniesBridge b = available() ? bridge : null;
        if (b == null) {
            return false;
        }
        try {
            return b.isClaimed(level, pos);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        bridge = null;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). */
    public static synchronized void initForTests(ModPresence presence, @Nullable ColoniesBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false);
    }

    /** Marks the integration failing on the first throw and rethrows, so the protection source fails closed. */
    private static final class Guarded implements ColoniesBridge {

        private final ColoniesBridge delegate;

        Guarded(ColoniesBridge delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isClaimed(ServerLevel level, BlockPos pos) {
            try {
                return delegate.isClaimed(level, pos);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }
    }
}
