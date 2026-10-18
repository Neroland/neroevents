package za.co.neroland.neroevents.compat.nerospace;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.storm.StormPayloads;

/**
 * The Nerospace façade: the only way NeroEvents reaches Nerospace. Registers the storm payload types in common init
 * slot 5, and only when Nerospace is loaded and this jar carries its bridge. Nothing here imports Nerospace.
 *
 * <p>The storm payloads receive a <b>guarded</b> bridge: every call still may throw (callers keep their own
 * fallbacks), but the first failure turns this integration off ({@link #available()} false) and is warned and
 * reported once.
 */
public final class NerospaceCompat {

    public static final String MOD_ID = "nerospace";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile @Nullable NerospaceBridge bridge;
    private static boolean initialised;

    private NerospaceCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("solar and planetary storms");
            return;
        }
        install(presence, HEALTH.loadBridge(NerospaceBridge.class), true);
    }

    private static void install(ModPresence presence, @Nullable NerospaceBridge found, boolean fromServices) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("solar and planetary storms");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        NerospaceBridge guarded = new Guarded(found);
        bridge = guarded;
        try {
            StormPayloads.register(guarded);
            HEALTH.logHooked("solar and planetary storms");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether Nerospace is loaded, its bridge is present and it has not failed this session. */
    public static boolean available() {
        return bridge != null && !HEALTH.failing();
    }

    /** The planet of a dimension; empty when absent, unknown or failing. */
    public static Optional<Identifier> planetId(Identifier dimension) {
        NerospaceBridge b = active();
        if (b == null) {
            return Optional.empty();
        }
        try {
            return b.planetId(dimension);
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    /** The atmosphere name at a position; empty when absent, unloaded or failing. */
    public static Optional<String> atmosphereAt(ServerLevel level, BlockPos pos) {
        NerospaceBridge b = active();
        if (b == null) {
            return Optional.empty();
        }
        try {
            return b.atmosphereAt(level, pos);
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    private static @Nullable NerospaceBridge active() {
        return available() ? bridge : null;
    }

    /** Tests only: forgets the bridge, the once-flag and the failure state. */
    public static synchronized void resetForTests() {
        bridge = null;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). */
    public static synchronized void initForTests(ModPresence presence, @Nullable NerospaceBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false);
    }

    /** Marks the integration failing on the first throw and rethrows, so callers keep their own fallback. */
    private static final class Guarded implements NerospaceBridge {

        private final NerospaceBridge delegate;

        Guarded(NerospaceBridge delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Identifier> planetId(Identifier dimension) {
            try {
                return delegate.planetId(dimension);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }

        @Override
        public Optional<String> atmosphereAt(ServerLevel level, BlockPos pos) {
            try {
                return delegate.atmosphereAt(level, pos);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }

        @Override
        public List<BlockPos> stationPositions(MinecraftServer server) {
            try {
                return delegate.stationPositions(server);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }
    }
}
