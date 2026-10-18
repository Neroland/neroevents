package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.neroevents.client.HudSettings.Anchor;
import za.co.neroland.neroevents.client.HudSettings.Element;
import za.co.neroland.neroevents.client.HudSettings.Toggle;

/** The HUD settings: nine snap anchors, the scale ladder, safe defaults and a file round trip. */
class HudSettingsTest {

    @Test
    void defaultsShowEverythingAtTopCentre() {
        HudSettings s = new HudSettings();
        for (Element e : Element.values()) {
            assertEquals(Anchor.TOP_CENTRE, s.anchor(e));
        }
        for (Toggle t : Toggle.values()) {
            assertTrue(s.on(t));
        }
        assertEquals(1.0F, s.scale());
    }

    @Test
    void dropsSnapToTheNearestOfNineAnchors() {
        int w = 132;
        int h = 160;
        assertEquals(Anchor.TOP_LEFT, Anchor.nearest(3, 4, w, h));
        assertEquals(Anchor.TOP_CENTRE, Anchor.nearest(70, 10, w, h));
        assertEquals(Anchor.CENTRE, Anchor.nearest(60, 85, w, h));
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.nearest(130, 158, w, h));
        assertEquals(Anchor.MIDDLE_LEFT, Anchor.nearest(2, 80, w, h));
        assertEquals(Optional.of(Anchor.CENTRE), Anchor.within(66 + 4, 80 - 4, w, h, 6));
        assertEquals(Optional.empty(), Anchor.within(30, 40, w, h, 6), "no magnet outside the 6-px radius");
    }

    @Test
    void anchorsKeepElementsOnScreen() {
        for (Anchor a : Anchor.values()) {
            int x = a.x(640, 120);
            int y = a.y(360, 16);
            assertTrue(x >= HudSettings.MARGIN && x + 120 <= 640 - HudSettings.MARGIN, a + " x");
            assertTrue(y >= HudSettings.MARGIN && y + 16 <= 360 - HudSettings.MARGIN, a + " y");
        }
        assertEquals(HudSettings.MARGIN, Anchor.TOP_LEFT.x(640, 120));
        assertEquals((640 - 120) / 2, Anchor.TOP_CENTRE.x(640, 120));
    }

    @Test
    void theScaleLadderClampsAndRoundsToASteps() {
        HudSettings s = new HudSettings();
        s.setScaleIndex(9);
        assertEquals(1.25F, s.scale());
        s.setScaleIndex(-3);
        assertEquals(0.75F, s.scale());
        Properties p = new Properties();
        p.setProperty("scale", "1.2");
        s.load(p);
        assertEquals(1.25F, s.scale());
    }

    @Test
    void settingsRoundTripThroughTheFile(@TempDir Path dir) throws IOException {
        HudSettings s = new HudSettings();
        s.setAnchor(Element.PILL, Anchor.BOTTOM_LEFT);
        s.setAnchor(Element.OBJECTIVES, Anchor.MIDDLE_RIGHT);
        s.set(Toggle.MARKER, false);
        s.set(Toggle.IMPACT_FLASH, false);
        s.setScaleIndex(0);
        Path file = dir.resolve(HudSettings.FILE_NAME);
        assertTrue(s.write(file));
        HudSettings back = HudSettings.read(file);
        assertEquals(Anchor.BOTTOM_LEFT, back.anchor(Element.PILL));
        assertEquals(Anchor.MIDDLE_RIGHT, back.anchor(Element.OBJECTIVES));
        assertEquals(Anchor.TOP_CENTRE, back.anchor(Element.BANNER));
        assertFalse(back.on(Toggle.MARKER));
        assertFalse(back.on(Toggle.IMPACT_FLASH));
        assertTrue(back.on(Toggle.COUNTDOWN));
        assertEquals(0.75F, back.scale());
        String text = Files.readString(file);
        assertFalse(text.contains("uuid") || text.contains("player"), "the HUD file holds preferences only");
    }

    @Test
    void aBrokenFileGivesTheDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(HudSettings.FILE_NAME);
        Files.writeString(file, "anchor.pill=SIDEWAYS\nshow.countdown=maybe\nscale=huge\n");
        HudSettings s = HudSettings.read(file);
        assertEquals(Anchor.TOP_CENTRE, s.anchor(Element.PILL));
        assertTrue(s.on(Toggle.COUNTDOWN));
        assertEquals(1.0F, s.scale());
        assertEquals(Anchor.TOP_CENTRE, HudSettings.read(dir.resolve("missing.properties")).anchor(Element.PILL));
    }
}
