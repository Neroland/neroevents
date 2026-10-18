package za.co.neroland.neroevents.war;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * {@code neroevents:war_beacon}: a faction-war capture objective. One block (no block-state properties); the
 * model is the plinth, and the column, faction band, emitter head and rings are drawn by the block-entity
 * renderer from the synced state. Unbreakable, no loot, light level 9. Placed and removed only by the faction
 * war payload; an operator may place a decorative one from the creative item, which does nothing without an
 * instance. No ticker: the payload drives the block entity.
 */
public class WarBeaconBlock extends BaseEntityBlock {

    public static final MapCodec<WarBeaconBlock> CODEC = BlockCodecs.simple(WarBeaconBlock::new);

    public WarBeaconBlock(Properties properties) {
        super(properties);
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<WarBeaconBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarBeaconBlockEntity(pos, state);
    }
}
