package za.co.neroland.neroevents.fx;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The five payload particle types: {@code neroevents:meteor_smoke} and {@code meteor_ember} (meteor and debris
 * trails, impact cone), {@code impact_flash} (the meteor impact flash), {@code beacon_ring} (a landed supply pod's
 * beacon) and {@code reward_spark} (an open pod). Registered through Neroland Core's {@link RegistrationProvider}
 * in common init slot 3, next to the storm particles; sprites are listed in
 * {@code assets/neroevents/particles/<id>.json} and the client providers are {@code client.fx.EventParticle}.
 */
public final class EventParticles {

    /** Which behaviour a payload particle has (one client particle class serves all five). */
    public enum Kind {
        METEOR_SMOKE("meteor_smoke"),
        METEOR_EMBER("meteor_ember"),
        IMPACT_FLASH("impact_flash"),
        BEACON_RING("beacon_ring"),
        REWARD_SPARK("reward_spark");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        /** The registry path under {@code neroevents:}. */
        public String id() {
            return id;
        }
    }

    public static final RegistrationProvider<ParticleType<?>> PARTICLES =
            RegistrationProvider.get(Registries.PARTICLE_TYPE, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<SimpleParticleType> METEOR_SMOKE =
            PARTICLES.register(Kind.METEOR_SMOKE.id(), key -> simple());
    public static final RegistryEntry<SimpleParticleType> METEOR_EMBER =
            PARTICLES.register(Kind.METEOR_EMBER.id(), key -> simple());
    public static final RegistryEntry<SimpleParticleType> IMPACT_FLASH =
            PARTICLES.register(Kind.IMPACT_FLASH.id(), key -> simple());
    public static final RegistryEntry<SimpleParticleType> BEACON_RING =
            PARTICLES.register(Kind.BEACON_RING.id(), key -> simple());
    public static final RegistryEntry<SimpleParticleType> REWARD_SPARK =
            PARTICLES.register(Kind.REWARD_SPARK.id(), key -> simple());

    private EventParticles() {
    }

    private static SimpleParticleType simple() {
        return new EventParticleType();
    }

    /** {@code SimpleParticleType}'s constructor is protected on vanilla (Fabric); a subclass reaches it everywhere. */
    private static final class EventParticleType extends SimpleParticleType {
        EventParticleType() {
            super(false);
        }
    }

    /** Classload-forcing no-op (common init slot 3). */
    public static void init() {
    }

    /** The registered type for a kind. */
    public static RegistryEntry<SimpleParticleType> forKind(Kind kind) {
        return switch (kind) {
            case METEOR_SMOKE -> METEOR_SMOKE;
            case METEOR_EMBER -> METEOR_EMBER;
            case IMPACT_FLASH -> IMPACT_FLASH;
            case BEACON_RING -> BEACON_RING;
            case REWARD_SPARK -> REWARD_SPARK;
        };
    }

    /** All five, in {@link Kind} order. */
    public static List<RegistryEntry<SimpleParticleType>> all() {
        return List.of(METEOR_SMOKE, METEOR_EMBER, IMPACT_FLASH, BEACON_RING, REWARD_SPARK);
    }
}
