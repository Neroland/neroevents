package za.co.neroland.neroevents.payload.meteor;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;

/**
 * Applies a {@link CraterPlan} cell by cell. Every cell must pass {@code evaluation.mayAffect} <b>and</b> be
 * replaceable by a crater (breakable, not a block entity, not a fluid source, not already a crater block);
 * slag is only laid where the ground is solid. The pocket is set last and only if allowed — if even the pocket
 * is denied, the impact placed nothing. World access goes through {@link CraterWorld} (production:
 * {@link ServerCraterWorld}), so the gate "a meteor over a denied position places nothing" is a plain-JVM test.
 */
public final class CraterBuilder {

    private CraterBuilder() {
    }

    /** What applying a plan did. {@code craterBlocks} counts every non-pocket cell set. */
    public record Result(int craterBlocks, boolean pocketPlaced) {

        public static final Result NOTHING = new Result(0, false);

        public boolean placedAnything() {
            return craterBlocks > 0 || pocketPlaced;
        }
    }

    /** The world seam. */
    public interface CraterWorld {
        /** Breakable, not a block entity, not a fluid source, not already a crater block. */
        boolean replaceable(BlockPos pos);

        /** Whether the current block is solid ground (slag only replaces solid ground). */
        boolean solid(BlockPos pos);

        /** Sets the cell (the pocket configures its block entity). */
        void set(CraterPlan.Cell cell);
    }

    public static Result apply(CraterWorld world, CraterPlan plan, ProtectionEvaluation evaluation, Identifier dimension) {
        int set = 0;
        boolean pocket = false;
        List<CraterPlan.Cell> cells = plan.cells();
        for (CraterPlan.Cell cell : cells) {
            if (!evaluation.mayAffect(dimension, cell.pos()).allowed()) {
                continue;
            }
            if (cell.kind() == CraterPlan.Kind.POCKET) {
                // The soft pocket sits on the air block over the surface: replaceable there means "empty".
                if (world.replaceable(cell.pos())) {
                    world.set(cell);
                    pocket = true;
                }
                continue;
            }
            if (!world.replaceable(cell.pos())) {
                continue;
            }
            if (cell.kind() == CraterPlan.Kind.SLAG && !world.solid(cell.pos())) {
                continue;
            }
            world.set(cell);
            set++;
        }
        return new Result(set, pocket);
    }

    /** The running level. Never loads a chunk; never touches a block entity. */
    public static final class ServerCraterWorld implements CraterWorld {
        private final ServerLevel level;
        private final @Nullable UUID instanceId;
        private final Identifier pocketTable;
        private final long pocketBudget;
        private final long seed;
        private final List<Identifier> oreBlocks;

        public ServerCraterWorld(ServerLevel level, @Nullable UUID instanceId, Identifier pocketTable, long pocketBudget,
                long seed, List<Identifier> oreBlocks) {
            this.level = level;
            this.instanceId = instanceId;
            this.pocketTable = pocketTable;
            this.pocketBudget = pocketBudget;
            this.seed = seed;
            this.oreBlocks = List.copyOf(oreBlocks);
        }

        @Override
        public boolean replaceable(BlockPos pos) {
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()) {
                return false;
            }
            if (!state.getFluidState().isEmpty() && state.getFluidState().isSource()) {
                return false;
            }
            return !isCraterBlock(state);
        }

        @Override
        public boolean solid(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && state.isSolidRender();
        }

        @Override
        public void set(CraterPlan.Cell cell) {
            BlockState next = switch (cell.kind()) {
                case AIR -> Blocks.AIR.defaultBlockState();
                case CRUST -> CorePayloadBlocks.COOLED_CRUST.get().defaultBlockState();
                case SLAG -> CorePayloadBlocks.METEOR_SLAG.get().defaultBlockState();
                case ORE -> oreState(cell.ore());
                case POCKET -> CorePayloadBlocks.CRATER_POCKET.get().defaultBlockState();
            };
            level.setBlock(cell.pos(), next, Block.UPDATE_ALL);
            if (cell.kind() == CraterPlan.Kind.POCKET
                    && level.getBlockEntity(cell.pos()) instanceof CraterPocketBlockEntity pocket) {
                pocket.configure(instanceId, pocketTable, pocketBudget, seed ^ cell.pos().asLong());
            }
        }

        private BlockState oreState(int index) {
            if (index >= 0 && index < oreBlocks.size()) {
                Block block = BuiltInRegistries.BLOCK.getValue(oreBlocks.get(index));
                if (block != Blocks.AIR) {
                    return block.defaultBlockState();
                }
            }
            return CorePayloadBlocks.EMBEDDED_METEOR_ORE.get().defaultBlockState();
        }

        private static boolean isCraterBlock(BlockState state) {
            Block block = state.getBlock();
            return block == CorePayloadBlocks.METEOR_SLAG.get() || block == CorePayloadBlocks.COOLED_CRUST.get()
                    || block == CorePayloadBlocks.EMBEDDED_METEOR_ORE.get()
                    || block == CorePayloadBlocks.CRATER_POCKET.get();
        }
    }
}
