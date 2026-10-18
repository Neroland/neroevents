package za.co.neroland.neroevents.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.client.ClientHudState;
import za.co.neroland.neroevents.client.ConsoleText;
import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.HudSettings;
import za.co.neroland.neroevents.client.HudSettings.Anchor;
import za.co.neroland.neroevents.client.HudSettings.Element;
import za.co.neroland.neroevents.client.HudSettings.Toggle;
import za.co.neroland.neroevents.client.widget.Hull;
import za.co.neroland.neroevents.client.widget.WidgetText;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsoleRows;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.Severity;
import za.co.neroland.neroevents.storm.StormVariant;
import za.co.neroland.neroevents.storm.client.ClientStormState;

/**
 * The event HUD: a countdown pill while an event is announced or voting ("Meteor Shower in 4:32", plus "Vote at a
 * console · 12 yes · 3 no" during a vote), a banner for the first 100 ticks of an active event that then collapses
 * to the pill, up to three objective lines, the storm shelter line and vignette, and a world marker toward a
 * public event's anchor while the player is inside its bounds (never for raid targets — the server never sends
 * one). Every element is a 2-px severity band, the 8x8 glyph, an {@code INK} body at alpha {@code 0xC0}, a 1-px
 * {@code EDGE} border and {@code TITLE} text; each sits at its own anchor from {@link HudSettings}. Everything
 * drawn here arrived in {@code HudState}. CLIENT-ONLY.
 */
public final class EventHud {

    public static final int PILL_W = 120;
    public static final int PILL_H = 16;
    public static final int VOTE_LINE_H = 10;
    public static final int BANNER_W = 220;
    public static final int BANNER_H = 28;
    public static final int OBJECTIVE_W = 160;
    public static final int OBJECTIVE_LINE = 12;
    public static final int SHELTER_H = 12;
    public static final int MARKER_MARGIN = 24;
    private static final int SLIDE_TICKS = 10;
    private static final int GAP = 2;

    private static int exposedTicks;

    private EventHud() {
    }

    /** One client tick (the frost vignette grows with time out in a cryo storm). */
    public static void tick() {
        exposedTicks = ClientStormState.exposed() ? Math.min(600, exposedTicks + 1) : Math.max(0, exposedTicks - 4);
    }

