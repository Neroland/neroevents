package za.co.neroland.neroevents.storm.client;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

import za.co.neroland.neroevents.storm.StormVariant;

/**
 * Client only. The four storm particles: {@code solar_flare} drifts slowly upward at full
 * bright and whitens as it ages; {@code storm_dust} blows along the instance's wind; {@code ion_spark}
 * jitters at full bright for 8 ticks; {@code cryo_flake} falls with a sine sway. The spawner passes the
 * motion; all four share one 400-particle limit.
 *
 * <p>Each loader registers {@link #provider(StormVariant)} as the sprite-set provider for the matching
 * {@code StormParticles} type.
 */
public final class StormParticle extends SingleQuadParticle {

    /** Every storm particle counts against this one limit (the client clamps storms to 400 alive). */
    public static final ParticleLimit STORM_LIMIT = new ParticleLimit(400);

    private static final int FULL_BRIGHT = 0x00F000F0;

    private final SpriteSet sprites;
    private final StormVariant variant;
    private final float r0;
    private final float g0;
    private final float b0;
    private final float swayPhase;

    StormParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
            StormVariant variant) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.variant = variant;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = variant == StormVariant.CRYO;
        int tint = variant.skyTint();
        this.r0 = ((tint >> 16) & 0xFF) / 255.0F;
        this.g0 = ((tint >> 8) & 0xFF) / 255.0F;
        this.b0 = (tint & 0xFF) / 255.0F;
        this.setColor(r0, g0, b0);
        this.swayPhase = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        switch (variant) {
            case SOLAR -> {
                this.lifetime = 40 + this.random.nextInt(21);
                this.quadSize = 0.12F + this.random.nextFloat() * 0.06F;
            }
            case DUST -> {
                this.lifetime = 30 + this.random.nextInt(21);
                this.quadSize = 0.10F + this.random.nextFloat() * 0.05F;
            }
            case ION -> {
                this.lifetime = 8;
                this.quadSize = 0.06F + this.random.nextFloat() * 0.03F;
            }
            case CRYO -> {
                this.lifetime = 50 + this.random.nextInt(21);
                this.quadSize = 0.08F + this.random.nextFloat() * 0.04F;
            }
        }
        this.setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return Optional.of(STORM_LIMIT);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return variant == StormVariant.SOLAR || variant == StormVariant.ION ? FULL_BRIGHT
                : super.getLightCoords(partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        this.setSpriteFromAge(sprites);
        float life = this.lifetime <= 0 ? 1.0F : (float) this.age / this.lifetime;
        switch (variant) {
            case SOLAR -> this.setColor(r0 + (1.0F - r0) * life, g0 + (1.0F - g0) * life, b0 + (1.0F - b0) * life);
            case ION -> {
                this.xd = (this.random.nextFloat() - 0.5F) * 0.12F;
                this.yd = (this.random.nextFloat() - 0.5F) * 0.12F;
                this.zd = (this.random.nextFloat() - 0.5F) * 0.12F;
            }
            case CRYO -> {
                float sway = (float) Math.sin(this.age * 0.25F + swayPhase) * 0.02F;
                this.xd += sway * 0.5F;
                this.zd += sway * 0.5F;
                if (this.onGround) {
                    this.remove();
                }
            }
            default -> {
            }
        }
        this.setAlpha(life > 0.75F ? Math.max(0.0F, (1.0F - life) * 4.0F) : 1.0F);
    }

    /** The sprite-set provider for one variant's particle type. */
    public static Function<SpriteSet, ParticleProvider<SimpleParticleType>> provider(StormVariant variant) {
        return sprites -> new Provider(sprites, variant);
    }

    /** Provider for one storm particle type. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final StormVariant variant;

        public Provider(SpriteSet sprites, StormVariant variant) {
            this.sprites = sprites;
            this.variant = variant;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, RandomSource random) {
            return new StormParticle(level, x, y, z, xd, yd, zd, sprites, variant);
        }
    }
}
