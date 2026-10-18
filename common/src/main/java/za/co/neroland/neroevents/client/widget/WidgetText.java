package za.co.neroland.neroevents.client.widget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text helpers (the NeroSecurity pattern): fit a one-line label to a width with an ellipsis (its full text is
 * then also a tooltip) and wrap sentences so they are never cut mid-word. CLIENT-ONLY.
 */
public final class WidgetText {

    private static final String ELLIPSIS = "…";

    private WidgetText() {
    }

    public static boolean overflows(Font font, Component text, int width) {
        return font.width(text) > width;
    }

    public static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        int room = Math.max(0, width - font.width(ELLIPSIS));
        FormattedText cut = font.substrByWidth(text, room);
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS)));
    }

    public static List<FormattedCharSequence> wrap(Font font, Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(8, width));
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    /** Draws {@code text} fitted to one line; returns the drawn width. */
    public static int drawFitted(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width,
            int colour) {
        FormattedCharSequence line = fit(font, text, width);
        g.text(font, line, x, y, colour, false);
        return font.width(line);
    }

    public static void drawCentredFitted(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y,
            int width, int colour) {
        FormattedCharSequence line = fit(font, text, width);
        g.text(font, line, centreX - font.width(line) / 2, y, colour, false);
    }

    /** Draws {@code text} right-aligned to {@code rightX}, fitted to {@code width}. */
    public static void drawRight(GuiGraphicsExtractor g, Font font, Component text, int rightX, int y, int width,
            int colour) {
        FormattedCharSequence line = fit(font, text, width);
        g.text(font, line, rightX - font.width(line), y, colour, false);
    }

    /** Draws up to {@code maxLines} wrapped lines; returns how many lines the full text needs. */
    public static int drawWrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width,
            int lineHeight, int maxLines, int colour) {
        List<FormattedCharSequence> lines = wrap(font, text, width);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.text(font, lines.get(i), x, y + i * lineHeight, colour, false);
        }
        return lines.size();
    }
}
