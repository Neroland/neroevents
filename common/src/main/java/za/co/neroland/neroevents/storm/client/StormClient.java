package za.co.neroland.neroevents.storm.client;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.storm.StormParticles;
import za.co.neroland.neroevents.storm.StormVariant;

/**
 * Client only. The per-tick storm presentation in the player's current level: advances the fades in
 * {@link ClientStormState}, keeps the ambient loop playing, spawns the storm's particles within 24 blocks of
 * the player (only where the sky is open, scaled by strength, inside the shared 400-particle limit), draws
 * the extras (dust kicked off the ground, ion dry lightning, the solar rumble) and the {@code ion_spark}s on
 * energy blocks in {@code #neroevents:energy_visuals} during a draining storm. Sky tint and fog are
 * {@link StormRenderHooks}' job. Called once per client level tick by the common client mixin.
 *
 * <p>If the storm particle providers are not registered on this client, a vanilla particle stands in, so a
 * wiring gap degrades the look instead of hiding the storm.
 */
public final class StormClient {

    /** Particles per tick at full strength, by variant. */
    static float rate(StormVariant variant) {
        return switch (variant) {
            case SOLAR -> 2.0F;
            case DUST -> 6.0F;
            case ION -> 3.0F;
            case CRYO -> 8.0F;
        };
    }

