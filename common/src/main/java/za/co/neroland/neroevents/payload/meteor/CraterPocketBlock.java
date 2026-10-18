package za.co.neroland.neroevents.payload.meteor;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.LootRolls;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * {@code neroevents:crater_pocket}: the hot heart of a crater. It has no drop of its own; breaking it as a
 * player rolls the pocket's loot table once (seeded when the crater formed, clamped to its VU budget) and
 * credits the breaker through {@link MeteorShowerPayload#onPocketMined} while the shower is still live.
 * After the event the loot still drops; the credit does not.
 */
public class CraterPocketBlock extends BaseEntityBlock {

    public static final MapCodec<CraterPocketBlock> CODEC = BlockCodecs.simple(CraterPocketBlock::new);

    public CraterPocketBlock(Properties properties) {
        super(properties);
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<CraterPocketBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraterPocketBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CraterPocketBlockEntity pocket) {
            try {
                payOut(serverLevel, pos, serverPlayer, pocket);
            } catch (RuntimeException | LinkageError e) {
                NeroEventsTelemetry.captureHandledException(e, "payload:meteor_shower", "pocket");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] crater pocket payout failed ({})", e.getClass().getName());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void payOut(ServerLevel level, BlockPos pos, ServerPlayer player, CraterPocketBlockEntity pocket) {
        if (!pocket.markRolled()) {
            return;
        }
        Identifier table = pocket.lootTable();
        if (table != null) {
            for (ItemStack stack : LootRolls.rollWithin(level, table, 1, pocket.seed(), pos, pocket.lootBudget())) {
                Block.popResource(level, pos, stack);
            }
        }
        UUID instance = pocket.instanceId();
        if (instance != null) {
            MeteorShowerPayload.onPocketMined(level.getServer(), instance, pos, player);
        }
    }
}
