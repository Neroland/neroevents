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
 * The player's own HUD preferences: where each element sits (one of nine snap anchors), which elements show,
 * and the scale ladder. Client-local only — written to {@code config/neroevents-hud.properties} and never sent
 * to a server. Pure Java (no Minecraft client classes): the snapping and the file round trip are unit-tested.
 */
public final class HudSettings {

    /** The file name in the loader's config directory. */
    public static final String FILE_NAME = "neroevents-hud.properties";
    /** The scale ladder. */
    public static final float[] SCALES = {0.75F, 1.0F, 1.25F};
    /** Pixels kept clear of the screen edge. */
    public static final int MARGIN = 4;

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
    private int scaleIndex = 1;

    public HudSettings() {
        reset();
    }

    /** Back to the defaults: everything shown, top centre, scale 1.0. */
    public void reset() {
        for (Element element : Element.values()) {
            anchors.put(element, Anchor.TOP_CENTRE);
        }
        for (Toggle toggle : Toggle.values()) {
            toggles.put(toggle, true);
        }
        scaleIndex = 1;
    }

    public Anchor anchor(Element element) {
        return anchors.getOrDefault(element, Anchor.TOP_CENTRE);
    }

    public void setAnchor(Element element, Anchor anchor) {
        if (element != null && anchor != null) {
            anchors.put(element, anchor);
        }
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
