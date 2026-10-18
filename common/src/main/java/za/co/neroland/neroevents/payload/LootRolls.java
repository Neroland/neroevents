package za.co.neroland.neroevents.payload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.ValueTable;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * MC adapter: rolls a loot table into a list of stacks with a fixed seed, so a pod, pocket or crate holds
 * the same contents however often it is looked at. The table is rolled {@code rolls} times (seed, seed+1,
 * ...) with {@link LootContextParamSets#CHEST} and the origin only: no player, no luck — who opens a
 * container never changes what is in it.
 *
 * <p>A missing table yields nothing (logged at DEBUG; a datapack may point elsewhere). Never throws.
 */
public final class LootRolls {

    private LootRolls() {
    }

    /** Rolls {@code table} {@code rolls} times at {@code origin} with {@code seed}. Server thread. */
    public static List<ItemStack> roll(ServerLevel level, Identifier table, int rolls, long seed, BlockPos origin) {
        List<ItemStack> out = new ArrayList<>();
        if (level == null || table == null) {
            return out;
        }
        try {
            LootTable loot = level.getServer().reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
            if (loot == LootTable.EMPTY) {
                NeroEventsCommon.LOGGER.debug("[NeroEvents] loot table {} is missing or empty", table);
                return out;
            }
            Vec3 at = new Vec3(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, at)
                    .create(LootContextParamSets.CHEST);
            int n = Math.max(1, rolls);
            for (int i = 0; i < n; i++) {
                for (ItemStack stack : loot.getRandomItems(params, seed + i)) {
                    if (!stack.isEmpty()) {
                        out.add(stack);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "payload:loot", "roll");
            NeroEventsCommon.LOGGER.debug("[NeroEvents] loot roll failed ({})", e.getClass().getName());
        }
        return out;
    }

    /** The VU value of a rolled stack: count × the configured default item value ({@code rewardItemDefaultValue}). */
    public static long valueOf(ItemStack stack) {
        return valueOf(stack, itemValue());
    }

    private static long valueOf(ItemStack stack, long perItem) {
        return stack == null || stack.isEmpty() ? 0L : stack.getCount() * perItem;
    }

    private static long itemValue() {
        try {
            return RewardConfig.fromConfig().rewardItemDefaultValue;
        } catch (RuntimeException | LinkageError e) {
            return ValueTable.DEFAULT.itemDefaultValue();
        }
    }

    /** Roll, then clamp to {@code budget} VU (0 = no cap). */
    public static List<ItemStack> rollWithin(ServerLevel level, Identifier table, int rolls, long seed,
            BlockPos origin, long budget) {
        long perItem = itemValue();
        return LootBudget.clamp(roll(level, table, rolls, seed, origin), budget, s -> valueOf(s, perItem));
    }
}
