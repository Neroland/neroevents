package za.co.neroland.neroevents.privacy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.data.NeroEventsData;

/**
 * The reward-history opt-out store {@code neroevents:privacy}: the set of players who asked NeroEvents not
 * to store reward history under their id ({@code /neroevents privacy optout}). Nothing else: no time, no
 * name, no reason.
 *
 * <p>An opted-out player is still paid. From the moment they opt out their ledger rows are written without
 * a player (anonymous), their items and experience wait in a temporary session crate, and reconnect
 * de-duplication uses a session-only marker. The opt-out is prospective: earlier rows keep the id until
 * they expire or are erased.
 *
 * <p>Overworld-resident, read only through {@link #get(MinecraftServer)} (Core's {@code SavedDataRecovery}).
 * The row is personal data in itself, so it joins the composite eraser as store {@code "privacy_optouts"}.
 * Logs never carry a UUID.
 */
public final class PrivacyOptOuts extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "privacy");
    public static final int SCHEMA_VERSION = 1;

    private record Root(int schemaVersion, List<String> optedOut) {
    }

    /** On disk: {@code { schema_version, opted_out: [uuid, ...] }}. Malformed ids are dropped on load. */
    public static final Codec<PrivacyOptOuts> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            Codec.STRING.listOf().optionalFieldOf("opted_out", List.of()).forGetter(Root::optedOut)
    ).apply(inst, Root::new)).xmap(PrivacyOptOuts::fromData, PrivacyOptOuts::toData);

    public static final SavedDataType<PrivacyOptOuts> TYPE = new SavedDataType<>(ID, PrivacyOptOuts::new, CODEC, null);

    private static final NeroEventsData.StoreEraser ERASER = eraser(PrivacyOptOuts::get);

    private final Set<UUID> optedOut = new LinkedHashSet<>();

    public PrivacyOptOuts() {
    }

    /** The only accessor. */
    public static PrivacyOptOuts get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, PrivacyOptOuts::new, ID.toString());
    }

    /** Joins the composite eraser (common init slot 4). Idempotent. */
    public static void registerEraser() {
        NeroEventsData.addStore(ERASER);
    }

    /** A store eraser over the store {@code lookup} returns: drops the subject's row, then backs up. */
    public static NeroEventsData.StoreEraser eraser(Function<MinecraftServer, PrivacyOptOuts> lookup) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return "privacy_optouts";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                lookup.apply(server).remove(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                BackupHook.current().backup(server, name(), () -> SavedDataRecovery.backupNow(server.overworld(),
                        TYPE, lookup.apply(server), ID.toString()));
            }
        };
    }

    // --- state ------------------------------------------------------------------------------------------

    public synchronized boolean isOptedOut(@Nullable UUID player) {
        return player != null && optedOut.contains(player);
    }

    /** Records the player's choice. Returns true when it changed anything (a repeat changes nothing). */
    public synchronized boolean set(UUID player, boolean out) {
        if (player == null) {
            return false;
        }
        boolean changed = out ? optedOut.add(player) : optedOut.remove(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Erasure: forgets the player entirely. */
    public synchronized boolean remove(UUID player) {
        return set(player, false);
    }

    /** Conformance probe. */
    public synchronized boolean holds(UUID player) {
        return isOptedOut(player);
    }

    public synchronized int size() {
        return optedOut.size();
    }

    // --- persistence --------------------------------------------------------------------------------------

    private synchronized Root toData() {
        List<String> ids = new ArrayList<>(optedOut.size());
        optedOut.forEach(id -> ids.add(id.toString()));
        return new Root(SCHEMA_VERSION, ids);
    }

    private static PrivacyOptOuts fromData(Root root) {
        PrivacyOptOuts store = new PrivacyOptOuts();
        int dropped = 0;
        for (String raw : root.optedOut()) {
            try {
                store.optedOut.add(UUID.fromString(raw));
            } catch (IllegalArgumentException malformed) {
                dropped++;
            }
        }
        if (dropped > 0) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] privacy store: dropped {} malformed row(s) on load.", dropped);
        }
        return store;
    }
}
