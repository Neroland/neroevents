package za.co.neroland.neroevents.client.fx;

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

import za.co.neroland.neroevents.fx.EventParticles.Kind;

/**
 * Client only. The five payload particles: {@code meteor_smoke} (grey puff, 30–45 ticks, fading out and
 * swelling), {@code meteor_ember} (hot speck at full bright, 15 ticks, light gravity, cooling to magma),
 * {@code impact_flash} (one bright quad shrinking from 4 blocks to nothing over 6 ticks), {@code beacon_ring}
 * (a reward-gold ring rising off a landed pod) and {@code reward_spark} (an ember-tinted spark drifting up from
 * an open pod). The spawner passes the motion. All five share one cap of {@value #CAP} alive.
 */
public final class EventParticle extends SingleQuadParticle {

    /** Every payload particle counts against this one limit. */
    public static final int CAP = 800;
    public static final ParticleLimit FX_LIMIT = new ParticleLimit(CAP);

    private static final int FULL_BRIGHT = 0x00F000F0;

    private static final int G_LIGHT = 0x8E98A4;
    private static final int TRIM = 0x5B6670;
    private static final int EMBER = 0xFF8C3A;
    private static final int MAGMA = 0xFF6A1A;
    private static final int MAGMA_HOT = 0xFFD27A;
    private static final int REWARD = 0xF4C542;

    private final SpriteSet sprites;
    private final Kind kind;
    private final float size0;

    EventParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
            Kind kind) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.hasPhysics = false;
        switch (kind) {
            case METEOR_SMOKE -> {
                this.lifetime = 30 + this.random.nextInt(16);
                this.quadSize = 0.25F + this.random.nextFloat() * 0.15F;
                tint(G_LIGHT);
                this.setAlpha(0.8F);
            }
            case METEOR_EMBER -> {
                this.lifetime = 15;
                this.quadSize = 0.06F + this.random.nextFloat() * 0.03F;
                this.gravity = 0.02F;
                this.friction = 0.98F;
                tint(EMBER);
            }
            case IMPACT_FLASH -> {
                this.lifetime = 6;
                this.quadSize = 2.0F; // half-extent: a 4-block flash
                this.friction = 0.0F;
                tint(MAGMA_HOT);
            }
            case BEACON_RING -> {
                this.lifetime = 20;
                this.quadSize = 0.3F;
                this.friction = 1.0F;
                tint(REWARD);
            }
            case REWARD_SPARK -> {
                this.lifetime = 20 + this.random.nextInt(10);
                this.quadSize = 0.08F;
                this.friction = 1.0F;
                tint(EMBER);
            }
        }
        this.size0 = this.quadSize;
        this.setSpriteFromAge(sprites);
    }

    private void tint(int rgb) {
        this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
    }

    private void lerpTint(int from, int to, float t) {
        float r = ((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t;
        float g = ((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t;
        float b = (from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t;
        this.setColor(r / 255.0F, g / 255.0F, b / 255.0F);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return Optional.of(FX_LIMIT);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return kind == Kind.METEOR_SMOKE ? super.getLightCoords(partialTick) : FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        this.setSpriteFromAge(sprites);
        float life = this.lifetime <= 0 ? 1.0F : Math.min(1.0F, (float) this.age / this.lifetime);
        switch (kind) {
            case METEOR_SMOKE -> {
                lerpTint(G_LIGHT, TRIM, life);
                this.setAlpha(0.8F * (1.0F - life));
                this.quadSize = size0 * (1.0F + life);
            }
            case METEOR_EMBER -> lerpTint(EMBER, MAGMA, life);
            case IMPACT_FLASH -> this.quadSize = size0 * (1.0F - life);
            case BEACON_RING -> {
                this.quadSize = size0 + 0.9F * life;
                this.setAlpha(1.0F - life);
            }
            case REWARD_SPARK -> this.setAlpha(life > 0.6F ? (1.0F - life) / 0.4F : 1.0F);
        }
    }

    /** The sprite-set provider for one kind's particle type. */
    public static Function<SpriteSet, ParticleProvider<SimpleParticleType>> provider(Kind kind) {
        return sprites -> new Provider(sprites, kind);
    }

    /** Provider for one payload particle type. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, RandomSource random) {
            return new EventParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
