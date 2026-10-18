package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.Severity;

/**
 * The severity colour code and every text/background pair the console and HUD draw, against WCAG 2.x: pill words
 * on their fill and body text at AA (4.5:1), glyphs and bands as non-text marks at 3:1, and every severity
 * distinguishable with colour removed (its own glyph and word). The contrast values are the published table.
 */
class EventColoursTest {

    private static final int INK = EventColours.INK & 0xFFFFFF;
    private static final int PANEL = EventColours.PANEL & 0xFFFFFF;
    private static final int APP_SURFACE = 0x0B1018;

    private static void near(double expected, double actual, String what) {
        assertEquals(expected, actual, 0.06, what);
    }

    @Test
    void severityHexMatchesTheTable() {
        assertEquals(0x4FC3FF, EventColours.severity(Severity.INFO));
        assertEquals(0xF4C542, EventColours.severity(Severity.REWARD));
        assertEquals(0xFF9A3C, EventColours.severity(Severity.HAZARD));
        assertEquals(0xFF4D4D, EventColours.severity(Severity.COMBAT));
        assertEquals(0xB388FF, EventColours.severity(Severity.WAR));
        assertEquals(0x8DA0B4, EventColours.severity(null));
        assertEquals(EventColours.SUBTLE & 0xFFFFFF, EventColours.SEV_DONE);
    }

    @Test
    void pillWordsOnSeverityFillsPassAa() {
        near(10.1, EventColours.contrast(EventColours.SEV_INFO, INK), "INFO on INK");
        near(12.3, EventColours.contrast(EventColours.SEV_REWARD, INK), "REWARD on INK");
        near(9.5, EventColours.contrast(EventColours.SEV_HAZARD, INK), "HAZARD on INK");
        near(6.1, EventColours.contrast(EventColours.SEV_COMBAT, INK), "COMBAT on INK");
        near(7.5, EventColours.contrast(EventColours.SEV_WAR, INK), "WAR on INK");
        for (Severity s : Severity.values()) {
            assertTrue(EventColours.contrast(EventColours.severity(s), EventColours.pillText(s)) >= 4.5, s + " pill");
        }
    }

    @Test
    void glyphsAndBandsOnThePanelPassNonTextAa() {
        near(8.7, EventColours.contrast(EventColours.SEV_INFO, PANEL), "INFO on PANEL");
        near(10.6, EventColours.contrast(EventColours.SEV_REWARD, PANEL), "REWARD on PANEL");
        near(8.1, EventColours.contrast(EventColours.SEV_HAZARD, PANEL), "HAZARD on PANEL");
        near(5.3, EventColours.contrast(EventColours.SEV_COMBAT, PANEL), "COMBAT on PANEL");
        near(6.4, EventColours.contrast(EventColours.SEV_WAR, PANEL), "WAR on PANEL");
        for (Severity s : Severity.values()) {
            assertTrue(EventColours.contrast(EventColours.severity(s), PANEL) >= 3.0, s + " mark");
            assertTrue(EventColours.contrast(EventColours.severity(s), EventColours.TROUGH) >= 3.0, s + " on trough");
        }
    }

    @Test
    void appSurfaceValuesMatch() {
        near(9.6, EventColours.contrast(EventColours.SEV_INFO, APP_SURFACE), "INFO on app surface");
        near(11.7, EventColours.contrast(EventColours.SEV_REWARD, APP_SURFACE), "REWARD on app surface");
        near(9.0, EventColours.contrast(EventColours.SEV_HAZARD, APP_SURFACE), "HAZARD on app surface");
        near(5.8, EventColours.contrast(EventColours.SEV_COMBAT, APP_SURFACE), "COMBAT on app surface");
        near(7.2, EventColours.contrast(EventColours.SEV_WAR, APP_SURFACE), "WAR on app surface");
    }

    @Test
    void bodyTextPairsPassAa() {
        near(14.1, EventColours.contrast(EventColours.TITLE, EventColours.PANEL), "TITLE on PANEL");
        near(16.5, EventColours.contrast(EventColours.TITLE, EventColours.INK), "TITLE on INK");
        int[][] pairs = {
                {EventColours.TITLE, EventColours.PANEL},
                {EventColours.SUBTLE, EventColours.PANEL},
                {EventColours.SUBTLE, EventColours.TROUGH},
                {EventColours.OK_TEXT, EventColours.TROUGH},
                {EventColours.DENY_TEXT, EventColours.TROUGH},
                {EventColours.BUTTON_TEXT, EventColours.BUTTON_BODY},
                {EventColours.BUTTON_TEXT_HOT, EventColours.BUTTON_HOVER},
                {EventColours.BUTTON_TEXT_HOT, EventColours.SELECTED_BODY},
                {EventColours.BUTTON_TEXT, EventColours.DANGER_BODY},
                {EventColours.BUTTON_TEXT_HOT, EventColours.DANGER_HOT},
                {EventColours.BUTTON_TEXT, EventColours.YES_BODY},
                {EventColours.BUTTON_TEXT_HOT, EventColours.YES_HOT},
                {EventColours.DISABLED_TEXT, EventColours.DISABLED_BODY},
                {EventColours.INK, EventColours.TITLE}, // the "YOU: YES" pill
        };
        for (int[] pair : pairs) {
            double ratio = EventColours.contrast(pair[0], pair[1]);
            assertTrue(ratio >= 4.5, String.format("%08X on %08X is %.2f", pair[0], pair[1], ratio));
        }
    }

