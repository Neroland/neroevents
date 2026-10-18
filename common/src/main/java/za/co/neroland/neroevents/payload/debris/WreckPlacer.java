package za.co.neroland.neroevents.payload.debris;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;

/**
 * Places a (rotated) {@link WreckLayout} cell by cell. A cell is placed only when {@code evaluation.mayAffect}
 * allows it <b>and</b> the current block is air — or, for {@code y = -1} only, a replaceable ground block
 * (breakable, no block entity). Anything else is skipped: structure-void semantics, so a tree trunk through a
 * strut field is fine. The ground below the plane is never touched otherwise. World access goes through
 * {@link WreckWorld} (production: {@link ServerWreckWorld}).
 */
public final class WreckPlacer {

    private WreckPlacer() {
    }

    /** What was placed: every cell of ours, and the crate cells among them. */
    public record Result(List<BlockPos> cells, List<BlockPos> crates) {
        public Result {
            cells = List.copyOf(cells);
            crates = List.copyOf(crates);
        }
    }

    /** The world seam. */
    public interface WreckWorld {
        boolean isAir(BlockPos pos);

        /** Solid ground that a {@code y = -1} cell may replace: breakable and no block entity. */
        boolean groundReplaceable(BlockPos pos);

        void place(BlockPos pos, WreckLayout.Placement placement);
    }

    public static Result place(WreckWorld world, WreckLayout rotated, BlockPos origin, ProtectionEvaluation evaluation,
            Identifier dimension) {
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> crates = new ArrayList<>();
        for (WreckLayout.Placement p : rotated.placements()) {
            BlockPos cell = origin.offset(p.x(), p.y(), p.z());
            if (!evaluation.mayAffect(dimension, cell).allowed()) {
                continue;
            }
            boolean free = p.y() == -1 ? world.groundReplaceable(cell) || world.isAir(cell) : world.isAir(cell);
            if (!free) {
                continue;
            }
            world.place(cell, p);
            cells.add(cell.immutable());
            if (p.kind() == WreckLayout.Kind.SALVAGE_CRATE) {
                crates.add(cell.immutable());
            }
        }
        return new Result(cells, crates);
    }

    /**
     * Where the wreck goes: centred on the landing column on its ground plane — unless the landing spot drifted
     * more than 2 blocks outside the pre-checked footprint, in which case the anchor's column is used (the
     * pre-check at the anchor is what was cleared).
     */
    public static BlockPos origin(BlockPos landing, int landingGroundY, BlockPos anchor, int anchorGroundY,
            WreckLayout rotated, Footprint prechecked) {
        int hx = (rotated.sizeX() + 1) / 2;
        int hz = (rotated.sizeZ() + 1) / 2;
        int minX = landing.getX() - hx;
        int minZ = landing.getZ() - hz;
        int maxX = minX + rotated.sizeX() - 1;
        int maxZ = minZ + rotated.sizeZ() - 1;
        Footprint allowed = prechecked.grow(2);
        boolean inside = minX >= allowed.minX() && maxX <= allowed.maxX() && minZ >= allowed.minZ()
                && maxZ <= allowed.maxZ();
        if (inside) {
            return new BlockPos(minX, landingGroundY + 1, minZ);
        }
        return new BlockPos(anchor.getX() - hx, anchorGroundY + 1, anchor.getZ() - hz);
    }

    /** The running level. Never loads a chunk; configures each crate's block entity as it is placed. */
    public static final class ServerWreckWorld implements WreckWorld {
        private final ServerLevel level;
        private final @Nullable UUID instanceId;
        private final @Nullable Identifier lootTable;
        private final long lootBudget;
        private final long seed;

        /** {@code lootTable} null places empty, already-rolled crates (no loot, no credit). */
        public ServerWreckWorld(ServerLevel level, @Nullable UUID instanceId, @Nullable Identifier lootTable,
                long lootBudget, long seed) {
            this.level = level;
            this.instanceId = instanceId;
            this.lootTable = lootTable;
            this.lootBudget = lootBudget;
            this.seed = seed;
        }

        private boolean loaded(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return loaded(pos) && level.getBlockState(pos).isAir();
        }

        @Override
        public boolean groundReplaceable(BlockPos pos) {
            if (!loaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F
                    && state.getFluidState().isEmpty();
        }

        @Override
        public void place(BlockPos pos, WreckLayout.Placement p) {
            BlockState state = switch (p.kind()) {
                case HULL_PLATING -> CorePayloadBlocks.HULL_PLATING.get().defaultBlockState();
                case SCORCHED_PANEL -> CorePayloadBlocks.SCORCHED_PANEL.get().defaultBlockState();
                case BENT_STRUT -> CorePayloadBlocks.BENT_STRUT.get().defaultBlockState()
                        .setValue(WreckPaletteBlocks.BentStrutBlock.FACING, p.facing());
                case SALVAGE_CRATE -> CorePayloadBlocks.SALVAGE_CRATE.get().defaultBlockState()
                        .setValue(SalvageCrateBlock.FACING, p.facing());
            };
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (p.kind() == WreckLayout.Kind.SALVAGE_CRATE
                    && level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate) {
                crate.configure(instanceId, lootTable, lootBudget, seed ^ pos.asLong());
            }
        }
    }
}
