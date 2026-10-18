package za.co.neroland.neroevents.payload.debris;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.debris.WreckDecay.WreckRecord;

/**
 * {@code neroevents:wrecks} (overworld world data): every placed wreck's cells, so {@link WreckDecay} can turn
 * them to rubble without a block-entity ticker. Holds no player identity, so it needs no eraser. Capped at
 * {@code wreckRecordsMax} records — the oldest is forgotten first, and its blocks then simply never decay.
 * Read only through {@link #get(MinecraftServer)} (Core's {@code SavedDataRecovery}).
 */
public final class WreckState extends SavedData implements WreckDecay.Store {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "wrecks");
    public static final int SCHEMA_VERSION = 1;

    static final Codec<WreckRecord> RECORD_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("instance").forGetter(WreckRecord::instanceId),
            Identifier.CODEC.fieldOf("dimension").forGetter(WreckRecord::dimension),
            BlockPos.CODEC.fieldOf("origin").forGetter(WreckRecord::origin),
            Identifier.CODEC.fieldOf("variant").forGetter(WreckRecord::variant),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(WreckRecord::rotation),
            Codec.LONG.fieldOf("placed_at").forGetter(WreckRecord::placedAtEpochMs),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(WreckRecord::stage),
            BlockPos.CODEC.listOf().optionalFieldOf("cells", List.of()).forGetter(WreckRecord::cells)
    ).apply(inst, WreckRecord::new));

    private record Root(int schemaVersion, int cursor, List<WreckRecord> records) {
    }

    public static final Codec<WreckState> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(Root::cursor),
            RECORD_CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(Root::records)
    ).apply(inst, Root::new)).xmap(WreckState::fromData, WreckState::toData);

    public static final SavedDataType<WreckState> TYPE = new SavedDataType<>(ID, WreckState::new, CODEC, null);

    private final List<WreckRecord> records = new ArrayList<>();
    private int cursor;

    public WreckState() {
    }

    public static WreckState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, WreckState::new, ID.toString());
    }

    /** Appends a placed wreck; beyond {@code max} the oldest record is forgotten. */
    public synchronized void add(WreckRecord record, int max) {
        if (record == null || record.cells().isEmpty()) {
            return;
        }
        records.add(record);
        while (records.size() > Math.max(1, max)) {
            records.remove(0);
            cursor = Math.max(0, cursor - 1);
        }
        setDirty();
    }

    @Override
    public synchronized List<WreckRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public synchronized void replaceAll(List<WreckRecord> next) {
        records.clear();
        records.addAll(next);
        setDirty();
    }

    @Override
    public synchronized int cursor() {
        return cursor;
    }

    @Override
    public synchronized void cursor(int next) {
        cursor = Math.max(0, next);
    }

    private synchronized Root toData() {
        return new Root(SCHEMA_VERSION, cursor, List.copyOf(records));
    }

    private static WreckState fromData(Root root) {
        WreckState state = new WreckState();
        state.records.addAll(root.records());
        state.cursor = Math.max(0, root.cursor());
        return state;
    }
}
