package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.command.GalleryLayout.Exhibit;

/** Pins the gallery floor plan: everything on the floor, nothing overlapping, every block and entity shown. */
class GalleryLayoutTest {

    @Test
    void everyExhibitStandsOnTheFloorAndInsideTheClearBox() {
        for (Exhibit e : GalleryLayout.all()) {
            assertTrue(GalleryLayout.onFloor(e.at().dx(), e.at().dz()), e.id());
            assertTrue(e.at().dy() >= 1 && e.at().dy() <= GalleryLayout.CLEAR_DY - 2, e.id());
            assertFalse(e.label().isBlank(), e.id());
        }
        assertTrue(GalleryLayout.onFloor(GalleryLayout.MARKER.dx(), GalleryLayout.MARKER.dz()));
    }

    @Test
    void noTwoExhibitsShareACell() {
        Set<String> cells = new HashSet<>();
        for (Exhibit e : GalleryLayout.all()) {
            assertTrue(cells.add(e.at().dx() + "," + e.at().dy() + "," + e.at().dz()), e.id());
        }
    }

    @Test
    void showsEveryBlockAndEveryEntity() {
        Set<String> ids = new HashSet<>();
        GalleryLayout.all().forEach(e -> ids.add(e.id()));
        for (String id : List.of("meteor_slag", "cooled_crust", "embedded_meteor_ore", "crater_pocket", "hull_plating",
                "scorched_panel", "bent_strut", "salvage_crate", "salvage_crate_open", "event_console",
                "event_console_active", "war_beacon", "supply_pod_t1", "supply_pod_t2", "supply_pod_t3",
                "supply_pod_descending", "meteor", "debris")) {
            assertTrue(ids.contains(id), id);
        }
    }

    @Test
    void lampsNeverSitUnderAnExhibitOrTheMarker() {
        int lamps = 0;
        for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
            for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                if (!GalleryLayout.lamp(dx, dz)) {
                    continue;
                }
                lamps++;
                for (Exhibit e : GalleryLayout.all()) {
                    assertFalse(e.at().dx() == dx && e.at().dz() == dz, e.id());
                }
            }
        }
        assertTrue(lamps > 10, "the floor is lit");
        assertFalse(GalleryLayout.lamp(GalleryLayout.MAX_DX + 2, -4), "nothing outside the floor");
        assertEquals("neroevents_gallery", GalleryLayout.ENTITY_TAG);
    }
}
