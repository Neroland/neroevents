package za.co.neroland.neroevents.item;

import java.util.function.BooleanSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroevents.progression.QuestHooks;

/**
 * The {@code hook/supply_beacon} delivery: one Supply Beacon straight into the player's inventory, never on
 * the ground. When there is no room the hook gate stays open and the beacon is handed over on the next
 * login.
 */
public final class SupplyBeaconDelivery implements QuestHooks.Delivery {

    public static final SupplyBeaconDelivery INSTANCE = new SupplyBeaconDelivery();

    private SupplyBeaconDelivery() {
    }

    @Override
    public boolean deliver(ServerPlayer player) {
        return handOver(() -> player.getInventory().add(new ItemStack(EventItems.SUPPLY_BEACON.get())),
                () -> player.sendSystemMessage(Component.translatable("message.neroevents.supply_beacon_no_room")));
    }

    /**
     * The delivery rule: {@code true} (close the gate) only when the whole beacon went into the inventory;
     * otherwise tell the player and leave the gate open. Pure.
     */
    public static boolean handOver(BooleanSupplier addToInventory, Runnable tellNoRoom) {
        if (addToInventory.getAsBoolean()) {
            return true;
        }
        tellNoRoom.run();
        return false;
    }
}
