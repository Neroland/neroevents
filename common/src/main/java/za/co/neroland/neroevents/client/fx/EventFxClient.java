package za.co.neroland.neroevents.client.fx;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.HudSettings;
import za.co.neroland.neroevents.fx.EventFx;
import za.co.neroland.neroevents.fx.EventParticles;
import za.co.neroland.neroevents.fx.EventParticles.Kind;
import za.co.neroland.neroevents.fx.TrailBudget;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;
import za.co.neroland.neroevents.payload.meteor.ImpactFlashPayload;
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;

/**
 * Client only. The payload effects behind {@link EventFx}: meteor trails (smoke and embers, off beyond 96 blocks,
 * every second meteor at half rate once more than 20 are in view), debris smoke, the meteor impact (flash when
 * the player's <em>Impact flash</em> switch is on, ember cone, ground dust), and the supply pod (descent loop,
 * landing dust, beacon ring, reward sparks while open; particles within 48 blocks). Rates and caps are
 * {@link TrailBudget}'s. Each loader installs it from its client setup with {@link #install()}.
 *
 * <p>If the particle providers are not registered on this client, vanilla particles stand in, so a wiring gap
 * degrades the look instead of hiding the effect.
 */
public final class EventFxClient implements EventFx.Sink {

    private static final EventFxClient INSTANCE = new EventFxClient();

    private final Map<Entity, PodDescendSound> descending = new WeakHashMap<>();
    private boolean fallback;

    private EventFxClient() {
    }

    /** Installs the client effects (each loader's client setup). */
    public static void install() {
        EventFx.install(INSTANCE);
    }

