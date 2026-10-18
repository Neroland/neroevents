package za.co.neroland.neroevents.client;

import za.co.neroland.neroevents.event.Severity;

/**
 * The NeroEvents palette in one place: the family metals and glass, the NeroEvents world colours, the five
 * severity colours and the GUI chrome, plus the WCAG helpers {@code EventColoursTest} checks every pair with.
 *
 * <p>Two conventions, chosen by use (the NeroSecurity convention):
 * <ul>
 *   <li><b>World / texture palette</b> constants are {@code 0xRRGGBB} (no alpha); the texture generator's palette
 *       mirrors them byte for byte. Renderers add alpha with {@link #argb(int, int)}.</li>
 *   <li><b>GUI chrome</b> constants are {@code 0xAARRGGBB}, opaque unless noted, ready for {@code fill}/{@code text}.</li>
 * </ul>
 *
 * <p>Severity is the one colour code a player must learn, so it never travels alone: every severity also has a
 * glyph ({@link #glyphSlot}) and a word. {@link #EVENT_MAGENTA} is the mod's own accent and is deliberately not a
 * severity colour. Pure Java (no Minecraft client classes), so it is unit-testable.
 */
public final class EventColours {

    // ---- family metals and glass (0xRRGGBB) ----
    public static final int G_DARK = 0x1A1D22;
    public static final int GRAPHITE = 0x2C3137;
    public static final int GRAPHITE_2 = 0x33393F;
    public static final int GRAPHITE_4 = 0x3A4047;
    public static final int G_LIGHT = 0x8E98A4;
    public static final int TRIM = 0x5B6670;
    public static final int STEEL = 0x7A8492;
    public static final int STEEL_DK = 0x424A56;
    public static final int NERO_ALLOY = 0x26A69A;
    public static final int PLASMA = 0x60D4E8;
    public static final int GLASS = 0x0B1119;
    public static final int HAZ_Y = 0xFFCE2C;
    public static final int HAZ_K = 0x18181E;
    public static final int GOLD = 0xD9B24C;

    // ---- NeroEvents world palette (0xRRGGBB) ----
    public static final int EVENT_MAGENTA = 0xDC54B0;
    public static final int EVENT_MAGENTA_BRIGHT = 0xFF9CE0;
    public static final int MAGMA = 0xFF6A1A;
    public static final int MAGMA_HOT = 0xFFD27A;
    public static final int SLAG = 0x3B2F2E;
    public static final int SLAG_DK = 0x231B1A;
    public static final int CRUST = 0x5A4C44;
    public static final int EMBER = 0xFF8C3A;
    public static final int SCORCH = 0x141214;
    public static final int RUST = 0x8A4B2E;
    public static final int CANOPY = 0xE8EDF2;
    public static final int DUST = 0xC9A066;
    public static final int ION = 0x7FD7FF;
    public static final int CRYO = 0xBFE9FF;
    public static final int SOLAR = 0xFFB347;

    // ---- severity (0xRRGGBB) ----
    public static final int SEV_INFO = 0x4FC3FF;
    public static final int SEV_REWARD = 0xF4C542;
    public static final int SEV_HAZARD = 0xFF9A3C;
    public static final int SEV_COMBAT = 0xFF4D4D;
    public static final int SEV_WAR = 0xB388FF;
    /** History rows, cancelled, skipped (= {@link #SUBTLE}). */
    public static final int SEV_DONE = 0x8DA0B4;

    // ---- glyph sheet slots (textures/gui/event_glyphs.png, 64x16, eight 8x8 slots on the top row) ----
    public static final int GLYPH_INFO = 0;
    public static final int GLYPH_REWARD = 1;
    public static final int GLYPH_HAZARD = 2;
    public static final int GLYPH_COMBAT = 3;
    public static final int GLYPH_WAR = 4;
    public static final int GLYPH_DONE = 5;
    public static final int GLYPH_VOTE = 6;
    public static final int GLYPH_CLOCK = 7;

    /** Packed light for emissive quads (sky 15, block 15). */
    public static final int FULL_BRIGHT = 0x00F000F0;

