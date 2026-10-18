package za.co.neroland.neroevents.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.factions.FactionsBridge;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The running-server {@link WarWorld}: the instance's level, the player list and the NeroFactions bridge. Every
 * bridge call is caught ({@code RuntimeException | LinkageError}) and answers a safe default, so a NeroFactions
 * API change degrades a war (fewer eligible factions, neutral players) instead of crashing a tick. Every block
 * this class sets is a position the payload has already cleared with the protection query.
 */
final class ServerWarWorld implements WarWorld {

    private final MinecraftServer server;
    private final @Nullable ServerLevel level;
    private final FactionsBridge bridge;

    ServerWarWorld(ServerPayloadContext ctx, FactionsBridge bridge) {
        this.server = ctx.server();
        this.level = ctx.level();
        this.bridge = bridge;
    }

    private static void failed(Throwable failure) {
        NeroEventsTelemetry.captureHandledException(failure, FactionWarPayload.TELEMETRY_SOURCE, "bridge");
    }

    @Override
    public Set<Identifier> factionIds() {
        try {
            Set<Identifier> ids = bridge.factionIds(server);
            return ids == null ? Set.of() : ids;
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Set.of();
        }
    }

    @Override
    public Set<Identifier> membershipOf(UUID player) {
        try {
            Set<Identifier> ids = bridge.membershipOf(server, player);
            return ids == null ? Set.of() : ids;
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Set.of();
        }
    }

    @Override
    public Optional<String> displayName(Identifier faction) {
        try {
            Optional<String> name = bridge.displayName(server, faction);
            return name == null ? Optional.empty() : name;
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Optional.empty();
        }
    }

    @Override
    public List<UUID> onlinePlayers() {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) {
                out.add(player.getUUID());
            }
        }
        return out;
    }

    @Override
    public OptionalInt surfaceY(int x, int z) {
        ServerLevel lvl = level;
        if (lvl == null || !lvl.hasChunk(x >> 4, z >> 4)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    @Override
    public boolean isAir(BlockPos pos) {
        ServerLevel lvl = level;
        return lvl != null && lvl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && lvl.getBlockState(pos).isAir();
    }

    @Override
    public boolean placeBeacon(BlockPos pos, UUID instanceId, int index, int colourA, int colourB, int captureTicks,
            int captureRadius) {
        ServerLevel lvl = level;
        if (lvl == null) {
            return false;
        }
        // The caller cleared this position with mayAffectAll (G-gate: protection before every setBlock).
        if (!lvl.setBlock(pos, WarBlocks.WAR_BEACON.get().defaultBlockState(), Block.UPDATE_ALL)) {
            return false;
        }
        if (lvl.getBlockEntity(pos) instanceof WarBeaconBlockEntity beacon) {
            beacon.bind(instanceId, index, colourA, colourB, captureTicks, captureRadius);
            return true;
        }
        return false;
    }

    @Override
    public boolean holdsBeacon(BlockPos pos, UUID instanceId) {
        ServerLevel lvl = level;
        if (lvl == null || !lvl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return true; // cannot look without loading the chunk: assume it is still there
        }
        return lvl.getBlockEntity(pos) instanceof WarBeaconBlockEntity beacon && instanceId.equals(beacon.instanceId());
    }

    @Override
    public void removeBeacon(BlockPos pos) {
        ServerLevel lvl = level;
        if (lvl == null || !lvl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        if (lvl.getBlockState(pos).is(WarBlocks.WAR_BEACON.get())) {
            lvl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // our own block only
        }
    }

    @Override
    public List<UUID> playersNear(BlockPos pos, int radius) {
        ServerLevel lvl = level;
        if (lvl == null) {
            return List.of();
        }
        AABB box = new AABB(pos).inflate(radius, radius, radius);
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : lvl.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isSpectator())) {
            out.add(player.getUUID());
        }
        return out;
    }

    @Override
    public void syncBeacon(BlockPos pos, int owner, int progressTicks, boolean contested, int progressSide) {
        ServerLevel lvl = level;
        if (lvl == null || !lvl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        if (lvl.getBlockEntity(pos) instanceof WarBeaconBlockEntity beacon) {
            beacon.sync(owner, progressTicks, contested, progressSide);
            boolean beingTaken = progressTicks > 0 && progressSide != WarBeaconBlockEntity.NONE && progressSide != owner;
            if (contested || beingTaken) {
                // The capture tick, once per scoring pass while the beacon is contested or being taken; its pitch
                // rises with the capture.
                za.co.neroland.neroevents.payload.PayloadSounds.play(lvl, pos.getX() + 0.5D, pos.getY() + 1.0D,
                        pos.getZ() + 0.5D, za.co.neroland.neroevents.payload.PayloadSounds.WAR_BEACON_CAPTURE_TICK,
                        32.0F, za.co.neroland.neroevents.payload.PayloadSounds.captureTickPitch(progressTicks,
                                beacon.captureTicks()));
            }
        }
    }

    @Override
    public void playCaptured(BlockPos pos) {
        ServerLevel lvl = level;
        if (lvl != null) {
            // The pitch lives in sounds.json; 64 blocks per the beacon's sound range.
            za.co.neroland.neroevents.payload.PayloadSounds.play(lvl, pos.getX() + 0.5D, pos.getY() + 1.0D,
                    pos.getZ() + 0.5D, za.co.neroland.neroevents.payload.PayloadSounds.WAR_BEACON_CAPTURED, 64.0F);
        }
    }
}