    @Test
    void hudTextStaysLegibleOverABrightSky() {
        // Every HUD text (pill, vote line, banner lines, objectives, shelter line, marker) is drawn in TITLE.
        int worst = EventColours.over(EventColours.INK, EventColours.HUD_ALPHA, 0xFFFFFF);
        assertTrue(EventColours.contrast(EventColours.TITLE, worst) >= 4.5, "TITLE on the HUD body over white");
        for (int sky : new int[] {0xFFFFFF, 0xBFE9FF, 0x87CEEB, 0x000000}) {
            int body = EventColours.over(EventColours.INK, EventColours.HUD_ALPHA, sky);
            assertTrue(EventColours.contrast(EventColours.TITLE, body) >= 4.5,
                    String.format("TITLE on the HUD body over %06X", sky));
        }
    }

    @Test
    void everySeverityIsDistinguishableWithoutColour() {
        Set<Integer> slots = new HashSet<>();
        Set<String> words = new HashSet<>();
        for (Severity s : Severity.values()) {
            assertTrue(slots.add(EventColours.glyphSlot(s)), "shared glyph for " + s);
            assertTrue(words.add(EventColours.wordKey(s)), "shared word for " + s);
        }
        assertTrue(slots.add(EventColours.glyphSlot(null)), "DONE has its own glyph");
        assertEquals(EventColours.GLYPH_DONE, EventColours.glyphSlot(null));
        // The closest colour pair (REWARD gold vs HAZARD amber) differs by glyph.
        assertNotEquals(EventColours.glyphSlot(Severity.REWARD), EventColours.glyphSlot(Severity.HAZARD));
        // INFO blue vs WAR purple differ in luminance by more than 1.3:1.
        assertTrue(EventColours.contrast(EventColours.SEV_INFO, EventColours.SEV_WAR) > 1.3);
    }

    @Test
    void theModAccentIsNotASeverityColour() {
        for (Severity s : Severity.values()) {
            assertNotEquals(EventColours.EVENT_MAGENTA, EventColours.severity(s));
        }
    }

    @Test
    void worldPaletteMirrorsTheTable() {
        int[][] table = {
                {EventColours.G_DARK, 0x1A1D22}, {EventColours.GRAPHITE, 0x2C3137}, {EventColours.GRAPHITE_2, 0x33393F},
                {EventColours.GRAPHITE_4, 0x3A4047}, {EventColours.G_LIGHT, 0x8E98A4}, {EventColours.TRIM, 0x5B6670},
                {EventColours.STEEL, 0x7A8492}, {EventColours.STEEL_DK, 0x424A56}, {EventColours.NERO_ALLOY, 0x26A69A},
                {EventColours.PLASMA, 0x60D4E8}, {EventColours.GLASS, 0x0B1119}, {EventColours.HAZ_Y, 0xFFCE2C},
                {EventColours.HAZ_K, 0x18181E}, {EventColours.GOLD, 0xD9B24C}, {EventColours.EVENT_MAGENTA, 0xDC54B0},
                {EventColours.EVENT_MAGENTA_BRIGHT, 0xFF9CE0}, {EventColours.MAGMA, 0xFF6A1A},
                {EventColours.MAGMA_HOT, 0xFFD27A}, {EventColours.SLAG, 0x3B2F2E}, {EventColours.SLAG_DK, 0x231B1A},
                {EventColours.CRUST, 0x5A4C44}, {EventColours.EMBER, 0xFF8C3A}, {EventColours.SCORCH, 0x141214},
                {EventColours.RUST, 0x8A4B2E}, {EventColours.CANOPY, 0xE8EDF2}, {EventColours.DUST, 0xC9A066},
                {EventColours.ION, 0x7FD7FF}, {EventColours.CRYO, 0xBFE9FF}, {EventColours.SOLAR, 0xFFB347},
        };
        for (int[] row : table) {
            assertEquals(row[1], row[0]);
        }
    }
}
