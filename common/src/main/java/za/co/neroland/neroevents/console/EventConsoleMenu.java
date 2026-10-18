package za.co.neroland.neroevents.console;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The Event Console's menu. It has no slots and no container data: everything the screen shows arrives in a
 * {@code ConsoleSnapshot}. Its jobs are to bind the open screen to one console block (every serverbound
 * console payload is refused unless the sender has this menu open and is still at the console) and to hold the
 * per-sitting rate limit and "already claimed" flag.
 */
public class EventConsoleMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    /** Server: this sitting's request and "too fast" answer limits. */
    private final ConsoleIntents.RateLimit rateLimit = new ConsoleIntents.RateLimit();
    /** Server: the viewer emptied their crate from this sitting. */
    private boolean claimedHere;

    /** Client-side factory: the client learns everything from the snapshot. */
    public EventConsoleMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public EventConsoleMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ConsoleBlocks.EVENT_CONSOLE_MENU.get(), containerId);
        this.access = access;
    }

    /** Records and allows a request at {@code gameTime} when the rate limit permits it. */
    public boolean tryRequest(long gameTime) {
        return rateLimit.tryRequest(gameTime);
    }

    /**
     * Records and allows a "too fast" answer at {@code gameTime}; at most one per
     * {@link ConsoleIntents#MIN_INTERVAL_TICKS}, so a flood of refused requests is never answered packet for
     * packet.
     */
    public boolean tryDeny(long gameTime) {
        return rateLimit.tryDeny(gameTime);
    }

    public boolean claimedHere() {
        return claimedHere;
    }

    void markClaimed() {
        this.claimedHere = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the console still stands within eight blocks of the player. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ConsoleBlocks.EVENT_CONSOLE.get());
    }
}