    // ---- GUI chrome (0xAARRGGBB) ----
    public static final int PANEL = 0xFF141C26;
    public static final int EDGE = 0xFF2A3A4D;
    public static final int TROUGH = 0xFF0B1119;
    public static final int INK = 0xFF05080D;
    public static final int TITLE = 0xFFD6ECFF;
    public static final int SUBTLE = 0xFF8DA0B4;
    public static final int BUTTON_BODY = 0xFF0C1E2B;
    public static final int BUTTON_HOVER = 0xFF16344A;
    public static final int BUTTON_EDGE = 0xFF2E4A5A;
    public static final int BUTTON_TEXT = 0xFFCFE7FF;
    public static final int BUTTON_TEXT_HOT = 0xFFFFFFFF;
    public static final int EDGE_HOT = 0xFF5F8FAD;
    public static final int SELECTED_BODY = 0xFF123042;
    public static final int DANGER_BODY = 0xFF3A1216;
    public static final int DANGER_HOT = 0xFF561A20;
    public static final int DANGER_EDGE = 0xFFE04545;
    /** The YES button: a green body with the OK edge. */
    public static final int YES_BODY = 0xFF12301C;
    public static final int YES_HOT = 0xFF1A4428;
    public static final int DENY_TEXT = 0xFFFF7A7A;
    public static final int OK_TEXT = 0xFF7CE08A;
    public static final int DISABLED_TEXT = 0xFF8A94A2;
    public static final int DISABLED_BODY = 0xFF12161F;
    public static final int DISABLED_EDGE = 0xFF262B33;
    /** The dim laid over the whole screen behind a confirm panel. */
    public static final int MODAL_DIM = 0xC0000000;
    /** HUD body alpha over {@link #INK}. */
    public static final int HUD_ALPHA = 0xC0;
    /** The HUD element body: {@link #INK} at {@link #HUD_ALPHA}. */
    public static final int HUD_BODY = (HUD_ALPHA << 24) | (INK & 0xFFFFFF);

    private EventColours() {
    }

    /** The severity's colour, {@code 0xRRGGBB}; {@code null} is the neutral DONE grey. */
    public static int severity(Severity severity) {
        if (severity == null) {
            return SEV_DONE;
        }
        return switch (severity) {
            case INFO -> SEV_INFO;
            case REWARD -> SEV_REWARD;
            case HAZARD -> SEV_HAZARD;
            case COMBAT -> SEV_COMBAT;
            case WAR -> SEV_WAR;
        };
    }

    /** The severity a wire byte names, or {@code null} when out of range. */
    public static Severity severityOf(int ordinal) {
        Severity[] all = Severity.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /** The glyph sheet slot of a severity ({@code null}: the DONE square). Every severity has its own shape. */
    public static int glyphSlot(Severity severity) {
        if (severity == null) {
            return GLYPH_DONE;
        }
        return switch (severity) {
            case INFO -> GLYPH_INFO;
            case REWARD -> GLYPH_REWARD;
            case HAZARD -> GLYPH_HAZARD;
            case COMBAT -> GLYPH_COMBAT;
            case WAR -> GLYPH_WAR;
        };
    }

    /** The severity word's translation key (shown next to the colour on every console row). */
    public static String wordKey(Severity severity) {
        return severity == null ? "severity.neroevents.done"
                : "severity.neroevents." + severity.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The word on a severity pill: always dark ink on the fill. */
    public static int pillText(Severity severity) {
        return INK;
    }

    /** {@code 0xRRGGBB} plus an alpha 0..255 to {@code 0xAARRGGBB}. */
    public static int argb(int rgb, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Opaque {@code 0xFFRRGGBB} from an {@code 0xRRGGBB} colour. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** WCAG 2.x relative luminance of a packed sRGB colour (alpha ignored). */
    public static double luminance(int colour) {
        return 0.2126 * linear((colour >> 16) & 0xFF)
                + 0.7152 * linear((colour >> 8) & 0xFF)
                + 0.0722 * linear(colour & 0xFF);
    }

    /** WCAG contrast ratio between two packed colours, 1..21. */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** {@code fg} at {@code alpha} (0..255) composited over the opaque {@code bg}, as {@code 0xRRGGBB}. */
    public static int over(int fg, int alpha, int bg) {
        float a = Math.max(0, Math.min(255, alpha)) / 255f;
        int r = Math.round(((fg >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (1f - a));
        int g = Math.round(((fg >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (1f - a));
        int b = Math.round((fg & 0xFF) * a + (bg & 0xFF) * (1f - a));
        return (r << 16) | (g << 8) | b;
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
