package za.co.neroland.neroevents.payload.debris;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * Wreck decay without a block-entity ticker: one sweep of the {@code neroevents:wrecks} records per scheduler
 * pass (≤ {@value #MAX_PER_PASS} records, round robin). After {@code wreckLingerDays} real days a wreck goes
 * from stage 0 to 1 (hull plating → scorched panel, bent struts and empty crates → air); after twice that, to
 * stage 2 (scorched panels → cobbled deepslate, empty crates → air). A crate that still holds loot is left alone
 * and keeps the record at stage 1. A record is forgotten once none of its cells is ours any more.
 *
 * <p>Identity only, no protection query: only a cell whose current block is one of <b>our</b> wreck blocks is
 * ever replaced, and only with rubble or air — a claim made over a wreck later must not pin our blocks there
 * forever. A cell in an unloaded chunk leaves the record for a later pass (converted cells are idempotent).
 */
public final class WreckDecay {

    public static final int MAX_PER_PASS = 32;
    public static final long DAY_MS = 86_400_000L;

    /** One placed wreck. World data: no player identity. */
    public record WreckRecord(UUID instanceId, Identifier dimension, BlockPos origin, Identifier variant,
            Rotation rotation, long placedAtEpochMs, int stage, List<BlockPos> cells) {
        public WreckRecord {
            cells = List.copyOf(cells);
        }

        WreckRecord withStage(int next) {
            return new WreckRecord(instanceId, dimension, origin, variant, rotation, placedAtEpochMs, next, cells);
        }
    }

    /** What a cell currently is, as far as decay cares. */
    public enum CellKind { HULL_PLATING, SCORCHED_PANEL, BENT_STRUT, CRATE_EMPTY, CRATE_WITH_LOOT, NOT_OURS }

    /** What a cell becomes. */
    public enum Replacement { SCORCHED_PANEL, RUBBLE, AIR }

    /** The world seam. */
    public interface DecayWorld {
        /** The cell's kind, or null when its chunk is not loaded (never loads it). */
        @Nullable CellKind kind(Identifier dimension, BlockPos pos);

        void replace(Identifier dimension, BlockPos pos, Replacement replacement);
    }

    /** The mutable record list plus the round-robin cursor (production: {@link WreckState}). */
    public interface Store {
        List<WreckRecord> records();

        void replaceAll(List<WreckRecord> records);

        int cursor();

        void cursor(int next);
    }

    private WreckDecay() {
    }

    /** The scheduler-pass hook (registered by the integration step). Never throws. */
    public static void tick(MinecraftServer server) {
        try {
            EventEngine engine = EventEngine.current();
            EngineSettings settings = engine == null ? EngineSettings.defaults() : engine.settings();
            WreckState state = WreckState.get(server);
            if (state.records().isEmpty()) {
                return;
            }
            if (pass(state, new ServerDecayWorld(server), System.currentTimeMillis(), settings.wreckLingerDays) > 0) {
                state.setDirty();
            }
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "payload:debris_crash", "cleanup");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] wreck decay pass failed ({})", e.getClass().getName());
        }
    }

    /**
     * One pass over at most {@value #MAX_PER_PASS} records from the cursor. Returns how many records changed
     * (stage advanced or forgotten). {@code lingerDays == 0} ⇒ nothing ever decays.
     */
    public static int pass(Store store, DecayWorld world, long nowEpochMs, int lingerDays) {
        if (lingerDays <= 0) {
            return 0;
        }
        List<WreckRecord> records = new ArrayList<>(store.records());
        if (records.isEmpty()) {
            return 0;
        }
        long lingerMs = lingerDays * DAY_MS;
        int n = records.size();
        int start = Math.floorMod(store.cursor(), n);
        int visits = Math.min(MAX_PER_PASS, n);
        boolean[] remove = new boolean[n];
        int changed = 0;
        for (int k = 0; k < visits; k++) {
            int i = (start + k) % n;
            WreckRecord r = records.get(i);
            WreckRecord next = step(r, world, nowEpochMs, lingerMs);
            if (next == null) {
                remove[i] = true;
                changed++;
            } else if (next != r) {
                records.set(i, next);
                changed++;
            }
        }
        List<WreckRecord> kept = new ArrayList<>(n);
        int removedBeforeCursor = 0;
        int newCursor = (start + visits) % n;
        for (int i = 0; i < n; i++) {
            if (remove[i]) {
                if (i < newCursor) {
                    removedBeforeCursor++;
                }
            } else {
                kept.add(records.get(i));
            }
        }
        store.replaceAll(kept);
        store.cursor(kept.isEmpty() ? 0 : Math.floorMod(newCursor - removedBeforeCursor, kept.size()));
        return changed;
    }

    /** One record: the same record (nothing due / blocked), the next stage, or null (forget it). */
    static @Nullable WreckRecord step(WreckRecord r, DecayWorld world, long now, long lingerMs) {
        if (r.stage() == 0 && now < r.placedAtEpochMs() + lingerMs) {
            return r;
        }
        if (r.stage() >= 1 && now < r.placedAtEpochMs() + 2L * lingerMs) {
            return r;
        }
        boolean allLoaded = true;
        boolean anyOurs = false;
        boolean lootLeft = false;
        int target = r.stage() == 0 ? 1 : 2;
        for (BlockPos cell : r.cells()) {
            CellKind kind = world.kind(r.dimension(), cell);
            if (kind == null) {
                allLoaded = false;
                anyOurs = true;
                continue;
            }
            if (target == 1) {
                switch (kind) {
                    case HULL_PLATING -> world.replace(r.dimension(), cell, Replacement.SCORCHED_PANEL);
                    case BENT_STRUT, CRATE_EMPTY -> world.replace(r.dimension(), cell, Replacement.AIR);
                    default -> {
                    }
                }
            } else {
                switch (kind) {
                    case HULL_PLATING, SCORCHED_PANEL -> world.replace(r.dimension(), cell, Replacement.RUBBLE);
                    case BENT_STRUT, CRATE_EMPTY -> world.replace(r.dimension(), cell, Replacement.AIR);
                    default -> {
                    }
                }
            }
            CellKind after = world.kind(r.dimension(), cell);
            if (after == CellKind.CRATE_WITH_LOOT) {
                lootLeft = true;
            }
            if (after != null && after != CellKind.NOT_OURS) {
                anyOurs = true;
            }
        }
        if (!anyOurs) {
            return null;
        }
        if (!allLoaded) {
            return r; // partial progress is fine; finish on a later pass
        }
        if (target == 2) {
            return lootLeft ? (r.stage() == 1 ? r : r.withStage(1)) : r.withStage(2);
        }
        return r.withStage(1);
    }

    /** The running server behind the seam. */
    static final class ServerDecayWorld implements DecayWorld {
        private final MinecraftServer server;

        ServerDecayWorld(MinecraftServer server) {
            this.server = server;
        }

        private @Nullable ServerLevel level(Identifier dimension) {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }

        @Override
        public @Nullable CellKind kind(Identifier dimension, BlockPos pos) {
            ServerLevel level = level(dimension);
            if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block == CorePayloadBlocks.HULL_PLATING.get()) {
                return CellKind.HULL_PLATING;
            }
            if (block == CorePayloadBlocks.SCORCHED_PANEL.get()) {
                return CellKind.SCORCHED_PANEL;
            }
            if (block == CorePayloadBlocks.BENT_STRUT.get()) {
                return CellKind.BENT_STRUT;
            }
            if (block == CorePayloadBlocks.SALVAGE_CRATE.get()) {
                return level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate && crate.holdsLoot()
                        ? CellKind.CRATE_WITH_LOOT : CellKind.CRATE_EMPTY;
            }
            return CellKind.NOT_OURS;
        }

        @Override
        public void replace(Identifier dimension, BlockPos pos, Replacement replacement) {
            ServerLevel level = level(dimension);
            if (level == null) {
                return;
            }
            BlockState next = switch (replacement) {
                case SCORCHED_PANEL -> CorePayloadBlocks.SCORCHED_PANEL.get().defaultBlockState();
                case RUBBLE -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case AIR -> Blocks.AIR.defaultBlockState();
            };
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }
}
