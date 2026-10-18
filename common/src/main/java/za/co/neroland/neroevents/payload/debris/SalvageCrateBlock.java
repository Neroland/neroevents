package za.co.neroland.neroevents.payload.debris;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * {@code neroevents:salvage_crate} ({@code facing}, {@code open}): the wreck's loot container. Using it opens a
 * one-row chest menu (through {@code MenuOpener}); the loot is rolled on the first open. While it still holds
 * loot (unopened, or items left inside) it cannot be broken — mining makes no progress and the player is told
 * {@code block.neroevents.salvage_crate.locked}. Empty, it breaks to air and drops nothing. Removed any
 * other way (an explosion, a creative break), it drops whatever it holds, rolling unopened loot first.
 */
public class SalvageCrateBlock extends BaseEntityBlock {

    public static final MapCodec<SalvageCrateBlock> CODEC = BlockCodecs.simple(SalvageCrateBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    @SuppressWarnings("this-escape")
    public SalvageCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SalvageCrateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SalvageCrateBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate) {
            crate.tryOpen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** No mining progress while the crate holds loot (creative players still break it). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate && crate.holdsLoot()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Tells a player who starts mining a full crate why nothing happens. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate
                && crate.holdsLoot()) {
            serverPlayer.sendOverlayMessage(Component.translatable("block.neroevents.salvage_crate.locked"));
        }
        super.attack(state, level, pos, player);
    }
}
