package za.co.neroland.neroevents.payload.debris;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The wreck palette. {@code hull_plating} and {@code scorched_panel} are plain {@link Block}s (registered
 * directly in {@code CorePayloadBlocks}); {@code bent_strut} is the one with a facing and a non-full shape.
 * None of them has behaviour: decay is driven by {@link WreckDecay}, never by a ticker.
 */
public final class WreckPaletteBlocks {

    private WreckPaletteBlocks() {
    }

    /** {@code neroevents:bent_strut}: a 4×16×4 kinked beam with a horizontal facing. */
    public static class BentStrutBlock extends Block {

        public static final MapCodec<BentStrutBlock> CODEC = BlockCodecs.simple(BentStrutBlock::new);
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

        @SuppressWarnings("this-escape")
        public BentStrutBlock(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
        protected MapCodec<BentStrutBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }
}
