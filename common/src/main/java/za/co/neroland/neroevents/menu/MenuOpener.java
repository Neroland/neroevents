package za.co.neroland.neroevents.menu;

import java.util.OptionalInt;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The single door every NeroEvents GUI goes through — {@code openMenu}, but it cannot take the server
 * thread with it when the host platform misbehaves. Copied from Nerospace's {@code menu.MenuOpener}
 * (the Neroland convention after crash MC-NEROSPACE-E). No NeroEvents GUI exists yet; every future
 * menu open must call {@link #open} or {@link #openOrConsume}, never {@code Player#openMenu} directly.
 *
 * <p>Vanilla {@code Player#openMenu} is a plain call, so anything that throws underneath it unwinds
 * straight out through the packet handler and kills the tick loop. On a stock Fabric/NeoForge/Forge
 * server nothing does. On a Bukkit/Paper hybrid it can: opening a menu there fires
 * {@code InventoryOpenEvent}, and any listening plugin that walks the inventory drags the server's
 * item-meta bridge across modded items it was never written for. Hybrids are not a supported platform
 * and we cannot fix their bridge, but a GUI that refuses to open is a far better outcome than a downed
 * server.</p>
 *
 * <p>So: catch, log once, put the player back in a clean state, tell them why, and carry on. Only
 * {@link RuntimeException} and {@link LinkageError} are caught — the failure modes a foreign platform
 * actually produces. Genuine {@code Error}s (out of memory, stack overflow) still propagate.</p>
 *
 * <p><b>POPIA/GDPR:</b> the log line and the telemetry report identify the menu by translation key or
 * class name, never the player and never a rendered title (which can contain player-authored text —
 * see {@link #describe}). No name, UUID, IP, or position is recorded on this path.</p>
 */
public final class MenuOpener {

    private MenuOpener() {
    }

    /**
     * Opens {@code provider} for {@code player}.
     *
     * @return the container id as vanilla would return it, or {@link OptionalInt#empty()} if the open
     *         was refused by the platform (or by vanilla itself, which also returns empty).
     */
    public static OptionalInt open(Player player, MenuProvider provider) {
        try {
            return player.openMenu(provider);
        } catch (RuntimeException | LinkageError e) {
            recover(player, provider, e);
            return OptionalInt.empty();
        }
    }

    /**
     * {@link #open} for the common interaction case, translated into the value a {@code useWithoutItem}
     * / {@code useOn} / {@code interact} override should return: {@link InteractionResult#SUCCESS} when
     * the menu opened, otherwise {@link InteractionResult#CONSUME} — the interaction is still spent, so a
     * refused GUI never falls through to placing a block against the thing the player tried to open.
     */
    public static InteractionResult openOrConsume(Player player, MenuProvider provider) {
        return open(player, provider).isPresent() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private static void recover(Player player, MenuProvider provider, Throwable cause) {
        String menu = describe(provider);
        NeroEventsCommon.LOGGER.warn(
                "[NeroEvents] The server platform refused to open menu '{}' — the interaction was "
                        + "cancelled instead of being allowed to crash the server thread. This is "
                        + "typically a Bukkit/Paper hybrid server, or a plugin reacting to the "
                        + "inventory-open event, choking on a modded item. Hybrid servers are not a "
                        + "supported platform for NeroEvents.",
                menu, cause);
        // Best-effort cleanup — the platform is already misbehaving, so nothing here may throw again.
        // Only ServerPlayer exposes closeContainer() publicly across all three loaders; on the client
        // side there is nothing to unwind anyway.
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Nothing further to do; the client will resync on its own.
        }
        try {
            player.sendSystemMessage(Component.translatable("gui.neroevents.menu_open_failed"));
        } catch (RuntimeException | LinkageError ignored) {
            // The player simply does not get the notice.
        }
        NeroEventsTelemetry.captureHandledException(cause, "menu_open", menu);
    }

    /**
     * A label for the menu that is safe to log and to send to Sentry: the translation key we authored,
     * or failing that the provider class. Never the <em>rendered</em> title, which can carry
     * player-authored text (a renamed block or a player-chosen event name).
     */
    private static String describe(MenuProvider provider) {
        try {
            Component title = provider.getDisplayName();
            if (title != null && title.getContents() instanceof TranslatableContents translatable) {
                return translatable.getKey();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the class name.
        }
        return provider.getClass().getName();
    }
}
