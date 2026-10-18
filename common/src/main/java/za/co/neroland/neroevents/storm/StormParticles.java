package za.co.neroland.neroevents.storm;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The four storm particle types ({@code neroevents:solar_flare}, {@code storm_dust}, {@code ion_spark},
 * {@code cryo_flake}), registered through Neroland Core's {@link RegistrationProvider} in common init
 * slot 3 whether or not Nerospace is present (an unused particle type costs nothing). Their sprites are
 * listed in {@code assets/neroevents/particles/<id>.json}; the client providers are
 * {@code storm.client.StormParticle}.
 */
public final class StormParticles {

    public static final RegistrationProvider<ParticleType<?>> PARTICLES =
            RegistrationProvider.get(Registries.PARTICLE_TYPE, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<SimpleParticleType> SOLAR_FLARE =
            PARTICLES.register("solar_flare", key -> simple());
    public static final RegistryEntry<SimpleParticleType> STORM_DUST =
            PARTICLES.register("storm_dust", key -> simple());
    public static final RegistryEntry<SimpleParticleType> ION_SPARK =
            PARTICLES.register("ion_spark", key -> simple());
    public static final RegistryEntry<SimpleParticleType> CRYO_FLAKE =
            PARTICLES.register("cryo_flake", key -> simple());

    private StormParticles() {
    }

    private static SimpleParticleType simple() {
        return new StormParticleType();
    }

    /** {@code SimpleParticleType}'s constructor is protected on vanilla (Fabric); a subclass reaches it everywhere. */
    private static final class StormParticleType extends SimpleParticleType {
        StormParticleType() {
            super(false);
        }
    }

    /** Classload-forcing no-op (common init slot 3). */
    public static void init() {
    }

    /** The particle a variant's storm draws. */
    public static RegistryEntry<SimpleParticleType> forVariant(StormVariant variant) {
        return switch (variant) {
            case SOLAR -> SOLAR_FLARE;
            case DUST -> STORM_DUST;
            case ION -> ION_SPARK;
            case CRYO -> CRYO_FLAKE;
        };
    }

    /** All four, in variant order. */
    public static List<RegistryEntry<SimpleParticleType>> all() {
        return List.of(SOLAR_FLARE, STORM_DUST, ION_SPARK, CRYO_FLAKE);
    }
}
