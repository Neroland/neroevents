package za.co.neroland.neroevents.client.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.client.EventColours;

/**
 * The NeroEvents button (a port of NeroSecurity's): a dark body, an edge that brightens on hover or keyboard
 * focus, a 1-px top sheen and centred text, all fills. Variants: {@link Tone#DANGER} (erase, cancel: red edge),
 * {@link Tone#YES} (the vote YES button: green body, OK edge); {@link #setSelected selected} for segments; an
 * optional filled {@link #setPill pill} ("YOU: YES") drawn inside the right end. Disabled buttons are grey and
 * explain why on hover. CLIENT-ONLY.
 */
public class EventButton extends EventWidget {

    /** The colour role of a button. */
    public enum Tone { NORMAL, DANGER, YES }

    /** A drawn icon instead of a word. */
    public enum Icon { NONE, GEAR, CROSS }

    private final Consumer<EventButton> onPress;
    private Tone tone = Tone.NORMAL;
    private Icon icon = Icon.NONE;
    private boolean selected;
    private @Nullable Component pill;

    public EventButton(int x, int y, int width, int height, Component label, Consumer<EventButton> onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    public static EventButton of(int x, int y, int width, int height, Component label, Consumer<EventButton> onPress) {
        return new EventButton(x, y, width, height, label, onPress);
    }

    public final EventButton tone(Tone value) {
        this.tone = value == null ? Tone.NORMAL : value;
        return this;
    }

    public final EventButton icon(Icon value) {
        this.icon = value == null ? Icon.NONE : value;
        return this;
    }

    public final void setSelected(boolean value) {
        this.selected = value;
    }

    public final boolean isSelected() {
        return selected;
    }

    /** A filled pill inside the button's right end ({@code null} to remove). */
    public final void setPill(@Nullable Component value) {
        this.pill = value;
    }

    public void press() {
        if (this.active) {
            onPress.accept(this);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.active && this.visible && event.isSelection()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = hot();
        int edge;
        int body;
        if (!this.active) {
            edge = EventColours.DISABLED_EDGE;
            body = EventColours.DISABLED_BODY;
        } else if (tone == Tone.DANGER) {
            edge = EventColours.DANGER_EDGE;
            body = hot ? EventColours.DANGER_HOT : EventColours.DANGER_BODY;
        } else if (tone == Tone.YES) {
            edge = EventColours.OK_TEXT;
            body = hot ? EventColours.YES_HOT : EventColours.YES_BODY;
        } else {
            edge = hot || selected ? EventColours.EDGE_HOT : EventColours.BUTTON_EDGE;
            body = selected ? EventColours.SELECTED_BODY : (hot ? EventColours.BUTTON_HOVER : EventColours.BUTTON_BODY);
        }
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        if (tone != Tone.NORMAL && this.active && hot) {
            g.outline(x + 1, y + 1, w - 2, h - 2, edge); // focus reads without relying on hue
        }
        int text = !this.active ? EventColours.DISABLED_TEXT
                : (hot || selected ? EventColours.BUTTON_TEXT_HOT : EventColours.BUTTON_TEXT);
        Font font = Minecraft.getInstance().font;
        switch (icon) {
            case GEAR -> drawGear(g, x + (w - 7) / 2, y + (h - 7) / 2, text);
            case CROSS -> drawCross(g, x + (w - 5) / 2, y + (h - 5) / 2, text);
            case NONE -> {
                Component label = getMessage();
                Component pillText = pill;
                if (pillText == null) {
                    WidgetText.drawCentredFitted(g, font, label, x + w / 2, y + (h - 8) / 2, w - 4, text);
                } else {
                    int pw = Math.min(w / 2, font.width(pillText) + 6);
                    int px = x + w - 3 - pw;
                    g.fill(px, y + 2, px + pw, y + h - 2, EventColours.TITLE);
                    WidgetText.drawCentredFitted(g, font, pillText, px + pw / 2, y + (h - 8) / 2, pw - 2,
                            EventColours.INK);
                    WidgetText.drawCentredFitted(g, font, label, x + (w - pw - 3) / 2, y + (h - 8) / 2,
                            w - pw - 8, text);
                }
            }
        }
    }

    /** A 7x7 cog: a 5x5 ring with four teeth. */
    private static void drawGear(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x + 3, y, x + 4, y + 1, colour);
        g.fill(x + 3, y + 6, x + 4, y + 7, colour);
        g.fill(x, y + 3, x + 1, y + 4, colour);
        g.fill(x + 6, y + 3, x + 7, y + 4, colour);
        g.outline(x + 1, y + 1, 5, 5, colour);
    }

    private static void drawCross(GuiGraphicsExtractor g, int x, int y, int colour) {
        for (int i = 0; i < 5; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, colour);
            g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, colour);
        }
    }
}
