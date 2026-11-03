package za.co.neroland.neroevents.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * The player's own HUD preferences: where each element sits (one of nine snap anchors plus a free offset from
 * it), which elements show, and the scale ladder. Client-local only — written to {@code config/neroevents-hud.properties} and never sent
 * to a server. Pure Java (no Minecraft client classes): the snapping and the file round trip are unit-tested.
 */
public final class HudSettings {

    /** The file name in the loader's config directory. */
    public static final String FILE_NAME = "neroevents-hud.properties";
    /** The scale ladder. */
    public static final float[] SCALES = {0.75F, 1.0F, 1.25F};
    /** Pixels kept clear of the screen edge. */
    public static final int MARGIN = 4;
    /**
     * Largest offset from an anchor along either axis, in thousandths of the screen. Offsets are stored as a
     * fraction of the screen rather than pixels so a placement survives a window resize or GUI-scale change.
     */
    public static final int MAX_OFFSET = 1000;

    /** The nine snap points: corners, edge centres and centre. */
    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTRE(1, 0), TOP_RIGHT(2, 0),
        MIDDLE_LEFT(0, 1), CENTRE(1, 1), MIDDLE_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2), BOTTOM_CENTRE(1, 2), BOTTOM_RIGHT(2, 2);

        private final int col;
        private final int row;

        Anchor(int col, int row) {
            this.col = col;
            this.row = row;
        }

        /** The translation key of the anchor's name ("Top centre"). */
        public String nameKey() {
            return "hud.neroevents.anchor." + name().toLowerCase(Locale.ROOT);
        }

        /** Left edge of a {@code w}-wide element on a {@code screenW}-wide screen. */
        public int x(int screenW, int w) {
            int room = Math.max(0, screenW - w - 2 * MARGIN);
            return MARGIN + room * col / 2;
        }

        /** Top edge of an {@code h}-tall element on a {@code screenH}-tall screen. */
        public int y(int screenH, int h) {
            int room = Math.max(0, screenH - h - 2 * MARGIN);
            return MARGIN + room * row / 2;
        }

        /** The anchor point itself on a {@code w} x {@code h} screen (where a centred element's centre goes). */
        public int pointX(int w) {
            return w * col / 2;
        }

        public int pointY(int h) {
            return h * row / 2;
        }

        /** Whether elements stack downward from this anchor (top and middle rows) or upward (bottom row). */
        public boolean stacksDown() {
            return row < 2;
        }

        public static Optional<Anchor> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        /** The anchor whose point is nearest {@code (cx, cy)} on a {@code w} x {@code h} screen. */
        public static Anchor nearest(double cx, double cy, int w, int h) {
            Anchor best = CENTRE;
            double bestD = Double.MAX_VALUE;
            for (Anchor anchor : values()) {
                double dx = cx - anchor.pointX(w);
                double dy = cy - anchor.pointY(h);
                double d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = anchor;
                }
            }
            return best;
        }

        /** The anchor within {@code radius} of {@code (cx, cy)}, if any (the magnet while dragging). */
        public static Optional<Anchor> within(double cx, double cy, int w, int h, int radius) {
            Anchor near = nearest(cx, cy, w, h);
            double dx = cx - near.pointX(w);
            double dy = cy - near.pointY(h);
            return dx * dx + dy * dy <= (double) radius * radius ? Optional.of(near) : Optional.empty();
        }
    }

    /** The three movable elements. The world marker follows the world, not an anchor. */
    public enum Element {
        PILL, BANNER, OBJECTIVES;

        public String nameKey() {
            return "hud.neroevents.element." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The six on/off switches, in screen order. */
    public enum Toggle {
        COUNTDOWN, BANNER, OBJECTIVES, MARKER, IMPACT_FLASH, STORM_VIGNETTE;

        public String nameKey() {
            return "hud.neroevents.toggle." + name().toLowerCase(Locale.ROOT);
        }

        String property() {
            return "show." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Map<Element, Anchor> anchors = new EnumMap<>(Element.class);
    private final Map<Toggle, Boolean> toggles = new EnumMap<>(Toggle.class);
    private final Map<Element, int[]> offsets = new EnumMap<>(Element.class);
    private int scaleIndex = 1;

    public HudSettings() {
        reset();
    }

    /** Back to the defaults: everything shown, top centre (no offset), scale 1.0. */
    public void reset() {
        for (Element element : Element.values()) {
            anchors.put(element, Anchor.TOP_CENTRE);
            offsets.put(element, new int[] {0, 0});
        }
        for (Toggle toggle : Toggle.values()) {
            toggles.put(toggle, true);
        }
        scaleIndex = 1;
    }

    public Anchor anchor(Element element) {
        return anchors.getOrDefault(element, Anchor.TOP_CENTRE);
    }

    /** Snaps {@code element} exactly onto {@code anchor} (clears any free offset). */
    public void setAnchor(Element element, Anchor anchor) {
        setPlacement(element, anchor, 0, 0);
    }

    /** Puts {@code element} at {@code anchor} moved by ({@code dx}, {@code dy}) thousandths of the screen. */
    public void setPlacement(Element element, Anchor anchor, int dx, int dy) {
        if (element != null && anchor != null) {
            anchors.put(element, anchor);
            offsets.put(element, new int[] {clampOffset(dx), clampOffset(dy)});
        }
    }

    /** Horizontal offset from the anchor, in thousandths of the screen width. */
    public int offsetX(Element element) {
        return offsets.getOrDefault(element, new int[] {0, 0})[0];
    }

    /** Vertical offset from the anchor, in thousandths of the screen height. */
    public int offsetY(Element element) {
        return offsets.getOrDefault(element, new int[] {0, 0})[1];
    }

    /** Whether {@code element} sits off its anchor (moved freely or nudged). */
    public boolean isFree(Element element) {
        return offsetX(element) != 0 || offsetY(element) != 0;
    }

    /** Whether two elements share one spot (same anchor, same offset), so they stack instead of overlapping. */
    public boolean sharesSpot(Element a, Element b) {
        return anchor(a) == anchor(b) && offsetX(a) == offsetX(b) && offsetY(a) == offsetY(b);
    }

    /** Left edge of a {@code w}-wide {@code element} on a {@code screenW}-wide screen, kept fully on screen. */
    public int x(Element element, int screenW, int w) {
        int x = anchor(element).x(screenW, w) + Math.round(offsetX(element) * screenW / (float) MAX_OFFSET);
        return clamp(x, screenW, w);
    }

    /** Top edge of an {@code h}-tall {@code element} on a {@code screenH}-tall screen, kept fully on screen. */
    public int y(Element element, int screenH, int h) {
        int y = anchor(element).y(screenH, h) + Math.round(offsetY(element) * screenH / (float) MAX_OFFSET);
        return clamp(y, screenH, h);
    }

    /**
     * Places {@code element} where it was dropped: its top-left at ({@code x}, {@code y}) for a {@code ew} x
     * {@code eh} element on a {@code screenW} x {@code screenH} screen. It belongs to the nearest anchor; inside
     * {@code snapRadius} of that anchor it snaps onto it, otherwise it keeps the exact drop spot as an offset.
     */
    public void dropAt(Element element, double x, double y, int ew, int eh, int screenW, int screenH,
            int snapRadius) {
        double cx = x + ew / 2.0;
        double cy = y + eh / 2.0;
        Anchor near = Anchor.nearest(cx, cy, screenW, screenH);
        if (Anchor.within(cx, cy, screenW, screenH, snapRadius).isPresent()) {
            setAnchor(element, near);
            return;
        }
        int dx = (int) Math.round((x - near.x(screenW, ew)) * MAX_OFFSET / Math.max(1, screenW));
        int dy = (int) Math.round((y - near.y(screenH, eh)) * MAX_OFFSET / Math.max(1, screenH));
        setPlacement(element, near, dx, dy);
    }

    /**
     * Moves {@code element} by ({@code px}, {@code py}) pixels of a {@code screenW} x {@code screenH} screen
     * (the arrow keys). Always moves at least one step in the pressed direction.
     */
    public void nudge(Element element, int px, int py, int screenW, int screenH) {
        if (element == null) {
            return;
        }
        int dx = step(px, screenW);
        int dy = step(py, screenH);
        setPlacement(element, anchor(element), offsetX(element) + dx, offsetY(element) + dy);
    }

    private static int step(int pixels, int size) {
        if (pixels == 0) {
            return 0;
        }
        int step = Math.round(pixels * MAX_OFFSET / (float) Math.max(1, size));
        return step != 0 ? step : Integer.signum(pixels);
    }

    private static int clamp(int pos, int screen, int size) {
        int max = Math.max(0, screen - size);
        return Math.max(0, Math.min(max, pos));
    }

    private static int clampOffset(int value) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }

    public boolean on(Toggle toggle) {
        return toggles.getOrDefault(toggle, true);
    }

    public void set(Toggle toggle, boolean value) {
        if (toggle != null) {
            toggles.put(toggle, value);
        }
    }

    public int scaleIndex() {
        return scaleIndex;
    }

    public float scale() {
        return SCALES[scaleIndex];
    }

    public void setScaleIndex(int index) {
        this.scaleIndex = Math.max(0, Math.min(SCALES.length - 1, index));
    }

    // --- persistence ------------------------------------------------------------------------------------

    /** Writes every value into {@code out}. */
    public void store(Properties out) {
        for (Element element : Element.values()) {
            out.setProperty("anchor." + element.name().toLowerCase(Locale.ROOT), anchor(element).name());
            out.setProperty("offset." + element.name().toLowerCase(Locale.ROOT),
                    offsetX(element) + "," + offsetY(element));
        }
        for (Toggle toggle : Toggle.values()) {
            out.setProperty(toggle.property(), Boolean.toString(on(toggle)));
        }
        out.setProperty("scale", Float.toString(scale()));
    }

    /** Reads {@code in}; a missing or unreadable value keeps its default. */
    public void load(Properties in) {
        reset();
        for (Element element : Element.values()) {
            Anchor.parse(in.getProperty("anchor." + element.name().toLowerCase(Locale.ROOT)))
                    .ifPresent(a -> anchors.put(element, a));
            int[] offset = parseOffset(in.getProperty("offset." + element.name().toLowerCase(Locale.ROOT)));
            if (offset != null) {
                offsets.put(element, offset);
            }
        }
        for (Toggle toggle : Toggle.values()) {
            String raw = in.getProperty(toggle.property());
            if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                toggles.put(toggle, Boolean.parseBoolean(raw));
            }
        }
        String scale = in.getProperty("scale");
        if (scale != null) {
            try {
                float wanted = Float.parseFloat(scale.trim());
                int best = 1;
                for (int i = 0; i < SCALES.length; i++) {
                    if (Math.abs(SCALES[i] - wanted) < Math.abs(SCALES[best] - wanted)) {
                        best = i;
                    }
                }
                scaleIndex = best;
            } catch (NumberFormatException ignored) {
                scaleIndex = 1;
            }
        }
    }

    /** "dx,dy" in thousandths of the screen; null when missing or unreadable (the offset stays zero). */
    private static int[] parseOffset(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {clampOffset(Integer.parseInt(parts[0].trim())),
                    clampOffset(Integer.parseInt(parts[1].trim()))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Loads from {@code file}; a missing or unreadable file gives the defaults. */
    public static HudSettings read(Path file) {
        HudSettings settings = new HudSettings();
        if (file == null || !Files.isRegularFile(file)) {
            return settings;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            settings.load(props);
        } catch (IOException | RuntimeException e) {
            settings.reset();
        }
        return settings;
    }

    /** Saves to {@code file}; returns whether it was written. */
    public boolean write(Path file) {
        if (file == null) {
            return false;
        }
        Properties props = new Properties();
        store(props);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "NeroEvents HUD settings (this computer only; never sent to a server)");
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** A copy (for the settings screen's "changed?" check). */
    public HudSettings copy() {
        HudSettings out = new HudSettings();
        Properties props = new Properties();
        store(props);
        out.load(props);
        return out;
    }
}
