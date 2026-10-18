package za.co.neroland.neroevents.payload.debris;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.menu.MenuOpener;
import za.co.neroland.neroevents.payload.LootRolls;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlockEntities;

/**
 * The salvage crate: a 9-slot container that rolls its loot table on the <b>first open</b> (seeded when the
 * wreck was placed, clamped to its VU budget), so contents are fixed per crate and identical for whoever gets
 * there. Every successful open by a player is reported to {@link DebrisCrashPayload#onCrateOpened} (a
 * per-crate once-key, so each player is credited once per crate). The {@code open} blockstate follows the
 * viewer count — a count, never who.
 *
 * <p>Extends {@link BlockEntity} rather than {@code BaseContainerBlockEntity} (Forge patches the latter with
 * an item handler on every side). No automation: crates are for players. The update tag carries only whether
 * the crate still holds loot (for the client's break prediction). If the crate is removed by anything other
 * than a player's mining (an explosion, a creative break), its contents drop where it stood.
 */
public class SalvageCrateBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SIZE = 9;

    private static final Component TITLE = Component.translatable("block.neroevents.salvage_crate");
    private static final String KEY_INSTANCE = "NeroEventsInstance";
    private static final String KEY_TABLE = "LootTable";
    private static final String KEY_BUDGET = "LootBudget";
    private static final String KEY_SEED = "Seed";
    private static final String KEY_ROLLED = "LootRolled";
    private static final String KEY_HOLDS = "HoldsLoot";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private @Nullable UUID instanceId;
    private @Nullable Identifier lootTable;
    private long lootBudget;
    private long seed;
    private boolean lootRolled;
    /** Client copy of {@link #holdsLoot()} from the update tag. */
    private boolean clientHoldsLoot = true;
    /** Transient: open sessions (a count). */
    private int viewers;

    public SalvageCrateBlockEntity(BlockPos pos, BlockState state) {
        super(CorePayloadBlockEntities.SALVAGE_CRATE.get(), pos, state);
    }

    /** Set once by {@link WreckPlacer} right after the crate is placed. */
    public void configure(@Nullable UUID instance, @Nullable Identifier table, long budget, long lootSeed) {
        this.instanceId = instance;
        this.lootTable = table;
        this.lootBudget = budget;
        this.seed = lootSeed;
        this.lootRolled = table == null;
        setChanged();
    }

    public @Nullable UUID instanceId() {
        return instanceId;
    }

    /** Whether the loot table has been rolled (the crate was opened at least once). */
    public boolean lootRolled() {
        return lootRolled;
    }

    /** True while the crate is unopened (loot still to roll) or still holds items. */
    public boolean holdsLoot() {
        if (level != null && level.isClientSide()) {
            return clientHoldsLoot;
        }
        return !lootRolled || !isEmpty();
    }

    /** A player used the crate: roll on the first open, open the menu, report the open. Server thread. */
    public void tryOpen(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        if (!lootRolled) {
            lootRolled = true;
            Identifier table = lootTable;
            if (table != null) {
                List<ItemStack> rolled = LootRolls.rollWithin(serverLevel, table, 1, seed, worldPosition, lootBudget);
                for (int i = 0; i < rolled.size() && i < SIZE; i++) {
                    items.set(i, rolled.get(i));
                }
            }
            setChanged();
            syncLootFlag(serverLevel);
        }
        if (MenuOpener.open(player, this).isPresent()) {
            UUID instance = instanceId;
            if (instance != null) {
                DebrisCrashPayload.onCrateOpened(serverLevel.getServer(), instance, worldPosition, player);
            }
        }
    }

    /**
     * The crate is being replaced by another block (an explosion, a creative player, a command): whatever it
     * holds — rolled now if nobody opened it yet — drops on the spot, so the loot is never voided.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!lootRolled) {
            lootRolled = true;
            Identifier table = lootTable;
            if (table != null) {
                List<ItemStack> rolled = LootRolls.rollWithin(serverLevel, table, 1, seed, pos, lootBudget);
                for (int i = 0; i < rolled.size() && i < SIZE; i++) {
                    items.set(i, rolled.get(i));
                }
            }
        }
        Containers.dropContents(serverLevel, pos, this);
    }

    private void syncLootFlag(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // --- Container ------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (isRemoved() || !(level instanceof ServerLevel serverLevel) || !(user instanceof ServerPlayer)) {
            return;
        }
        viewers++;
        updateOpenState(serverLevel);
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!(level instanceof ServerLevel serverLevel) || !(user instanceof ServerPlayer)) {
            return;
        }
        viewers = Math.max(0, viewers - 1);
        updateOpenState(serverLevel);
        syncLootFlag(serverLevel);
    }

    private void updateOpenState(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(SalvageCrateBlock.OPEN)) {
            return;
        }
        boolean open = viewers > 0;
        if (state.getValue(SalvageCrateBlock.OPEN) != open) {
            serverLevel.setBlock(worldPosition, state.setValue(SalvageCrateBlock.OPEN, open), Block.UPDATE_ALL);
        }
    }

    // --- MenuProvider ---------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
    }

    // --- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        UUID instance = instanceId;
        if (instance != null) {
            output.putString(KEY_INSTANCE, instance.toString());
        }
        Identifier table = lootTable;
        if (table != null) {
            output.putString(KEY_TABLE, table.toString());
        }
        output.putLong(KEY_BUDGET, lootBudget);
        output.putLong(KEY_SEED, seed);
        output.putBoolean(KEY_ROLLED, lootRolled);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        String instance = input.getStringOr(KEY_INSTANCE, "");
        instanceId = instance.isEmpty() ? null : parseUuid(instance);
        String table = input.getStringOr(KEY_TABLE, "");
        lootTable = table.isEmpty() ? null : Identifier.tryParse(table);
        lootBudget = input.getLongOr(KEY_BUDGET, 0L);
        seed = input.getLongOr(KEY_SEED, 0L);
        lootRolled = input.getBooleanOr(KEY_ROLLED, false);
        clientHoldsLoot = input.getBooleanOr(KEY_HOLDS, true);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only whether the crate still holds loot: never the items or the instance. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_HOLDS, !lootRolled || !isEmpty());
        return tag;
    }

    private static @Nullable UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
