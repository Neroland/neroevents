package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.client.GalleryShots.Shot;
import za.co.neroland.neroevents.command.GalleryDemoData;
import za.co.neroland.neroevents.command.GalleryLayout;
import za.co.neroland.neroevents.command.GalleryStations;

/** The capture's shot lists: unique names, every cue and station shot, HUD shots in the gallery, every tab. */
class GalleryShotsTest {

    /**
     * How far outside the footprint a HUD shot's camera may stand. The hero camera sits at {@code dz 4}
     * ({@code MAX_DZ + 6}); the slack keeps a small camera move from failing the test for no visual reason.
     */
    private static final int CAMERA_MARGIN = 8;

    @Test
    void namesAreUniqueAndFileSafe() {
        for (List<Shot> list : List.of(GalleryShots.SHOWCASE, GalleryShots.STRESS)) {
            Set<String> names = new HashSet<>();
            for (Shot shot : list) {
                assertTrue(shot.name().matches("[a-z0-9_]+"), shot.name());
                assertTrue(names.add(shot.name()), "duplicate shot " + shot.name());
                assertTrue(shot.warmup() >= 0, shot.name());
            }
        }
        assertEquals("hero", GalleryShots.SHOWCASE.get(0).name(), "the first shot carries the build");
        assertEquals(9 + 26, GalleryShots.SHOWCASE.size());
    }

    @Test
    void everyCueAndEveryStationIsShot() {
        Set<String> names = new HashSet<>();
        List<String> setups = new ArrayList<>();
        for (Shot shot : GalleryShots.SHOWCASE) {
            names.add(shot.name());
            setups.addAll(shot.setup());
        }
        for (String cue : GalleryStations.CUE_IDS) {
            assertTrue(setups.contains(GalleryShots.CUE + cue), "no shot cues " + cue);
        }
        for (String storm : List.of("solar", "dust", "ion", "cryo", "off")) {
            assertTrue(setups.contains(GalleryShots.STORM + storm), "no shot sets the storm " + storm);
        }
        for (GalleryStations.Station station : GalleryStations.ALL) {
            String shot = GalleryShots.STATION_SHOTS.get(station.id());
            assertTrue(shot != null && names.contains(shot), "no shot for station " + station.id());
        }
    }

    @Test
    void hudShotsLookAtTheGalleryAndConsoleShotsShowEveryTab() {
        List<Integer> tabs = new ArrayList<>();
        for (Shot shot : GalleryShots.SHOWCASE) {
            if (shot.hud()) {
                assertTrue(shot.camera().x() >= GalleryLayout.MIN_DX - CAMERA_MARGIN
                        && shot.camera().x() <= GalleryLayout.MAX_DX + CAMERA_MARGIN, shot.name());
                assertTrue(shot.camera().z() >= GalleryLayout.MIN_DZ - CAMERA_MARGIN
                        && shot.camera().z() <= GalleryLayout.MAX_DZ + CAMERA_MARGIN, shot.name());
                assertTrue(shot.camera().y() >= 0 && shot.camera().y() <= GalleryLayout.CLEAR_DY + CAMERA_MARGIN,
                        shot.name());
            }
            if (shot.tab() >= 0) {
                tabs.add(shot.tab());
            }
        }
        assertEquals(List.of(0, 1, 2, 3, 4), tabs);
        assertEquals("console_privacy", GalleryShots.lastConsoleShot(GalleryShots.SHOWCASE));
        assertEquals(List.of("hero", "stress_budget"), GalleryShots.STRESS.stream().map(Shot::name).toList());
    }

    @Test
    void consecutiveDemoScenesChangeTheHudSignature() {
        Identifier dim = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos origin = new BlockPos(0, 64, 0);
        for (int scene = 0; scene < GalleryDemoData.SCENES; scene++) {
            String a = ClientHudState.signature(GalleryDemoData.hud(scene, 0L, dim, origin).entries());
            String b = ClientHudState.signature(GalleryDemoData.hud(GalleryDemoData.next(scene), 0L, dim, origin)
                    .entries());
            assertNotEquals(a, b, "scene " + scene + " to the next");
        }
    }
}
