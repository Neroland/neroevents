package za.co.neroland.neroevents.compat.mc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Version seam for player chat/action-bar messages. 26.1 gave {@link Player} {@code sendSystemMessage(Component)}
 * and {@code sendOverlayMessage(Component)}; before that the same two calls were
 * {@code displayClientMessage(Component, false|true)}. The build rewrites {@code x.sendSystemMessage(c)} and
 * {@code x.sendOverlayMessage(c)} to these helpers on Minecraft 1.21.x (see legacyRenames in stonecutter.gradle),
 * so the 26.x source keeps calling the player directly.
 */
public final class ChatCompat {

    private ChatCompat() {
    }

    /** A chat-box message to this player only. */
    public static void system(Player player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    /** An action-bar (overlay) message to this player only. */
    public static void overlay(Player player, Component message) {
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);
        *///?}
    }
}
