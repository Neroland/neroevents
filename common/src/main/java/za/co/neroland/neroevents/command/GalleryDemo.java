package za.co.neroland.neroevents.command;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.command.GalleryShowcase.Session;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.EventConsoleBlockEntity;

/**
 * The console server's view of the gallery demo: a demo HUD and console for the gallery's owner only, while
 * that owner is in the gallery's dimension and nothing real is announced or running (the console server only
 * asks when it is idle). Everything else — another player, another dimension, no gallery — gets nothing here.
 * The gallery's own consoles light up with the demo scene's severity; consoles outside the gallery are never
 * touched.
 */
public final class GalleryDemo {

    private GalleryDemo() {
    }

    private static Identifier dimensionOf(ServerPlayer player) {
        return player.level().dimension().identifier();
    }

    private static @Nullable Session sessionFor(ServerPlayer player) {
        Session s = GalleryShowcase.session();
        if (s == null || s.server != player.level().getServer()) {
            return null;
        }
        return s.matches(player.getUUID(), dimensionOf(player)) ? s : null;
    }

    /** Whether the demo is on for {@code player}. */
    public static boolean active(ServerPlayer player) {
        return sessionFor(player) != null;
    }

    /** The demo HUD for {@code player}, if the demo is on for them. */
    public static Optional<HudState> hudFor(ServerPlayer player) {
        return hudFor(sessionFor(player), player.getUUID(), dimensionOf(player));
    }

    /** Pure: the demo HUD session {@code s} shows {@code viewer} in {@code dimension}, if any. */
    static Optional<HudState> hudFor(@Nullable Session s, UUID viewer, Identifier dimension) {
        if (s == null || !s.matches(viewer, dimension)) {
            return Optional.empty();
        }
        return Optional.of(GalleryDemoData.hud(s.hudScene, s.sceneAge(), s.dimension, s.origin));
    }

    /** The demo console for {@code player} with a footer answer ({@code key} "" for the demo footer). */
    public static Optional<ConsoleSnapshot> snapshotFor(ServerPlayer player, boolean ok, String key, String arg) {
        Session s = sessionFor(player);
        if (s == null) {
            return Optional.empty();
        }
        return Optional.of(GalleryDemoData.console(s.hudScene, s.sceneAge(), s.dimension, s.origin, ok, key, arg));
    }

    /**
     * A real event is showing for {@code player}: if the demo would have been on, say once that it steps aside.
     * Called by the console server on every busy push.
     */
    public static void noteBusy(ServerPlayer player) {
        Session s = sessionFor(player);
        if (s != null && !s.yieldedNoticeSent) {
            s.yieldedNoticeSent = true;
            player.sendSystemMessage(Component.translatable("command.neroevents.gallery.demo_yields"));
        }
    }

    /** Nothing real is showing any more: the next real event may say "steps aside" again. */
    public static void noteIdle(ServerPlayer player) {
        Session s = sessionFor(player);
        if (s != null) {
            s.yieldedNoticeSent = false;
        }
    }

    /**
     * The demo scene's top severity for a console at {@code pos}, when it stands inside the live gallery; else
     * {@link EventConsoleBlockEntity#NONE}.
     */
    public static byte severityAt(@Nullable Level level, BlockPos pos) {
        Session s = GalleryShowcase.session();
        if (s == null || level == null || pos == null || !s.covers(level.dimension().identifier(), pos)) {
            return EventConsoleBlockEntity.NONE;
        }
        return GalleryDemoData.topSeverity(s.hudScene);
    }
}
