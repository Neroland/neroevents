package za.co.neroland.neroevents.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.world.DensityCells;
import za.co.neroland.neroevents.event.world.WorldView;

/**
 * A mutable {@link WorldView} for plain-JVM tests. Like the real view it has no method that returns a
 * player position, name or UUID — players exist only as counts. It counts chunk accessor calls so tests
 * can prove which ones the code under test used.
 */
public final class FakeWorldView implements WorldView {

    public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    public static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    public long gameTime;
    public boolean dedicated = true;
    private final Map<Identifier, Integer> online = new HashMap<>();
    private final List<Identifier> dimensions = new ArrayList<>();
    private final Map<Identifier, Set<Identifier>> tags = new HashMap<>();
    private final Map<Identifier, Long> dayTimes = new HashMap<>();
    private final Map<Identifier, Integer> gates = new HashMap<>();
    private final Set<String> mods = new HashSet<>();
    private final Map<Identifier, Map<Long, Integer>> density = new HashMap<>();
    private final Map<Identifier, Set<Long>> loadedChunks = new HashMap<>();
    private final Set<Identifier> allChunksLoaded = new HashSet<>();
    private final Map<Identifier, Integer> surface = new HashMap<>();
    private final Map<Identifier, BlockPos> spawns = new HashMap<>();
    public int spawnProtectionRadius;
    private final Map<Identifier, Map<Long, Set<String>>> blockEntities = new HashMap<>();

    public int isChunkLoadedCalls;
    public int surfaceYCalls;
    public int blockEntityCalls;
    public int densityCalls;

    public FakeWorldView() {
        dimensions.add(OVERWORLD);
    }

    public FakeWorldView dimension(Identifier dimension) {
        if (!dimensions.contains(dimension)) {
            dimensions.add(dimension);
        }
        return this;
    }

    public FakeWorldView online(Identifier dimension, int players) {
        dimension(dimension);
        online.put(dimension, players);
        return this;
    }

    public FakeWorldView tag(Identifier dimension, Identifier tag) {
        tags.computeIfAbsent(dimension, d -> new HashSet<>()).add(tag);
        return this;
    }

    public FakeWorldView dayTime(Identifier dimension, long time) {
        dayTimes.put(dimension, time);
        return this;
    }

    public FakeWorldView gateHolders(Identifier gate, int holders) {
        gates.put(gate, holders);
        return this;
    }

    public FakeWorldView mod(String modId) {
        mods.add(modId);
        return this;
    }

    /** Puts {@code players} into one density cell (any cell size the caller later asks for). */
    public FakeWorldView denseCell(Identifier dimension, int cellX, int cellZ, int players) {
        density.computeIfAbsent(dimension, d -> new HashMap<>()).put(DensityCells.pack(cellX, cellZ), players);
        return this;
    }

    public FakeWorldView allChunksLoaded(Identifier dimension) {
        allChunksLoaded.add(dimension);
        return this;
    }

    /** Marks chunks {@code [cx0..cx1] × [cz0..cz1]} as loaded. */
    public FakeWorldView loadedChunks(Identifier dimension, int cx0, int cz0, int cx1, int cz1) {
        Set<Long> set = loadedChunks.computeIfAbsent(dimension, d -> new HashSet<>());
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                set.add(DensityCells.pack(x, z));
            }
        }
        return this;
    }

    public FakeWorldView blockEntityNamespace(Identifier dimension, int chunkX, int chunkZ, String namespace) {
        blockEntities.computeIfAbsent(dimension, d -> new HashMap<>())
                .computeIfAbsent(DensityCells.pack(chunkX, chunkZ), k -> new HashSet<>()).add(namespace);
        return this;
    }

    public FakeWorldView surface(Identifier dimension, int y) {
        surface.put(dimension, y);
        return this;
    }

    public FakeWorldView spawn(Identifier dimension, BlockPos pos) {
        spawns.put(dimension, pos);
        return this;
    }

    @Override
    public long gameTime() {
        return gameTime;
    }

    @Override
    public boolean isDedicatedServer() {
        return dedicated;
    }

    @Override
    public int onlinePlayers() {
        int total = 0;
        for (int count : online.values()) {
            total += count;
        }
        return total;
    }

    @Override
    public Map<Identifier, Integer> onlinePlayersPerDimension() {
        return Map.copyOf(online);
    }

    @Override
    public List<Identifier> loadedDimensions() {
        return List.copyOf(dimensions);
    }

    @Override
    public boolean dimensionInTag(Identifier dimension, Identifier dimensionTypeTag) {
        return tags.getOrDefault(dimension, Set.of()).contains(dimensionTypeTag);
    }

    @Override
    public long dayTime(Identifier dimension) {
        return dayTimes.getOrDefault(dimension, 6000L);
    }

    @Override
    public int playersWithGateOpen(Identifier gate) {
        return gates.getOrDefault(gate, 0);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return mods.contains(modId);
    }

    @Override
    public Map<Long, Integer> densityCells(Identifier dimension, int cellBlocks) {
        densityCalls++;
        return Map.copyOf(density.getOrDefault(dimension, Map.of()));
    }

    @Override
    public boolean isChunkLoaded(Identifier dimension, int chunkX, int chunkZ) {
        isChunkLoadedCalls++;
        return allChunksLoaded.contains(dimension)
                || loadedChunks.getOrDefault(dimension, Set.of()).contains(DensityCells.pack(chunkX, chunkZ));
    }

    @Override
    public OptionalInt surfaceY(Identifier dimension, int x, int z) {
        surfaceYCalls++;
        boolean loaded = allChunksLoaded.contains(dimension)
                || loadedChunks.getOrDefault(dimension, Set.of()).contains(DensityCells.pack(x >> 4, z >> 4));
        if (!loaded) {
            throw new AssertionError("surfaceY asked for an unloaded chunk");
        }
        return OptionalInt.of(surface.getOrDefault(dimension, 64));
    }

    @Override
    public Optional<BlockPos> spawnPosition(Identifier dimension) {
        return Optional.ofNullable(spawns.get(dimension));
    }

    @Override
    public int spawnProtectionRadius() {
        return spawnProtectionRadius;
    }

    @Override
    public Set<String> blockEntityNamespaces(Identifier dimension, int chunkX, int chunkZ) {
        blockEntityCalls++;
        return Set.copyOf(blockEntities.getOrDefault(dimension, Map.of()).getOrDefault(DensityCells.pack(chunkX, chunkZ),
                Set.of()));
    }

    @Override
    public int minBuildHeight(Identifier dimension) {
        return -64;
    }

    @Override
    public int maxBuildHeight(Identifier dimension) {
        return 319;
    }
}
