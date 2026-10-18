package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.command.GalleryLayout.Box;
import za.co.neroland.neroevents.command.GalleryLayout.Exhibit;
import za.co.neroland.neroevents.command.GalleryLayout.Offset;
import za.co.neroland.neroevents.command.GalleryStations.Station;

/** Pins the gallery floor plan: everything on the floor, nothing overlapping, every block and entity shown. */
class GalleryLayoutTest {

    @Test
    void theFootprintIsSymmetricAndInsideTheClearBox() {
        assertEquals(-GalleryLayout.MIN_DX, GalleryLayout.MAX_DX);
        assertTrue(GalleryLayout.CLEAR_BOX.contains(GalleryLayout.FOOTPRINT));
        assertEquals(0, GalleryLayout.FOOTPRINT.minDy());
    }

    @Test
    void stationBaysSitInsideTheFootprintAndNeverOverlap() {
        List<Station> all = GalleryStations.ALL;
        for (int i = 0; i < all.size(); i++) {
            Station a = all.get(i);
            assertTrue(GalleryLayout.FOOTPRINT.contains(a.bay()), a.id());
            assertTrue(a.bay().contains(a.centre()), a.id() + " centre inside its bay");
            for (int j = i + 1; j < all.size(); j++) {
                assertFalse(a.bay().overlaps(all.get(j).bay()), a.id() + " overlaps " + all.get(j).id());
            }
            for (Exhibit e : GalleryLayout.all()) {
                assertFalse(a.bay().containsColumn(e.at().dx(), e.at().dz()), a.id() + " covers " + e.id());
            }
            assertFalse(a.bay().containsColumn(GalleryLayout.MARKER.dx(), GalleryLayout.MARKER.dz()), a.id());
            for (int dx = a.bay().minDx(); dx <= a.bay().maxDx(); dx++) {
                for (int dz = a.bay().minDz(); dz <= a.bay().maxDz(); dz++) {
                    assertFalse(GalleryLayout.lamp(dx, dz), a.id() + " has a lamp at " + dx + "," + dz);
                }
            }
        }
    }

    @Test
    void theStressSceneStaysInsideTheFootprintAndOffTheLoopColumns() {
        List<Offset> meteors = GalleryLayout.stressMeteors();
        List<Offset> pods = GalleryLayout.stressPods();
        assertEquals(40, meteors.size());
        assertEquals(6, pods.size());
        Set<String> loopColumns = Set.of("-14,-32", "0,-32", "14,-32");
        Set<String> exhibitColumns = new HashSet<>();
        GalleryLayout.all().forEach(e -> exhibitColumns.add(e.at().dx() + "," + e.at().dz()));
        for (Offset o : meteors) {
            assertTrue(GalleryLayout.FOOTPRINT.contains(o), o.toString());
            assertEquals(GalleryLayout.STRESS_METEOR_DY, o.dy(), o.toString());
        }
        for (Offset o : pods) {
            assertTrue(GalleryLayout.FOOTPRINT.contains(o), o.toString());
            assertTrue(o.dy() >= 1, o.toString());
        }
        List<Offset> all = new java.util.ArrayList<>(meteors);
        all.addAll(pods);
        for (Offset o : all) {
            String column = o.dx() + "," + o.dz();
            assertFalse(loopColumns.contains(column), "stress offset on a loop column " + column);
            assertFalse(exhibitColumns.contains(column), "stress offset on an exhibit column " + column);
        }
        Box footprint = GalleryLayout.FOOTPRINT;
        Offset pad = GalleryStations.SUPPLY_DROP.centre();
        assertTrue(footprint.contains(new Offset(pad.dx(), GalleryShowcase.POD_DROP_DY, pad.dz())),
                "the pod drop starts inside");
    }

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
