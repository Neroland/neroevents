package za.co.neroland.neroevents.testing.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.war.WarWorld;

/** A scriptable {@link WarWorld}: factions, members, a flat surface, beacons in a map, presence per beacon. */
public final class FakeWarWorld implements WarWorld {

    public final Set<Identifier> factions = new HashSet<>();
    public final Map<UUID, Set<Identifier>> members = new HashMap<>();
    public final List<UUID> online = new ArrayList<>();
    public int surface = 64;
    public final Map<BlockPos, UUID> beacons = new LinkedHashMap<>();
    public final Set<BlockPos> blocked = new HashSet<>();
    public final Map<BlockPos, List<UUID>> presence = new HashMap<>();
    public final List<BlockPos> removed = new ArrayList<>();
    public final List<BlockPos> captured = new ArrayList<>();
    public final Map<BlockPos, int[]> synced = new HashMap<>();
    public int placements;

    public FakeWarWorld faction(Identifier faction, UUID... players) {
        factions.add(faction);
        for (UUID player : players) {
            members.computeIfAbsent(player, p -> new HashSet<>()).add(faction);
            if (!online.contains(player)) {
                online.add(player);
            }
        }
        return this;
    }

    @Override
    public Set<Identifier> factionIds() {
        return Set.copyOf(factions);
    }

    @Override
    public Set<Identifier> membershipOf(UUID player) {
        return Set.copyOf(members.getOrDefault(player, Set.of()));
    }

    @Override
    public Optional<String> displayName(Identifier faction) {
        return Optional.of(faction.getPath());
    }

    @Override
    public List<UUID> onlinePlayers() {
        return List.copyOf(online);
    }

    @Override
    public OptionalInt surfaceY(int x, int z) {
        return OptionalInt.of(surface);
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return !blocked.contains(pos) && !beacons.containsKey(pos);
    }

    @Override
    public boolean placeBeacon(BlockPos pos, UUID instanceId, int index, int colourA, int colourB, int captureTicks,
            int captureRadius) {
        placements++;
        beacons.put(pos.immutable(), instanceId);
        return true;
    }

    @Override
    public boolean holdsBeacon(BlockPos pos, UUID instanceId) {
        return instanceId.equals(beacons.get(pos));
    }

    @Override
    public void removeBeacon(BlockPos pos) {
        if (beacons.remove(pos) != null) {
            removed.add(pos);
        }
    }

    @Override
    public List<UUID> playersNear(BlockPos pos, int radius) {
        return presence.getOrDefault(pos, List.of());
    }

    @Override
    public void syncBeacon(BlockPos pos, int owner, int progressTicks, boolean contested, int progressSide) {
        synced.put(pos, new int[] {owner, progressTicks, contested ? 1 : 0, progressSide});
    }

    @Override
    public void playCaptured(BlockPos pos) {
        captured.add(pos);
    }
}
