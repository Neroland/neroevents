package za.co.neroland.neroevents.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.privacy.BackupHook;

/**
 * The reward crate {@code neroevents:crates} (overworld): items and experience a player earned but has not
 * claimed yet ({@code /neroevents claim}). Reward items never go into the world; they wait here.
 * Entries are kept {@code privacyRewardCrateRetentionDays} (14 by default) and then <b>forfeited</b>; an
 * erasure request forfeits everything.
 *
 * <p>Player-keyed: joins the composite eraser as store {@code "reward_crates"}. Read only through
 * {@link #get(MinecraftServer)} (Core's {@code SavedDataRecovery}). Never logs a UUID or a crate's contents.
 */
public final class RewardCrateState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "crates");
    public static final int SCHEMA_VERSION = 1;
    private static final long DAY_MS = 86_400_000L;

    private record PlayerCrate(UUID player, List<CrateEntry> entries) {

        static final Codec<PlayerCrate> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerCrate::player),
                CrateEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(PlayerCrate::entries)
        ).apply(inst, PlayerCrate::new));
    }

    private record Root(int schemaVersion, List<PlayerCrate> crates) {
    }

    /** On disk: {@code { schema_version, crates: [ { player, entries: [ CrateEntry ] } ] }}. */
    public static final Codec<RewardCrateState> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            PlayerCrate.CODEC.listOf().optionalFieldOf("crates", List.of()).forGetter(Root::crates)
    ).apply(inst, Root::new)).xmap(RewardCrateState::fromData, RewardCrateState::toData);

    public static final SavedDataType<RewardCrateState> TYPE =
            new SavedDataType<>(ID, RewardCrateState::new, CODEC, null);

    private static final NeroEventsData.StoreEraser ERASER = eraser(RewardCrateState::get);

    private final Map<UUID, List<CrateEntry>> crates = new LinkedHashMap<>();

    public RewardCrateState() {
    }

    public static RewardCrateState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, RewardCrateState::new, ID.toString());
    }

    /** Joins the composite eraser (common init slot 4). Idempotent. */
    public static void registerEraser() {
        NeroEventsData.addStore(ERASER);
    }

    /** A store eraser over the store {@code lookup} returns: forfeits the subject's crate, then backs up. */
    public static NeroEventsData.StoreEraser eraser(Function<MinecraftServer, RewardCrateState> lookup) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return "reward_crates";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                lookup.apply(server).erase(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                BackupHook.current().backup(server, name(), () -> SavedDataRecovery.backupNow(server.overworld(),
                        TYPE, lookup.apply(server), ID.toString(), server.registryAccess()));
            }
        };
    }

    // --- state ---------------------------------------------------------------------------------------

    /** Adds one instance's entry (the stack cap is enforced by {@link RewardCrates} before this call). */
    public synchronized void add(UUID player, CrateEntry entry) {
        if (player == null || entry == null || entry.empty()) {
            return;
        }
        crates.computeIfAbsent(player, p -> new ArrayList<>()).add(entry);
        setDirty();
    }

    /** The player's entries, oldest first. */
    public synchronized List<CrateEntry> entriesFor(UUID player) {
        List<CrateEntry> entries = crates.get(player);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /** After a (partial) claim: what is still waiting. */
    public synchronized void replace(UUID player, List<CrateEntry> remaining) {
        List<CrateEntry> kept = new ArrayList<>();
        for (CrateEntry entry : remaining) {
            if (!entry.empty()) {
                kept.add(entry);
            }
        }
        if (kept.isEmpty()) {
            if (crates.remove(player) != null) {
                setDirty();
            }
        } else {
            crates.put(player, kept);
            setDirty();
        }
    }

    public synchronized int stackCount(UUID player) {
        int count = 0;
        for (CrateEntry entry : crates.getOrDefault(player, List.of())) {
            count += entry.items().size();
        }
        return count;
    }

    /** Retention: forfeits whole entries with {@code at ≤ now − days}; returns how many entries. */
    public synchronized int purgeExpired(long now, int retentionDays) {
        long cutoff = now - retentionDays * DAY_MS;
        int removed = 0;
        var it = crates.entrySet().iterator();
        while (it.hasNext()) {
            List<CrateEntry> entries = it.next().getValue();
            int before = entries.size();
            entries.removeIf(entry -> entry.at() <= cutoff);
            removed += before - entries.size();
            if (entries.isEmpty()) {
                it.remove();
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Erasure: forfeits everything the player had waiting; returns how many entries. */
    public synchronized int erase(UUID player) {
        List<CrateEntry> removed = crates.remove(player);
        if (removed != null) {
            setDirty();
            return removed.size();
        }
        return 0;
    }

    /** Conformance probe. */
    public synchronized boolean holds(UUID player) {
        return player != null && crates.containsKey(player);
    }

    public synchronized int size() {
        return crates.size();
    }

    private synchronized Root toData() {
        List<PlayerCrate> out = new ArrayList<>();
        crates.forEach((player, entries) -> out.add(new PlayerCrate(player, List.copyOf(entries))));
        return new Root(SCHEMA_VERSION, out);
    }

    private static RewardCrateState fromData(Root root) {
        RewardCrateState state = new RewardCrateState();
        for (PlayerCrate crate : root.crates()) {
            if (!crate.entries().isEmpty()) {
                state.crates.put(crate.player(), new ArrayList<>(crate.entries()));
            }
        }
        return state;
    }
}
