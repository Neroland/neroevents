package za.co.neroland.neroevents.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.event.Severity;

/**
 * The drawing pieces every NeroEvents screen and the HUD share, ported from NeroSecurity's procedural hull:
 * the panel (1-px {@code EDGE} border, 1-px {@code INK} line, {@code PANEL} body), the 14-px {@code TROUGH}
 * footer with its feedback mark, titled section boxes, the severity glyphs from {@code event_glyphs.png} and the
 * filled severity pill. No GUI texture other than the glyph sheet. CLIENT-ONLY.
 */
public final class Hull {

    /** The single glyph sheet: eight 8x8 slots on the top row of a 64x16 texture. */
    public static final Identifier GLYPHS =
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "textures/gui/event_glyphs.png");
    public static final int GLYPH = 8;
    public static final int MARK = 7;
    public static final int PILL_H = 10;

    private Hull() {
    }

    /** The panel at absolute {@code (x, y)}. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EventColours.EDGE);
        g.fill(x, y, x + w, y + h, EventColours.INK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, EventColours.PANEL);
    }

    /** The footer strip at the bottom of a {@code w x h} panel, with the feedback line (or nothing). */
    public static void footer(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int footerH,
            @Nullable Feedback feedback) {
        int fy = y + h - footerH;
        g.fill(x + 1, fy, x + w - 1, y + h - 1, EventColours.TROUGH);
        g.fill(x + 1, fy, x + w - 1, fy + 1, EventColours.EDGE);
        if (feedback == null) {
            return;
        }
        int tx = x + 8;
        int colour = switch (feedback.kind()) {
            case OK -> EventColours.OK_TEXT;
            case DENY -> EventColours.DENY_TEXT;
            case HINT -> EventColours.SUBTLE;
        };
        switch (feedback.kind()) {
            case OK -> {
                g.fill(tx, fy + 4, tx + MARK, fy + 4 + MARK, EventColours.OK_TEXT);
                tx += MARK + 4;
            }
            case DENY -> {
                cross(g, tx, fy + 4, EventColours.DENY_TEXT);
                tx += MARK + 4;
            }
            case HINT -> {
            }
        }
        WidgetText.drawFitted(g, font, feedback.text(), tx, fy + 3, x + w - 8 - tx, colour);
    }

    /** A 7x7 two-pixel cross (the DENY mark). */
    public static void cross(GuiGraphicsExtractor g, int x, int y, int argb) {
        for (int i = 0; i < MARK; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
            g.fill(x + MARK - 1 - i, y + i, x + MARK - i, y + i + 1, argb);
            if (i < MARK - 1) {
                g.fill(x + i + 1, y + i, x + i + 2, y + i + 1, argb);
                g.fill(x + MARK - 2 - i, y + i, x + MARK - 1 - i, y + i + 1, argb);
            }
        }
    }

    /** A titled group box, caption cut into the top edge in {@code SUBTLE}. */
    public static void section(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
            @Nullable Component caption) {
        g.outline(x0, y0, x1 - x0, y1 - y0, EventColours.EDGE);
        if (caption != null) {
            int w = Math.min(font.width(caption), x1 - x0 - 12);
            g.fill(x0 + 4, y0, x0 + 8 + w, y0 + 1, EventColours.PANEL);
            WidgetText.drawFitted(g, font, caption, x0 + 6, y0 - 4, x1 - x0 - 12, EventColours.SUBTLE);
        }
    }

    /** One 8x8 glyph from the sheet, tinted {@code rgb} (the sheet is white with a baked dark outline). */
    public static void glyph(GuiGraphicsExtractor g, int x, int y, int slot, int rgb) {
        g.blit(RenderPipelines.GUI_TEXTURED, GLYPHS, x, y, slot * (float) GLYPH, 0.0F, GLYPH, GLYPH, 64, 16,
                EventColours.opaque(rgb));
    }

    /** The severity glyph in its colour. */
    public static void severityGlyph(GuiGraphicsExtractor g, int x, int y, @Nullable Severity severity) {
        glyph(g, x, y, EventColours.glyphSlot(severity), EventColours.severity(severity));
    }

    /** The filled severity pill with its word in dark ink; returns its width. */
    public static int severityPill(GuiGraphicsExtractor g, Font font, int x, int y, @Nullable Severity severity) {
        Component word = Component.translatable(EventColours.wordKey(severity));
        int w = font.width(word) + 6;
        g.fill(x, y, x + w, y + PILL_H, EventColours.opaque(EventColours.severity(severity)));
        g.text(font, word, x + 3, y + 1, EventColours.INK, false);
        return w;
    }

    /** The width {@link #severityPill} will take. */
    public static int severityPillWidth(Font font, @Nullable Severity severity) {
        return font.width(Component.translatable(EventColours.wordKey(severity))) + 6;
    }
}
