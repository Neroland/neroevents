package za.co.neroland.neroevents.client.widget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import za.co.neroland.neroevents.client.EventColours;

/**
 * The confirm step for a destructive action (erase, cancel an event): a 220-px modal panel over the modal
 * dim with a title, the consequence (wrapped, never cut), {@code [Yes, …]} (danger) and {@code [Cancel]}. Focus
 * starts on Cancel; Esc cancels; Yes closes the panel first, then runs the action. CLIENT-ONLY.
 */
public final class ConfirmPanel {

    /** The screen that shows the panel. */
    public interface Host {
        void closeConfirm(ConfirmPanel panel);

        int hostWidth();

        int hostHeight();
    }

    public static final int WIDTH = 220;
    public static final int MIN_HEIGHT = 72;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int BUTTON_H = 16;

    private final Host host;
    private final Component title;
    private final Component body;
    private final Runnable onYes;
    private final EventButton yes;
    private final EventButton cancel;
    private int x;
    private int y;
    private int height = MIN_HEIGHT;
    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> bodyLines = List.of();
    private int focus = 1;

    public ConfirmPanel(Host host, Component title, Component body, Component yesLabel, Runnable onYes) {
        this.host = host;
        this.title = title;
        this.body = body;
        this.onYes = onYes;
        this.yes = EventButton.of(0, 0, 100, BUTTON_H, yesLabel, b -> confirm()).tone(EventButton.Tone.DANGER);
        this.cancel = EventButton.of(0, 0, 100, BUTTON_H, Component.translatable("console.neroevents.cancel"),
                b -> cancel());
        this.yes.setIntent(false);
        this.cancel.setIntent(false);
    }

    public void cancel() {
        host.closeConfirm(this);
    }

    private void confirm() {
        host.closeConfirm(this);
        onYes.run();
    }

    public void layout(Font font) {
        int inner = WIDTH - 2 * PAD;
        titleLines = WidgetText.wrap(font, title, inner);
        bodyLines = WidgetText.wrap(font, body, inner);
        int content = PAD + titleLines.size() * LINE + 4 + bodyLines.size() * LINE + 8 + BUTTON_H + PAD;
        height = Math.max(MIN_HEIGHT, content);
        x = (host.hostWidth() - WIDTH) / 2;
        y = (host.hostHeight() - height) / 2;
        int bw = (inner - 6) / 2;
        int by = y + height - PAD - BUTTON_H;
        yes.setX(x + PAD);
        yes.setY(by);
        yes.setWidth(bw);
        cancel.setX(x + PAD + bw + 6);
        cancel.setY(by);
        cancel.setWidth(inner - bw - 6);
        yes.setFocused(focus == 0);
        cancel.setFocused(focus == 1);
    }

    public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, host.hostWidth(), host.hostHeight(), EventColours.MODAL_DIM);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, EventColours.DANGER_EDGE);
        g.fill(x, y, x + WIDTH, y + height, EventColours.INK);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + height - 1, EventColours.PANEL);
        int ty = y + PAD;
        for (FormattedCharSequence line : titleLines) {
            g.text(font, line, x + PAD, ty, EventColours.TITLE, false);
            ty += LINE;
        }
        ty += 4;
        for (FormattedCharSequence line : bodyLines) {
            g.text(font, line, x + PAD, ty, EventColours.TITLE, false);
            ty += LINE;
        }
        yes.extractRenderState(g, mouseX, mouseY, partialTick);
        cancel.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (yes.mouseClicked(event, doubleClick)) {
            return true;
        }
        cancel.mouseClicked(event, doubleClick);
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            cancel();
        } else if (event.isCycleFocus() || event.isLeft() || event.isRight()) {
            focus = 1 - focus;
            yes.setFocused(focus == 0);
            cancel.setFocused(focus == 1);
        } else if (event.isSelection()) {
            (focus == 0 ? yes : cancel).keyPressed(event);
        }
        return true;
    }
}
