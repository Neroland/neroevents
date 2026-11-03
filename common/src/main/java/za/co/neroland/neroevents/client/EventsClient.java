package za.co.neroland.neroevents.client;

import java.nio.file.Path;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.hud.EventHud;
import za.co.neroland.neroevents.client.render.ConsoleTint;
import za.co.neroland.neroevents.client.screen.EventHudConfigScreen;
import za.co.neroland.neroevents.console.ConsoleBlocks;
import za.co.neroland.neroevents.platform.Services;

/**
 * The client half of the console and HUD that every loader shares: the dismiss key (default {@code H}), the
 * "move event messages" key (default {@code J}, opens the HUD position screen from anywhere), the
 * player's HUD settings (loaded once from {@code config/neroevents-hud.properties}), the per-tick work and the
 * console tint fallback. Each loader registers the key, the HUD layer, the menu screen and the renderer through its
 * own API and calls {@link #tick()} from its client tick. CLIENT-ONLY.
 */
public final class EventsClient {

    /** Hides the event HUD until the next phase change. */
    //? if >=1.21.9 {
    public static final KeyMapping DISMISS_HUD = new KeyMapping("key.neroevents.dismiss_hud", InputConstants.KEY_H,
            KeyMapping.Category.MISC);
    //?} else {
    /*public static final KeyMapping DISMISS_HUD = new KeyMapping("key.neroevents.dismiss_hud", InputConstants.KEY_H,
            "key.categories.misc");
    *///?}

    /**
     * Opens "Where event messages appear" from anywhere in the world — no console needed, and the only way in on
     * a loader without a mod-list config button (Fabric without Mod Menu).
     */
    //? if >=1.21.9 {
    public static final KeyMapping OPEN_HUD_SETTINGS = new KeyMapping("key.neroevents.hud_settings",
            InputConstants.KEY_J, KeyMapping.Category.MISC);
    //?} else {
    /*public static final KeyMapping OPEN_HUD_SETTINGS = new KeyMapping("key.neroevents.hud_settings",
            InputConstants.KEY_J, "key.categories.misc");
    *///?}

    private static @Nullable HudSettings settings;
    private static boolean tintRegistered;

    private EventsClient() {
    }

    /** One client tick: the HUD's local countdown, the dismiss key, the HUD-settings key. */
    public static void tick() {
        ClientHudState.tick();
        EventHud.tick();
        while (DISMISS_HUD.consumeClick()) {
            ClientHudState.dismiss();
        }
        boolean open = false;
        while (OPEN_HUD_SETTINGS.consumeClick()) {
            open = true;
        }
        if (open && Minecraft.getInstance().player != null) {
            setScreen(new EventHudConfigScreen(null));
        }
    }

    /** Shows {@code screen} ({@code null}: back to the game). 26.2 moved screen switching onto {@code Gui}. */
    public static void setScreen(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    /**
     * Whether this client is alone in its own world: an integrated server with at most one player online (the
     * server-side single-player rule, run on the client's own server). False on a dedicated connection.
     */
    public static boolean singlePlayer() {
        Minecraft mc = Minecraft.getInstance();
        var integrated = mc.getSingleplayerServer();
        return integrated != null && integrated.getPlayerCount() <= 1;
    }

    /** The key's current binding, for tooltips ("Press H to hide…"). */
    public static Component dismissKeyName() {
        return DISMISS_HUD.getTranslatedKeyMessage();
    }

    /** The player's HUD settings (loaded on first use). */
    public static synchronized HudSettings hudSettings() {
        HudSettings current = settings;
        if (current == null) {
            current = HudSettings.read(settingsFile());
            settings = current;
        }
        return current;
    }

    /** Saves the HUD settings to this computer; returns whether the file was written. */
    public static synchronized boolean saveHudSettings() {
        return hudSettings().write(settingsFile());
    }

    private static @Nullable Path settingsFile() {
        try {
            return Services.PLATFORM.getConfigDir().resolve(HudSettings.FILE_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Registers the console's screen tint straight into the vanilla block colours, for a loader without a tint
     * registration event (Fabric). Idempotent; call from the client tick so the colour table exists.
     */
    public static void registerTintFallback() {
        if (tintRegistered) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getBlockColors() == null) {
            return;
        }
        try {
            //? if >=26.1 {
            mc.getBlockColors().register(List.of(ConsoleTint.INSTANCE), ConsoleBlocks.EVENT_CONSOLE.get());
            //?} else {
            /*mc.getBlockColors().register(ConsoleTint.INSTANCE, ConsoleBlocks.EVENT_CONSOLE.get());
            *///?}
        } catch (RuntimeException | LinkageError e) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] console tint not registered ({})", e.getClass().getName());
        }
        tintRegistered = true;
    }

    /**
     * F1 / hide-GUI: 26.x keeps the flag on {@code Gui}'s package-private {@code hud} field, so it is read
     * reflectively (handles cached; "not hidden" if the shape ever changes) — the Nerospace HUD recipe.
     */
    private static boolean hideGuiResolved;
    private static java.lang.reflect.Field guiHudField;
    private static java.lang.reflect.Method hudIsHidden;

    public static boolean isGuiHidden(Minecraft mc) {
        //? if <26.1 {
        /*// Before 26.1 the flag was the public Options.hideGui.
        if (mc != null) {
            return mc.options.hideGui;
        }
        *///?}
        if (!hideGuiResolved) {
            hideGuiResolved = true;
            try {
                java.lang.reflect.Field field = net.minecraft.client.gui.Gui.class.getDeclaredField("hud");
                field.setAccessible(true);
                hudIsHidden = field.getType().getMethod("isHidden");
                guiHudField = field;
            } catch (ReflectiveOperationException | RuntimeException e) {
                guiHudField = null;
                hudIsHidden = null;
            }
        }
        java.lang.reflect.Field field = guiHudField;
        java.lang.reflect.Method method = hudIsHidden;
        if (field == null || method == null) {
            return false;
        }
        try {
            Object hud = field.get(mc.gui);
            return hud != null && Boolean.TRUE.equals(method.invoke(hud));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
