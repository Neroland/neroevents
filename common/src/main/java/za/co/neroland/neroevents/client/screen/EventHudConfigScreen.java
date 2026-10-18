package za.co.neroland.neroevents.client.screen;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.client.ConsoleLayout;
import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.HudSettings;
import za.co.neroland.neroevents.client.HudSettings.Anchor;
import za.co.neroland.neroevents.client.HudSettings.Element;
import za.co.neroland.neroevents.client.HudSettings.Toggle;
import za.co.neroland.neroevents.client.hud.EventHud;
import za.co.neroland.neroevents.client.widget.EventButton;
import za.co.neroland.neroevents.client.widget.Feedback;
import za.co.neroland.neroevents.client.widget.Hull;
import za.co.neroland.neroevents.client.widget.WidgetText;

/**
 * "Where event messages appear": a miniature of the game screen with the countdown pill, the banner and the
 * objective lines drawn small — drag any of them and it snaps to one of nine anchors — plus the six show/hide
 * switches, the scale ladder and Reset. Every change is saved at once to this computer only; nothing is sent to
 * a server. Opened from the console's gear and from the mod list's config button. CLIENT-ONLY.
 */
public class EventHudConfigScreen extends Screen {

    private final @Nullable Screen parent;
    private final Map<Toggle, EventButton> toggles = new EnumMap<>(Toggle.class);
    private final EventButton[] scales = new EventButton[HudSettings.SCALES.length];
    private int leftPos;
    private int topPos;
    private @Nullable Feedback feedback;
    private @Nullable Element dragging;
    private @Nullable Element selected = Element.PILL;
    private double dragX;
    private double dragY;
    private double grabDx;
    private double grabDy;

    public EventHudConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("hud.neroevents.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (this.width - ConsoleLayout.HUD_PANEL_W) / 2;
        topPos = (this.height - ConsoleLayout.HUD_PANEL_H) / 2;
        toggles.clear();
        Toggle[] all = Toggle.values();
        for (int i = 0; i < all.length; i++) {
            Toggle toggle = all[i];
            ConsoleLayout.Box box = ConsoleLayout.toggle(i);
            EventButton button = addRenderableWidget(EventButton.of(leftPos + box.x0(), topPos + box.y0(), box.width(),
                    box.height(), Component.empty(), b -> flip(toggle)));
            button.setIntent(false);
            button.setHelp(Component.translatable(toggle.nameKey() + ".help"));
            toggles.put(toggle, button);
        }
        for (int i = 0; i < scales.length; i++) {
            final int index = i;
            ConsoleLayout.Box box = ConsoleLayout.scaleButton(i);
            scales[i] = addRenderableWidget(EventButton.of(leftPos + box.x0(), topPos + box.y0(), box.width(),
                    box.height(), Component.translatable("hud.neroevents.config.scale." + i), b -> setScale(index)));
            scales[i].setIntent(false);
            scales[i].setHelp(Component.translatable("hud.neroevents.config.scale.help"));
        }
        ConsoleLayout.Box reset = ConsoleLayout.resetButton();
        EventButton resetButton = addRenderableWidget(EventButton.of(leftPos + reset.x0(), topPos + reset.y0(),
                reset.width(), reset.height(), Component.translatable("hud.neroevents.config.reset"), b -> reset()));
        resetButton.setIntent(false);
        resetButton.setHelp(Component.translatable("hud.neroevents.config.reset.help"));
        if (feedback == null) {
            feedback = Feedback.hint(Component.translatable("hud.neroevents.config.hint", EventsClient.dismissKeyName()));
        }
        refresh();
    }

    private HudSettings settings() {
        return EventsClient.hudSettings();
    }

    private void refresh() {
        HudSettings s = settings();
        for (Map.Entry<Toggle, EventButton> entry : toggles.entrySet()) {
            boolean on = s.on(entry.getKey());
            entry.getValue().setMessage(Component.translatable("hud.neroevents.config.toggle",
                    Component.translatable(entry.getKey().nameKey()),
                    Component.translatable(on ? "hud.neroevents.config.on" : "hud.neroevents.config.off")));
            entry.getValue().setSelected(on);
        }
        for (int i = 0; i < scales.length; i++) {
            if (scales[i] != null) {
                scales[i].setSelected(s.scaleIndex() == i);
            }
        }
    }

    private void changed() {
        boolean ok = EventsClient.saveHudSettings();
        feedback = ok ? Feedback.ok(Component.translatable("hud.neroevents.config.saved"))
                : Feedback.deny(Component.translatable("hud.neroevents.config.not_saved"));
        refresh();
    }

    private void flip(Toggle toggle) {
        settings().set(toggle, !settings().on(toggle));
        changed();
    }

    private void setScale(int index) {
        settings().setScaleIndex(index);
        changed();
    }

    private void reset() {
        settings().reset();
        changed();
    }