    /** Draw the HUD for the local player (no-op with the GUI hidden, in a menu-less spectator, or with nothing on). */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || EventsClient.isGuiHidden(mc)) {
            return;
        }
        HudSettings settings = EventsClient.hudSettings();
        float scale = settings.scale();
        int screenW = Math.round(g.guiWidth() / scale);
        int screenH = Math.round(g.guiHeight() / scale);
        Font font = mc.font;
        List<HudEntry> entries = ClientHudState.visible();
        int since = ClientHudState.sinceUpdate();
        Identifier dimension = player.level().dimension().identifier();

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        try {
            drawVignette(g, settings, dimension, screenW, screenH);
            List<Block> pillStack = new ArrayList<>();
            HudEntry banner = null;
            for (HudEntry entry : entries) {
                boolean active = entry.phase() == EventPhase.ACTIVE.ordinal();
                int age = entry.phaseAge() + since;
                if (active && age < ConsoleRows.BANNER_TICKS && settings.on(Toggle.BANNER) && banner == null) {
                    banner = entry;
                    continue;
                }
                if (settings.on(Toggle.COUNTDOWN)) {
                    pillStack.add(pill(font, entry, since));
                }
            }
            Optional<ClientStormState.View> storm = ClientStormState.view(dimension);
            if (storm.isPresent() && storm.get().exposed() && settings.on(Toggle.COUNTDOWN)) {
                pillStack.add(shelter(font, storm.get().variant()));
            }
            Anchor pillAnchor = settings.anchor(Element.PILL);
            int stackBottom = drawStack(g, font, pillStack, pillAnchor, screenW, screenH);

            HudEntry focus = firstActive(entries);
            if (focus != null && settings.on(Toggle.OBJECTIVES)) {
                List<Component> lines = objectives(focus);
                if (!lines.isEmpty()) {
                    Anchor anchor = settings.anchor(Element.OBJECTIVES);
                    List<Block> blocks = new ArrayList<>();
                    for (Component line : lines) {
                        blocks.add(new Block(EventColours.severityOf(focus.severity()), line, null, OBJECTIVE_W,
                                OBJECTIVE_LINE, false));
                    }
                    if (anchor == pillAnchor && !pillStack.isEmpty()) {
                        drawStackFrom(g, font, blocks, anchor, screenW, stackBottom);
                    } else {
                        drawStack(g, font, blocks, anchor, screenW, screenH);
                    }
                }
            }
            HudEntry bannerEntry = banner;
            if (bannerEntry != null) {
                drawBanner(g, font, bannerEntry, since, settings.anchor(Element.BANNER), screenW, screenH);
            }
            if (settings.on(Toggle.MARKER)) {
                for (HudEntry entry : entries) {
                    if (entry.marker() && entry.phase() == EventPhase.ACTIVE.ordinal()) {
                        drawMarker(g, font, mc, player, entry, screenW, screenH);
                        break;
                    }
                }
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    private static HudEntry firstActive(List<HudEntry> entries) {
        for (HudEntry entry : entries) {
            if (entry.phase() == EventPhase.ACTIVE.ordinal()) {
                return entry;
            }
        }
        return null;
    }

    /** One HUD box: severity, first line, optional second line, minimum size. */
    private record Block(Severity severity, Component line, Component second, int minW, int h, boolean glyph) {
    }

    private static Block pill(Font font, HudEntry entry, int since) {
        long left = Math.max(0L, (long) entry.ticksLeft() - since);
        Component title = ConsoleText.titleLine(entry.titleKey(), entry.dimension());
        boolean active = entry.phase() == EventPhase.ACTIVE.ordinal();
        Component line = active
                ? Component.translatable("hud.neroevents.pill.active", title, ConsoleText.clock(left))
                : Component.translatable("hud.neroevents.pill.upcoming", title, ConsoleText.clock(left));
        Component second = null;
        int h = PILL_H;
        if (entry.votingOpen()) {
            second = Component.translatable("hud.neroevents.pill.vote", entry.yes(), entry.no());
            h += VOTE_LINE_H;
        }
        return new Block(EventColours.severityOf(entry.severity()), line, second, PILL_W, h, true);
    }

    private static Block shelter(Font font, StormVariant variant) {
        Component name = Component.translatable("hud.neroevents.storm.name." + variant.serializedName());
        return new Block(Severity.HAZARD, Component.translatable("hud.neroevents.storm.shelter", name), null, PILL_W,
                SHELTER_H, false);
    }

    private static List<Component> objectives(HudEntry entry) {
        List<Component> out = new ArrayList<>();
        if (!entry.objective().isEmpty()) {
            out.add(Component.literal(entry.objective()));
        }
        if (entry.remaining() >= 0 && entry.progressTotal() > 0) {
            int wave = Math.min(entry.progressTotal(), entry.progressDone() + 1);
            out.add(Component.translatable("hud.neroevents.objective.wave", wave, entry.progressTotal(),
                    entry.remaining()));
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private static int blockWidth(Font font, Block block) {
        int text = font.width(block.line());
        Component second = block.second();
        if (second != null) {
            text = Math.max(text, font.width(second));
        }
        int pad = block.glyph() ? 2 + 3 + Hull.GLYPH + 4 + 4 : 2 + 4 + 4;
        return Math.max(block.minW(), text + pad);
    }

    /** Draws a stack from its anchor; returns the y just past the stack (downward) or its top (upward). */
    private static int drawStack(GuiGraphicsExtractor g, Font font, List<Block> blocks, Anchor anchor, int screenW,
            int screenH) {
        if (blocks.isEmpty()) {
            return anchor.y(screenH, 0);
        }
        int total = 0;
        for (Block block : blocks) {
            total += block.h() + GAP;
        }
        int y = anchor.y(screenH, total - GAP);
        for (Block block : blocks) {
            int w = blockWidth(font, block);
            drawBlock(g, font, block, anchor.x(screenW, w), y, w);
            y += block.h() + GAP;
        }
        return y;
    }

    private static void drawStackFrom(GuiGraphicsExtractor g, Font font, List<Block> blocks, Anchor anchor,
            int screenW, int startY) {
        int y = startY;
        for (Block block : blocks) {
            int w = blockWidth(font, block);
            drawBlock(g, font, block, anchor.x(screenW, w), y, w);
            y += block.h() + GAP;
        }
    }

    private static void drawBlock(GuiGraphicsExtractor g, Font font, Block block, int x, int y, int w) {
        int h = block.h();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EventColours.EDGE);
        g.fill(x, y, x + w, y + h, EventColours.HUD_BODY);
        g.fill(x, y, x + 2, y + h, EventColours.opaque(EventColours.severity(block.severity())));
        int tx = x + 5;
        if (block.glyph()) {
            Hull.severityGlyph(g, tx, y + 4, block.severity());
            tx += Hull.GLYPH + 4;
        }
        int ty = block.glyph() ? y + 4 : y + 2;
        WidgetText.drawFitted(g, font, block.line(), tx, ty, x + w - 4 - tx, EventColours.TITLE);
        Component second = block.second();
        if (second != null) {
            WidgetText.drawFitted(g, font, second, tx, ty + VOTE_LINE_H, x + w - 4 - tx, EventColours.TITLE);
        }
    }

    private static void drawBanner(GuiGraphicsExtractor g, Font font, HudEntry entry, int since, Anchor anchor,
            int screenW, int screenH) {
        int age = entry.phaseAge() + since;
        int w = BANNER_W;
        int x = anchor.x(screenW, w);
        int y = anchor.y(screenH, BANNER_H);
        int offset = 0;
        if (age < SLIDE_TICKS) {
            offset = -(BANNER_H + HudSettings.MARGIN) * (SLIDE_TICKS - age) / SLIDE_TICKS;
        } else if (age > ConsoleRows.BANNER_TICKS - SLIDE_TICKS) {
            offset = -(BANNER_H + HudSettings.MARGIN) * (age - (ConsoleRows.BANNER_TICKS - SLIDE_TICKS)) / SLIDE_TICKS;
        }
        y += anchor.stacksDown() ? offset : -offset;
        Severity severity = EventColours.severityOf(entry.severity());
        g.fill(x - 1, y - 1, x + w + 1, y + BANNER_H + 1, EventColours.EDGE);
        g.fill(x, y, x + w, y + BANNER_H, EventColours.HUD_BODY);
        g.fill(x, y, x + 2, y + BANNER_H, EventColours.opaque(EventColours.severity(severity)));
        Hull.severityGlyph(g, x + 5, y + 4, severity);
        long left = Math.max(0L, (long) entry.ticksLeft() - since);
        WidgetText.drawFitted(g, font, ConsoleText.titleLine(entry.titleKey(), entry.dimension()), x + 17, y + 4,
                w - 21, EventColours.TITLE);
        WidgetText.drawFitted(g, font, ConsoleText.whatToDo(entry.definitionTodoKey(), entry.todoKey(), "",
                ConsoleText.timeLeft(left)), x + 17, y + 15, w - 21, EventColours.TITLE);
    }

    private static void drawVignette(GuiGraphicsExtractor g, HudSettings settings, Identifier dimension, int w, int h) {
        if (!settings.on(Toggle.STORM_VIGNETTE) || !ClientStormState.exposed()) {
            return;
        }
        Optional<ClientStormState.View> storm = ClientStormState.view(dimension);
        if (storm.isEmpty()) {
            return;
        }
        StormVariant variant = storm.get().variant();
        int colour;
        int thickness;
        if (variant == StormVariant.SOLAR) {
            colour = EventColours.argb(EventColours.HAZ_Y, 0xA0);
            thickness = 2;
        } else if (variant == StormVariant.CRYO) {
            colour = EventColours.argb(EventColours.CRYO, 0x90);
            thickness = 1 + exposedTicks / 120;
        } else {
            return;
        }
        g.fill(0, 0, w, thickness, colour);
        g.fill(0, h - thickness, w, h, colour);
        g.fill(0, thickness, thickness, h - thickness, colour);
        g.fill(w - thickness, thickness, w, h - thickness, colour);
    }

    private static void drawMarker(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer player, HudEntry entry,
            int w, int h) {
        double ex = player.getX();
        double ey = player.getEyeY();
        double ez = player.getZ();
        double dx = entry.markerPos().getX() + 0.5D - ex;
        double dy = entry.markerPos().getY() + 1.0D - ey;
        double dz = entry.markerPos().getZ() + 0.5D - ez;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = player.getViewYRot(1.0F);
        float pitch = player.getViewXRot(1.0F);
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double targetPitch = -Math.toDegrees(Math.atan2(dy, Math.max(0.001D, horizontal)));
        double deltaYaw = wrap(targetYaw - yaw);
        double deltaPitch = targetPitch - pitch;
        double vfov = Math.toRadians(Math.max(30, mc.options.fov().get()));
        double aspect = w / (double) Math.max(1, h);
        double hfov = 2.0D * Math.atan(Math.tan(vfov / 2.0D) * aspect);
        boolean behind = Math.abs(deltaYaw) >= 90.0D;
        double sx = w / 2.0D + Math.tan(Math.toRadians(Math.max(-89, Math.min(89, deltaYaw)))) / Math.tan(hfov / 2.0D)
                * w / 2.0D;
        double sy = h / 2.0D + Math.tan(Math.toRadians(Math.max(-89, Math.min(89, deltaPitch))))
                / Math.tan(vfov / 2.0D) * h / 2.0D;
        boolean clamped = behind || sx < MARKER_MARGIN || sx > w - MARKER_MARGIN || sy < MARKER_MARGIN
                || sy > h - MARKER_MARGIN;
        if (behind) {
            sx = deltaYaw > 0 ? w - MARKER_MARGIN : MARKER_MARGIN;
        }
        int x = (int) Math.round(Math.max(MARKER_MARGIN, Math.min(w - MARKER_MARGIN, sx)));
        int y = (int) Math.round(Math.max(MARKER_MARGIN, Math.min(h - MARKER_MARGIN, sy)));
        Severity severity = EventColours.severityOf(entry.severity());
        Hull.severityGlyph(g, x - Hull.GLYPH / 2, y - Hull.GLYPH / 2, severity);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Component text = Component.translatable("hud.neroevents.marker.distance", Math.round(distance));
        int tw = font.width(text);
        g.text(font, text, x - tw / 2, y + Hull.GLYPH / 2 + 2, EventColours.TITLE, true);
        if (clamped) {
            String chevron = x >= w - MARKER_MARGIN ? ">" : x <= MARKER_MARGIN ? "<" : (y <= MARKER_MARGIN ? "^" : "v");
            g.text(font, chevron, x - font.width(chevron) / 2, y - Hull.GLYPH / 2 - 10, EventColours.TITLE, true);
        }
    }

    private static double wrap(double degrees) {
        double d = degrees % 360.0D;
        if (d >= 180.0D) {
            d -= 360.0D;
        }
        if (d < -180.0D) {
            d += 360.0D;
        }
        return d;
    }
}
