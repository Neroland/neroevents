package za.co.neroland.neroevents.event.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neroevents.platform.Services;

/**
 * The running-server {@link WorldView}. The only engine class that reads players and levels; it reduces
 * them to counts before anything else sees them. Chunk accessors use {@code getChunkNow} and never load
 * or generate a chunk. An unloaded dimension is simply absent from {@link #loadedDimensions()}.
 */
public final class ServerWorldView implements WorldView {

    private final MinecraftServer server;

    public ServerWorldView(MinecraftServer server) {
        this.server = server;
    }

    private ServerLevel level(Identifier dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    @Override
    public long gameTime() {
        return server.overworld().getGameTime();
    }

    @Override
    public boolean isDedicatedServer() {
        return server.isDedicatedServer();
    }

    @Override
    public int onlinePlayers() {
        return server.getPlayerList().getPlayerCount();
    }

    @Override
    public Map<Identifier, Integer> onlinePlayersPerDimension() {
        Map<Identifier, Integer> out = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            out.merge(player.level().dimension().identifier(), 1, Integer::sum);
        }
        return out;
    }

    @Override
    public List<Identifier> loadedDimensions() {
        List<Identifier> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(level.dimension().identifier());
        }
        return out;
    }

    @Override
    public boolean dimensionInTag(Identifier dimension, Identifier dimensionTypeTag) {
        ServerLevel level = level(dimension);
        return level != null
                && level.dimensionTypeRegistration().is(TagKey.create(Registries.DIMENSION_TYPE, dimensionTypeTag));
    }

    @Override
    public long dayTime(Identifier dimension) {
        ServerLevel level = level(dimension);
        return level == null ? 0L : Math.floorMod(level.getDefaultClockTime(), 24000L);
    }

    @Override
    public int playersWithGateOpen(Identifier gate) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ProgressionGates.isOpen(player, gate)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return Services.PLATFORM.isModLoaded(modId);
    }

    @Override
    public Map<Long, Integer> densityCells(Identifier dimension, int cellBlocks) {
        Map<Long, Integer> out = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().identifier().equals(dimension)) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            out.merge(DensityCells.cellOf(pos.getX(), pos.getZ(), cellBlocks), 1, Integer::sum);
        }
        return out;
    }

    @Override
    public boolean isChunkLoaded(Identifier dimension, int chunkX, int chunkZ) {
        ServerLevel level = level(dimension);
        return level != null && level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    @Override
    public OptionalInt surfaceY(Identifier dimension, int x, int z) {
        ServerLevel level = level(dimension);
        if (level == null) {
            return OptionalInt.empty();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15));
    }

    @Override
    public Optional<BlockPos> spawnPosition(Identifier dimension) {
        LevelData.RespawnData spawn = server.overworld().getRespawnData();
        if (spawn.dimension() == null || !spawn.dimension().identifier().equals(dimension)) {
            return Optional.empty();
        }
        return Optional.of(spawn.pos());
    }

    @Override
    public int spawnProtectionRadius() {
        return server instanceof DedicatedServer dedicated ? Math.max(0, dedicated.spawnProtectionRadius()) : 0;
    }

    @Override
    public Set<String> blockEntityNamespaces(Identifier dimension, int chunkX, int chunkZ) {
        ServerLevel level = level(dimension);
        if (level == null) {
            return Set.of();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (key != null) {
                out.add(key.getNamespace());
            }
        }
        return out;
    }

    @Override
    public int minBuildHeight(Identifier dimension) {
        ServerLevel level = level(dimension);
        return level == null ? -64 : level.getMinY();
    }

    @Override
    public int maxBuildHeight(Identifier dimension) {
        ServerLevel level = level(dimension);
        return level == null ? 319 : level.getMaxY();
    }
}