    @Override
    public void onClose() {
        EventsClient.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- miniature geometry --------------------------------------------------------------------------------

    /** The real GUI size the miniature stands for. */
    private int screenW() {
        return Math.max(1, this.width);
    }

    private int screenH() {
        return Math.max(1, this.height);
    }

    /** The miniature screen rectangle (aspect-correct, centred in the left box). */
    private int[] mini() {
        ConsoleLayout.Box area = ConsoleLayout.MINIATURE;
        double scale = Math.min(area.width() / (double) screenW(), area.height() / (double) screenH());
        int w = Math.max(16, (int) Math.round(screenW() * scale));
        int h = Math.max(16, (int) Math.round(screenH() * scale));
        int x = leftPos + area.x0() + (area.width() - w) / 2;
        int y = topPos + area.y0() + (area.height() - h) / 2;
        return new int[] {x, y, w, h};
    }

    private int[] elementSize(Element element, int miniW) {
        double s = miniW / (double) screenW() * settings().scale();
        int[] real = switch (element) {
            case PILL -> new int[] {EventHud.PILL_W, EventHud.PILL_H};
            case BANNER -> new int[] {EventHud.BANNER_W, EventHud.BANNER_H};
            case OBJECTIVES -> new int[] {EventHud.OBJECTIVE_W, EventHud.OBJECTIVE_LINE * 3};
        };
        return new int[] {Math.max(8, (int) Math.round(real[0] * s)), Math.max(4, (int) Math.round(real[1] * s))};
    }

    /** Absolute rectangle of an element in the miniature (x, y, w, h). */
    private int[] elementRect(Element element) {
        int[] m = mini();
        int[] size = elementSize(element, m[2]);
        if (element == dragging) {
            return new int[] {(int) Math.round(dragX), (int) Math.round(dragY), size[0], size[1]};
        }
        Anchor anchor = settings().anchor(element);
        int x = m[0] + anchor.x(m[2], size[0]);
        int y = m[1] + anchor.y(m[3], size[1]);
        if (element == Element.OBJECTIVES && settings().anchor(Element.PILL) == anchor) {
            int[] pill = elementSize(Element.PILL, m[2]);
            y += anchor.stacksDown() ? pill[1] + 1 : -(pill[1] + 1);
        }
        return new int[] {x, y, size[0], size[1]};
    }

    // --- input ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (Element element : new Element[] {Element.OBJECTIVES, Element.PILL, Element.BANNER}) {
            int[] r = elementRect(element);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                dragging = element;
                selected = element;
                grabDx = mx - r[0];
                grabDy = my - r[1];
                dragX = r[0];
                dragY = r[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int[] m = mini();
            dragX = Math.max(m[0], Math.min(m[0] + m[2] - 2, event.x() - grabDx));
            dragY = Math.max(m[1], Math.min(m[1] + m[3] - 2, event.y() - grabDy));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Element element = dragging;
        if (element != null) {
            int[] m = mini();
            int[] size = elementSize(element, m[2]);
            double cx = dragX + size[0] / 2.0 - m[0];
            double cy = dragY + size[1] / 2.0 - m[1];
            dragging = null;
            settings().setAnchor(element, Anchor.nearest(cx, cy, m[2], m[3]));
            changed();
            return true;
        }
        return super.mouseReleased(event);
    }

    // --- drawing -------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos;
        int y = topPos;
        int w = ConsoleLayout.HUD_PANEL_W;
        int h = ConsoleLayout.HUD_PANEL_H;
        Hull.panel(g, x, y, w, h);
        WidgetText.drawFitted(g, font, this.title, x + ConsoleLayout.PAD, y + 6, w - 2 * ConsoleLayout.PAD,
                EventColours.TITLE);
        WidgetText.drawFitted(g, font, Component.translatable("hud.neroevents.config.subtitle"),
                x + ConsoleLayout.PAD, y + 17, w - 2 * ConsoleLayout.PAD, EventColours.SUBTLE);
        Hull.footer(g, font, x, y, w, h, ConsoleLayout.FOOTER_H, feedback);
        drawMiniature(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick); // the buttons
    }

    private void drawMiniature(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ConsoleLayout.Box area = ConsoleLayout.MINIATURE;
        g.fill(leftPos + area.x0(), topPos + area.y0(), leftPos + area.x1(), topPos + area.y1(), EventColours.TROUGH);
        int[] m = mini();
        g.outline(m[0] - 1, m[1] - 1, m[2] + 2, m[3] + 2, EventColours.EDGE);
        Anchor magnet = null;
        Element element = dragging;
        if (element != null) {
            int[] size = elementSize(element, m[2]);
            magnet = Anchor.within(dragX + size[0] / 2.0 - m[0], dragY + size[1] / 2.0 - m[1], m[2], m[3],
                    ConsoleLayout.SNAP_RADIUS).orElse(null);
        }
        for (Anchor anchor : Anchor.values()) {
            int ax = m[0] + Math.min(m[2] - 1, anchor.pointX(m[2]));
            int ay = m[1] + Math.min(m[3] - 1, anchor.pointY(m[3]));
            int colour = anchor == magnet ? EventColours.TITLE : EventColours.EDGE_HOT;
            int r = anchor == magnet ? 2 : 1;
            g.fill(ax - r, ay - r, ax + r + 1, ay + r + 1, colour);
        }
        for (Element e : new Element[] {Element.BANNER, Element.PILL, Element.OBJECTIVES}) {
            int[] r = elementRect(e);
            boolean on = switch (e) {
                case PILL -> settings().on(Toggle.COUNTDOWN);
                case BANNER -> settings().on(Toggle.BANNER);
                case OBJECTIVES -> settings().on(Toggle.OBJECTIVES);
            };
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? EventColours.HUD_BODY : 0x60000000);
            g.outline(r[0], r[1], r[2], r[3], e == selected ? EventColours.TITLE : EventColours.EDGE);
            g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], EventColours.opaque(EventColours.SEV_HAZARD));
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (hover && dragging == null) {
                List<FormattedCharSequence> tip = font.split(Component.translatable("hud.neroevents.config.drag",
                        Component.translatable(e.nameKey())), 200);
                g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        }
        Element shown = selected;
        if (shown != null) {
            Component name = Component.translatable("hud.neroevents.config.anchor_name",
                    Component.translatable(shown.nameKey()), Component.translatable(settings().anchor(shown).nameKey()));
            WidgetText.drawCentredFitted(g, font, name, leftPos + area.x0() + area.width() / 2,
                    topPos + ConsoleLayout.ANCHOR_NAME_Y, area.width() + 8, EventColours.TITLE);
        }
    }
}
