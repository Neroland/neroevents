package za.co.neroland.neroevents.compat.creatures;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.compat.CompatHealth;
import za.co.neroland.neroevents.compat.ModPresence;
import za.co.neroland.neroevents.creatures.BossPayload;
import za.co.neroland.neroevents.creatures.CreaturePayloads;

/**
 * The NeroCreatures façade: the only way NeroEvents reaches NeroCreatures. Registers the raid, invasion and boss
 * payload types in common init slot 5, and only when NeroCreatures is loaded and this jar carries its bridge. Also
 * listens on Core's threshold bus for the falling {@code nerocreatures:boss_pressure} crossing (a boss defeated) and
 * asks the boss payload to run its identity check at once in that dimension. Nothing here imports NeroCreatures.
 *
 * <p>The payloads receive a <b>guarded</b> bridge: every call still may throw, but the first failure turns this
 * integration off ({@link #available()} false) and is warned and reported once.
 */
public final class CreaturesCompat {

    public static final String MOD_ID = "nerocreatures";

    /** NeroCreatures' boss-pressure channel (a defeat is its only falling crossing). */
    public static final Identifier BOSS_PRESSURE = Identifier.fromNamespaceAndPath(MOD_ID, "boss_pressure");

    private static final CompatHealth HEALTH = new CompatHealth(MOD_ID);
    private static volatile @Nullable CreaturesBridge bridge;
    private static boolean initialised;
    private static boolean listening;

    private CreaturesCompat() {
    }

    /** Common init slot 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("pirate raids, alien invasions and bosses");
            return;
        }
        install(presence, HEALTH.loadBridge(CreaturesBridge.class), true);
    }

    private static void install(ModPresence presence, @Nullable CreaturesBridge found, boolean fromServices) {
        if (!presence.isLoaded(MOD_ID)) {
            HEALTH.logAbsent("pirate raids, alien invasions and bosses");
            return;
        }
        if (found == null) {
            if (fromServices && !HEALTH.failing()) {
                HEALTH.logNoBridge();
            }
            return;
        }
        CreaturesBridge guarded = new Guarded(found);
        bridge = guarded;
        try {
            CreaturePayloads.register(guarded);
            if (fromServices && !listening) {
                listening = true;
                ThresholdEvents.onCrossing(CreaturesCompat::onCrossing);
            }
            HEALTH.logHooked("pirate raids, alien invasions and bosses");
        } catch (RuntimeException | LinkageError failure) {
            HEALTH.fail(failure);
        }
    }

    /**
     * A falling {@code boss_pressure} crossing — a boss was defeated in the dimension named by the scope (a place,
     * never a person): the boss payload checks its boss on the next tick instead of waiting for its cadence.
     */
    static void onCrossing(ThresholdEvents.ThresholdCrossing crossing) {
        if (crossing == null || crossing.rising() || !BOSS_PRESSURE.equals(crossing.channel()) || !available()) {
            return;
        }
        Identifier dimension = crossing.scope() == null ? null : Identifier.tryParse(crossing.scope());
        if (dimension == null) {
            return;
        }
        try {
            BossPayload.requestCheck(dimension);
        } catch (RuntimeException failure) {
            HEALTH.fail(failure);
        }
    }

    /** Whether NeroCreatures is loaded, its bridge is present and it has not failed this session. */
    public static boolean available() {
        return bridge != null && !HEALTH.failing();
    }

    /** Every summonable boss id; empty when absent or failing. */
    public static Set<Identifier> bossIds() {
        CreaturesBridge b = available() ? bridge : null;
        if (b == null) {
            return Set.of();
        }
        try {
            return b.bossIds();
        } catch (RuntimeException | LinkageError failure) {
            return Set.of();
        }
    }

    /** Tests only. */
    public static synchronized void resetForTests() {
        bridge = null;
        initialised = false;
        HEALTH.reset();
    }

    /** Tests only: initialises against a fake presence and a fake bridge ({@code null} = this jar has none). */
    public static synchronized void initForTests(ModPresence presence, @Nullable CreaturesBridge fake) {
        resetForTests();
        initialised = true;
        install(presence, fake, false);
    }

    /** Marks the integration failing on the first throw and rethrows, so callers keep their own fallback. */
    private static final class Guarded implements CreaturesBridge {

        private final CreaturesBridge delegate;

        Guarded(CreaturesBridge delegate) {
            this.delegate = delegate;
        }

        private static RuntimeException failed(RuntimeException failure) {
            HEALTH.fail(failure);
            return failure;
        }

        private static LinkageError failed(LinkageError failure) {
            HEALTH.fail(failure);
            return failure;
        }

        @Override
        public Set<Identifier> bossIds() {
            try {
                return delegate.bossIds();
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public Optional<SummonedBoss> summon(ServerLevel level, BlockPos pos, Identifier bossId) {
            try {
                return delegate.summon(level, pos, bossId);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public boolean isFightActive(UUID fightId) {
            try {
                return delegate.isFightActive(fightId);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public Optional<Boolean> isDefeated(ServerLevel level, UUID entityUuid) {
            try {
                return delegate.isDefeated(level, entityUuid);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public Object reserveWave(ServerLevel level, int budget) {
            try {
                return delegate.reserveWave(level, budget);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public UUID waveId(Object handle) {
            try {
                return delegate.waveId(handle);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public int placed(Object handle) {
            try {
                return delegate.placed(handle);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos) {
            try {
                return delegate.spawnWithin(handle, entityType, pos);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public int closeWave(Object handle) {
            try {
                return delegate.closeWave(handle);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public int sweepWave(ServerLevel level, UUID waveId) {
            try {
                return delegate.sweepWave(level, waveId);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }

        @Override
        public List<UUID> findWave(ServerLevel level, UUID waveId) {
            try {
                return delegate.findWave(level, waveId);
            } catch (RuntimeException failure) {
                throw failed(failure);
            } catch (LinkageError failure) {
                throw failed(failure);
            }
        }
    }
}