    @Override
    public void meteorTick(MeteorEntity meteor) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(meteor.level() instanceof ClientLevel level)) {
            return;
        }
        double d2 = meteor.distanceToSqr(player);
        boolean half = TrailBudget.halfRate(RenderBudget.meteorsLastFrame(), meteor.getId());
        int smoke = TrailBudget.smoke(half, d2);
        int embers = TrailBudget.embers(half, d2, meteor.tickCount);
        if (smoke + embers == 0) {
            return;
        }
        RandomSource random = level.getRandom();
        double mx = meteor.getX() - meteor.xo;
        double my = meteor.getY() - meteor.yo;
        double mz = meteor.getZ() - meteor.zo;
        double cy = meteor.getY() + 0.6D;
        for (int i = 0; i < smoke; i++) {
            double back = random.nextDouble();
            spawn(mc, level, Kind.METEOR_SMOKE, meteor.getX() - mx * back + jitter(random, 0.3D),
                    cy - my * back + jitter(random, 0.3D), meteor.getZ() - mz * back + jitter(random, 0.3D),
                    jitter(random, 0.02D), 0.01D, jitter(random, 0.02D));
        }
        for (int i = 0; i < embers; i++) {
            double back = random.nextDouble() * 0.5D;
            spawn(mc, level, Kind.METEOR_EMBER, meteor.getX() - mx * back + jitter(random, 0.4D),
                    cy - my * back + jitter(random, 0.4D), meteor.getZ() - mz * back + jitter(random, 0.4D),
                    -mx * 0.1D + jitter(random, 0.06D), -my * 0.1D + jitter(random, 0.06D),
                    -mz * 0.1D + jitter(random, 0.06D));
        }
        RenderBudget.particles(smoke + embers);
    }

    @Override
    public void debrisTick(DebrisEntity debris) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(debris.level() instanceof ClientLevel level)) {
            return;
        }
        int smoke = TrailBudget.debrisSmoke(debris.distanceToSqr(player));
        RandomSource random = level.getRandom();
        for (int i = 0; i < smoke; i++) {
            spawn(mc, level, Kind.METEOR_SMOKE, debris.getX() + jitter(random, 0.4D), debris.getY() + 0.3D,
                    debris.getZ() + jitter(random, 0.4D), jitter(random, 0.02D), 0.01D, jitter(random, 0.02D));
        }
        RenderBudget.particles(smoke);
    }

    @Override
    public void podTick(SupplyPodEntity pod) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(pod.level() instanceof ClientLevel level)) {
            return;
        }
        if (!pod.isLanded()) {
            descentLoop(mc, pod);
            return;
        }
        if (!TrailBudget.podInRange(pod.distanceToSqr(player))) {
            return;
        }
        RandomSource random = level.getRandom();
        int sinceLanded = (int) pod.ticksSinceLanded(0.0F);
        int spawned = 0;
        if (sinceLanded == 0) {
            BlockState ground = level.getBlockState(pod.blockPosition().below());
            if (!ground.isAir()) {
                for (int i = 0; i < TrailBudget.LANDING_DUST; i++) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            pod.getX() + jitter(random, 0.8D), pod.getY() + 0.1D, pod.getZ() + jitter(random, 0.8D),
                            jitter(random, 0.15D), 0.05D, jitter(random, 0.15D));
                }
                spawned += TrailBudget.LANDING_DUST;
            }
        }
        if (sinceLanded >= 0 && TrailBudget.beaconRingDue(sinceLanded)) {
            spawn(mc, level, Kind.BEACON_RING, pod.getX(), pod.getY() + 1.6D, pod.getZ(), 0.0D, 0.05D, 0.0D);
            spawned++;
        }
        if (TrailBudget.sparkDue(pod.isOpenForRender(), pod.tickCount)) {
            spawn(mc, level, Kind.REWARD_SPARK, pod.getX() + jitter(random, 0.3D), pod.getY() + 0.8D,
                    pod.getZ() + jitter(random, 0.3D), jitter(random, 0.01D), 0.03D, jitter(random, 0.01D));
            spawned++;
        }
        RenderBudget.particles(spawned);
    }

    private void descentLoop(Minecraft mc, SupplyPodEntity pod) {
        PodDescendSound sound = descending.get(pod);
        if (sound != null && !sound.isStopped()) {
            return;
        }
        PodDescendSound loop = new PodDescendSound(pod);
        descending.put(pod, loop);
        mc.getSoundManager().play(loop);
    }

    @Override
    public void impact(ImpactFlashPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || payload == null) {
            return;
        }
        BlockPos pos = payload.pos();
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 128.0D * 128.0D) {
            return;
        }
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.1D;
        double z = pos.getZ() + 0.5D;
        int spawned = 0;
        if (TrailBudget.impactFlash(payload.soft(), flashSwitchOn())) {
            spawn(mc, level, Kind.IMPACT_FLASH, x, y + 0.5D, z, 0.0D, 0.0D, 0.0D);
            spawned++;
        }
        int embers = TrailBudget.impactEmbers(payload.soft());
        for (int i = 0; i < embers; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double spread = 0.08D + random.nextDouble() * 0.18D;
            spawn(mc, level, Kind.METEOR_EMBER, x, y, z, Math.cos(angle) * spread, 0.25D + random.nextDouble() * 0.25D,
                    Math.sin(angle) * spread);
        }
        spawned += embers;
        BlockState ground = level.getBlockState(pos);
        if (!ground.isAir()) {
            for (int i = 0; i < TrailBudget.IMPACT_DUST; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x + jitter(random, 1.5D), y,
                        z + jitter(random, 1.5D), jitter(random, 0.2D), 0.15D, jitter(random, 0.2D));
            }
            spawned += TrailBudget.IMPACT_DUST;
        }
        RenderBudget.particles(spawned);
    }

    private static boolean flashSwitchOn() {
        try {
            return EventsClient.hudSettings().on(HudSettings.Toggle.IMPACT_FLASH);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static double jitter(RandomSource random, double amount) {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    private void spawn(Minecraft mc, ClientLevel level, Kind kind, double x, double y, double z, double vx, double vy,
            double vz) {
        SimpleParticleType type = typeOf(kind);
        if (type != null && !fallback) {
            Particle made = mc.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
            if (made != null) {
                return;
            }
            fallback = true; // no provider on this client: vanilla stand-ins from now on
            NeroEventsCommon.LOGGER.debug("[NeroEvents] payload particle providers missing; using vanilla particles");
        }
        level.addParticle(fallbackFor(kind), x, y, z, vx, vy, vz);
    }

    private static @Nullable SimpleParticleType typeOf(Kind kind) {
        try {
            return EventParticles.forKind(kind).get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ParticleOptions fallbackFor(Kind kind) {
        return switch (kind) {
            case METEOR_SMOKE -> ParticleTypes.SMOKE;
            case METEOR_EMBER -> ParticleTypes.FLAME;
            case IMPACT_FLASH, BEACON_RING, REWARD_SPARK -> ParticleTypes.END_ROD;
        };
    }

    /** Disconnect: stop tracking loops and retry the real particles next session. */
    public static void reset() {
        INSTANCE.descending.clear();
        INSTANCE.fallback = false;
    }
}
