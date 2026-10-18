package za.co.neroland.neroevents.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventDefinitions;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The Supply Beacon ({@code neroevents:supply_beacon}): one use, handed out by the {@code hook/supply_beacon}
 * quest hook. Using it requests the configured supply-drop event at the holder's own position through the
 * event engine as a {@code REQUEST} (see {@link SupplyBeaconUse}); it is consumed only when the drop is
 * queued. Until the server has the supply-drop event, the beacon says so and is kept.
 */
public class SupplyBeaconItem extends Item {

    public SupplyBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.neroevents.supply_beacon.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MinecraftServer server = serverPlayer.level().getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }
        SupplyBeaconUse.Outcome outcome;
        try {
            RewardConfig cfg = RewardConfig.fromConfig();
            EventEngine engine = EventEngine.get(server);
            outcome = SupplyBeaconUse.decide(cfg.supplyBeaconDefinition,
                    id -> EventDefinitions.definitionsForServer(server).containsKey(id), engine::request,
                    serverPlayer.level().dimension().identifier(), serverPlayer.blockPosition());
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "item", "supply_beacon_use");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] Supply Beacon use failed ({}).", failure.getClass().getName());
            serverPlayer.sendSystemMessage(Component.translatable("item.neroevents.supply_beacon.refused.other"));
            return InteractionResult.FAIL;
        }
        serverPlayer.sendSystemMessage(Component.translatable(outcome.messageKey()));
        if (!outcome.consume()) {
            return InteractionResult.FAIL;
        }
        serverPlayer.getItemInHand(hand).consume(1, serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