    private static final int RADIUS = 24;
    private static final int ENERGY_SCAN_TICKS = 20;
    private static final TagKey<Block> ENERGY_VISUALS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "energy_visuals"));

    private static @Nullable StormLoopSound loop;
    private static @Nullable StormLoopSound layer;
    private static float carry;
    private static int ticks;
    private static int nextFlash = 60;
    private static int nextRumble = 300;
    private static boolean fallbackParticles;

    private StormClient() {
    }

    /** Whether the "sky/fog layers not installed" line was logged this session. */
    private static boolean layersMissingLogged;

    /** One client tick of {@code level}. */
    public static void tick(ClientLevel level) {
        ClientStormState.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.level() != level) {
            return;
        }
        Identifier dimension = level.dimension().identifier();
        Optional<ClientStormState.View> maybe = ClientStormState.view(dimension);
        if (maybe.isEmpty()) {
            carry = 0.0F;
            return;
        }
        ClientStormState.View view = maybe.get();
        if (!StormRenderHooks.installed() && !layersMissingLogged) {
            layersMissingLogged = true;
            NeroEventsCommon.LOGGER.debug("[NeroEvents] storm sky/fog layers not installed on this Minecraft version");
        }
        ticks++;
        try {
            sounds(mc, dimension, view);
            particles(mc, level, player, view);
            extras(level, player, view);
        } catch (RuntimeException e) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] storm presentation tick failed", e);
        }
    }

    private static void sounds(Minecraft mc, Identifier dimension, ClientStormState.View view) {
        if (loop == null || loop.isStopped() || loop.variant() != view.variant() || !loop.dimension().equals(dimension)) {
            loop = new StormLoopSound(SoundEvent.createVariableRangeEvent(StormLoopSound.loopId(view.variant())),
                    dimension, view.variant(), 1.0F);
            mc.getSoundManager().play(loop);
        }
        if (view.variant() == StormVariant.CRYO) {
            if (layer == null || layer.isStopped() || !layer.dimension().equals(dimension)) {
                layer = new StormLoopSound(SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "storm.cryo.loop_layer")),
                        dimension, StormVariant.CRYO, 0.6F);
                mc.getSoundManager().play(layer);
            }
        }
    }

    private static void particles(Minecraft mc, ClientLevel level, LocalPlayer player, ClientStormState.View view) {
        carry += rate(view.variant()) * view.strength() * (fallbackParticles ? 0.5F : 1.0F);
        int count = (int) carry;
        carry -= count;
        if (count <= 0) {
            return;
        }
        RandomSource random = level.getRandom();
        double yaw = Math.toRadians(view.windYaw());
        double windX = -Math.sin(yaw);
        double windZ = Math.cos(yaw);
        SimpleParticleType type = stormType(view.variant());
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() * 2.0D - 1.0D) * RADIUS;
            double z = player.getZ() + (random.nextDouble() * 2.0D - 1.0D) * RADIUS;
            double y = player.getY() + random.nextDouble() * 16.0D - 4.0D;
            if (!level.canSeeSky(BlockPos.containing(x, y, z))) {
                continue;
            }
            double vx = 0.0D;
            double vy = 0.0D;
            double vz = 0.0D;
            switch (view.variant()) {
                case SOLAR -> vy = 0.01D;
                case DUST -> {
                    vx = windX * 0.25D;
                    vz = windZ * 0.25D;
                    vy = (random.nextDouble() - 0.5D) * 0.02D;
                }
                case CRYO -> {
                    vx = windX * 0.04D;
                    vz = windZ * 0.04D;
                    vy = -0.08D;
                }
                case ION -> {
                }
            }
            spawn(mc, level, type, view.variant(), x, y, z, vx, vy, vz);
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level, @Nullable SimpleParticleType type, StormVariant variant,
            double x, double y, double z, double vx, double vy, double vz) {
        if (type != null && !fallbackParticles) {
            Particle made = mc.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
            if (made != null) {
                return;
            }
            fallbackParticles = true; // no provider on this client: use the vanilla stand-ins from now on
            NeroEventsCommon.LOGGER.debug("[NeroEvents] storm particle providers missing; using vanilla particles");
        }
        level.addParticle(fallback(variant), x, y, z, vx, vy, vz);
    }

    private static ParticleOptions fallback(StormVariant variant) {
        return switch (variant) {
            case SOLAR -> ParticleTypes.END_ROD;
            case DUST -> ParticleTypes.WHITE_ASH;
            case ION -> ParticleTypes.ELECTRIC_SPARK;
            case CRYO -> ParticleTypes.SNOWFLAKE;
        };
    }

    private static @Nullable SimpleParticleType stormType(StormVariant variant) {
        try {
            return StormParticles.forVariant(variant).get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void extras(ClientLevel level, LocalPlayer player, ClientStormState.View view) {
        RandomSource random = level.getRandom();
        switch (view.variant()) {
            case DUST -> {
                if (random.nextFloat() < view.strength()) {
                    int x = (int) Math.floor(player.getX()) + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                    int z = (int) Math.floor(player.getZ()) + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    BlockState ground = level.getBlockState(new BlockPos(x, top - 1, z));
                    if (!ground.isAir()) {
                        double yaw = Math.toRadians(view.windYaw());
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x + 0.5D, top + 0.1D,
                                z + 0.5D, -Math.sin(yaw) * 0.2D, 0.05D, Math.cos(yaw) * 0.2D);
                    }
                }
            }
            case ION -> {
                if (--nextFlash <= 0) {
                    nextFlash = 40 + random.nextInt(41);
                    level.setSkyFlashTime(2);
                    playLocal(level, player, "storm.ion.crack", 0.4F + 0.6F * view.loopVolume());
                }
            }
            case SOLAR -> {
                if (--nextRumble <= 0) {
                    nextRumble = 200 + random.nextInt(201);
                    playLocal(level, player, "storm.solar.rumble", 0.3F + 0.7F * view.loopVolume());
                }
            }
            case CRYO -> {
            }
        }
        if (view.variant().drains() && ticks % ENERGY_SCAN_TICKS == 0) {
            energySparks(level, player, view);
        }
    }

    private static void energySparks(ClientLevel level, LocalPlayer player, ClientStormState.View view) {
        Minecraft mc = Minecraft.getInstance();
        SimpleParticleType spark = stormType(StormVariant.ION);
        BlockPos origin = player.blockPosition();
        ChunkPos centre = ChunkPos.containing(origin);
        int chunkRadius = (RADIUS >> 4) + 1;
        int budget = 16;
        RandomSource random = level.getRandom();
        for (int dx = -chunkRadius; dx <= chunkRadius && budget > 0; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius && budget > 0; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (budget <= 0) {
                        break;
                    }
                    if (pos.distSqr(origin) > (double) RADIUS * RADIUS || !level.canSeeSky(pos.above())
                            || !level.getBlockState(pos).is(ENERGY_VISUALS)) {
                        continue;
                    }
                    if (random.nextFloat() < view.strength()) {
                        budget--;
                        spawn(mc, level, spark, StormVariant.ION, pos.getX() + random.nextDouble(),
                                pos.getY() + 1.0D + random.nextDouble() * 0.3D, pos.getZ() + random.nextDouble(),
                                0.0D, 0.0D, 0.0D);
                    }
                }
            }
        }
    }

    private static void playLocal(ClientLevel level, LocalPlayer player, String path, float volume) {
        level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path)),
                SoundSource.WEATHER, volume, 1.0F, false);
    }

    /** Disconnect / new level: stop loops, forget the mirror. */
    public static void reset() {
        loop = null;
        layer = null;
        carry = 0.0F;
        fallbackParticles = false;
        ClientStormState.clear();
    }
}
