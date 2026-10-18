package za.co.neroland.neroevents.compat.factions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.war.WarPayloads;

/**
 * The NeroFactions façade: the only way NeroEvents reaches NeroFactions. Registers {@code neroevents:faction_war} in
 * common init slot 5, and only when NeroFactions is loaded and this jar carries its bridge (a build with
 * {@code nerofactions_compat=false} has none, so faction wars never enter the pool). Nothing here imports
 * NeroFactions.
 *
 * <p>The war payload receives a <b>guarded</b> bridge: every call still may throw, but the first failure turns this
 * integration off ({@link #available()} false) and is warned and reported once. Membership answers are used and
 * never logged.
 */
public final class FactionsCompat {

    public static final String MOD_ID = "nerofactions";

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile @Nullable FactionsBridge bridge;
    private static boolean initialised;

    private FactionsCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("faction wars");
            return;
        }
        install(presence, HEALTH.loadBridge(FactionsBridge.class), true);
    }

    private static void install(ModPresence presence, @Nullable FactionsBridge found, boolean fromServices) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("faction wars");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        FactionsBridge guarded = new Guarded(found);
        bridge = guarded;
        try {
            WarPayloads.register(guarded);
            HEALTH.logHooked("faction wars");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether NeroFactions is loaded, its bridge is present and it has not failed this session. */
    public static boolean available() {
        return bridge != null && !HEALTH.failing();
    }

    /** Every faction on this server; empty when absent or failing. */
    public static Set<Identifier> factionIds(MinecraftServer server) {
        FactionsBridge b = available() ? bridge : null;
        if (b == null) {
            return Set.of();
        }
        try {
            return b.factionIds(server);
        } catch (RuntimeException | LinkageError failure) {
            return Set.of();
        }
    }

    /** A faction's display name; empty when absent, unknown or failing. */
    public static Optional<String> displayName(MinecraftServer server, Identifier faction) {
        FactionsBridge b = available() ? bridge : null;
        if (b == null) {
            return Optional.empty();
        }
        try {
            return b.displayName(server, faction);
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        bridge = null;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). */
    public static synchronized void initForTests(ModPresence presence, @Nullable FactionsBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false);
    }

    /** Marks the integration failing on the first throw and rethrows, so callers keep their own fallback. */
    private static final class Guarded implements FactionsBridge {

        private final FactionsBridge delegate;

        Guarded(FactionsBridge delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<Identifier> factionIds(MinecraftServer server) {
            try {
                return delegate.factionIds(server);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }

        @Override
        public Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
            try {
                return delegate.membershipOf(server, player);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }

        @Override
        public Optional<String> displayName(MinecraftServer server, Identifier faction) {
            try {
                return delegate.displayName(server, faction);
            } catch (RuntimeException | LinkageError failure) {
                HEALTH.fail(failure);
                throw failure;
            }
        }
    }
}
