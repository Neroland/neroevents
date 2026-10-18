package za.co.neroland.neroevents.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The gallery capture's shot lists, pure: a name, the commands that set the scene up (gallery cues, the storm
 * preview), the ticks to wait after them, whether the HUD is shown, which console tab is shown (-1 none) and the
 * camera and target as offsets from where the run started. {@link GalleryCaptureHarness} plays them; the poses
 * mirror {@code GalleryLayout}/{@code GalleryStations}, so tune them together.
 */
public final class GalleryShots {

    /** A point as an offset from the run's origin. */
    public record Pose(double x, double y, double z) {
    }

    /** One shot. */
    public record Shot(String name, List<String> setup, int warmup, boolean hud, int tab, Pose camera, Pose target) {

        public Shot {
            setup = List.copyOf(setup);
        }

        Shot withWarmup(int ticks) {
            return new Shot(name, setup, ticks, hud, tab, camera, target);
        }
    }

    public static final String CUE = "neroevents gallery cue ";
    public static final String STORM = "neroevents gallery storm ";

    /** The whole-gallery hero pose. */
    private static final Pose HERO_CAM = new Pose(0, 9, 4);
    private static final Pose HERO_TARGET = new Pose(0, 1.5, -13);
    private static final Pose STORM_CAM = new Pose(-10, 3, -58);
    private static final Pose STORM_TARGET = new Pose(-10, 6, -66);
    private static final Pose CONSOLE_CAM = new Pose(0, 2, -4);
    private static final Pose CONSOLE_TARGET = new Pose(0, 1.5, -6);

    /** {@code /nsevgallery capture}: the nine exhibit shots, then every station and every UI state. */
    public static final List<Shot> SHOWCASE = showcase();

    /** {@code /nsevgallery capture stress}: the hero shot, then the renderer-budget frame. */
    public static final List<Shot> STRESS = List.of(
            shot("hero", List.of(), 0, false, -1, HERO_CAM, HERO_TARGET),
            shot("stress_budget", List.of(), 240, false, -1, new Pose(0, 12, 2), new Pose(0, 6, -40)));

    /** Which shot shows each station (the test checks every station is covered). */
    public static final Map<String, String> STATION_SHOTS = Map.of(
            "supply_drop", "station_supply_drop",
            "meteor_shower", "station_meteor_fall",
            "debris_crash", "station_debris_fall",
            "wreck_yard", "wreck_yard",
            "faction_war", "station_war_capture",
            "creatures", "station_creatures",
            "boss", "station_boss",
            "storms", "storm_solar",
            "market_shock", "station_market");

    private GalleryShots() {
    }

    private static Shot shot(String name, List<String> setup, int warmup, boolean hud, int tab, Pose camera,
            Pose target) {
        return new Shot(name, setup, warmup, hud, tab, camera, target);
    }

    private static Shot still(String name, double cx, double cy, double cz, double tx, double ty, double tz) {
        return shot(name, List.of(), 0, false, -1, new Pose(cx, cy, cz), new Pose(tx, ty, tz));
    }

    private static Shot cued(String name, String setup, int warmup, double cx, double cy, double cz, double tx,
            double ty, double tz) {
        return shot(name, List.of(setup), warmup, false, -1, new Pose(cx, cy, cz), new Pose(tx, ty, tz));
    }

    private static List<Shot> showcase() {
        List<Shot> shots = new ArrayList<>();
        // The exhibit rows. The descending pod falls slowly from 14 up, so the shots that show it come first.
        shots.add(shot("hero", List.of(), 0, false, -1, HERO_CAM, HERO_TARGET));     // the whole gallery
        shots.add(still("pod_descending", 4, 6, -7, 4, 10, -14));                    // canopy, gores, falling pod
        shots.add(still("pods", -5, 4, -7.5, -5, 1.5, -14));                         // tiers 1-3 landed
        shots.add(still("war_beacon", 9, 3.5, -9.5, 9, 1.5, -14));                   // pylon and rings
        shots.add(still("meteors", -3, 5, -13.5, -3, 4.5, -20));                     // two meteors and trails
        shots.add(still("debris", 6, 5, -15.5, 6, 4.5, -20));                        // the tumbling hull fragment
        shots.add(still("crater_blocks", -9, 3.2, -1.5, -9, 1.2, -6));               // slag, crust, ore, pocket
        shots.add(still("consoles", -1, 2.4, -3, -1, 1.5, -6));                      // idle and active console
        shots.add(still("wreck_blocks", 8, 3.2, -1.5, 8, 1.2, -6));                  // plating .. crates
        // The event stations.
        shots.add(cued("station_supply_drop", CUE + "supply_drop", 60, -14, 5, -24, -14, 6, -32));
        shots.add(cued("station_meteor_fall", CUE + "meteor_shower", 10, -4, 7, -25, 0, 4, -32));
        shots.add(still("station_meteor_crater", 0, 8, -26, 0, 2, -32).withWarmup(40));
        shots.add(cued("station_debris_fall", CUE + "debris_crash", 10, 10, 6, -25, 14, 3, -32));
        shots.add(still("wreck_yard", 0, 7, -36, 0, 1.5, -45));
        shots.add(still("wreck_strut_field", -15, 4, -39, -15, 1, -45));
        shots.add(still("wreck_decay", 13.5, 4, -39, 13.5, 1.5, -45));
        shots.add(cued("station_war_capture", CUE + "faction_war", 10, -14, 4, -51, -14, 2, -58));
        shots.add(still("station_creatures", 0, 4, -50, 0, 1.5, -59));
        shots.add(still("station_boss", 14, 5, -50, 14, 2, -58));
        for (String storm : List.of("solar", "dust", "ion", "cryo")) {
            shots.add(shot("storm_" + storm, List.of(STORM + storm), 50, true, -1, STORM_CAM, STORM_TARGET));
        }
        shots.add(cued("station_market", STORM + "off", 210, 10, 3, -60, 10, 2, -67));
        // The demo HUD scenes.
        shots.add(shot("hud_info_vote", List.of(CUE + "hud_info"), 10, true, -1, HERO_CAM, HERO_TARGET));
        shots.add(shot("hud_reward_banner", List.of(CUE + "hud_reward"), 10, true, -1, HERO_CAM, HERO_TARGET));
        shots.add(shot("hud_hazard_banner", List.of(CUE + "hud_hazard"), 10, true, -1, HERO_CAM, HERO_TARGET));
        shots.add(shot("hud_combat_banner", List.of(CUE + "hud_combat"), 10, true, -1, HERO_CAM, HERO_TARGET));
        shots.add(shot("hud_combat_pill", List.of(), 110, true, -1, HERO_CAM, HERO_TARGET));
        shots.add(shot("hud_war_marker", List.of(CUE + "hud_war"), 110, true, -1, new Pose(-14, 3, -50),
                new Pose(-14, 2, -58)));
        // The demo console, every tab (the INFO scene first, so Soon has the vote row).
        shots.add(shot("console_live", List.of(CUE + "hud_info", CUE + "console"), 15, false, 0, CONSOLE_CAM,
                CONSOLE_TARGET));
        shots.add(shot("console_soon_vote", List.of(), 5, false, 1, CONSOLE_CAM, CONSOLE_TARGET));
        shots.add(shot("console_past", List.of(), 5, false, 2, CONSOLE_CAM, CONSOLE_TARGET));
        shots.add(shot("console_rewards", List.of(), 5, false, 3, CONSOLE_CAM, CONSOLE_TARGET));
        shots.add(shot("console_privacy", List.of(), 5, false, 4, CONSOLE_CAM, CONSOLE_TARGET));
        return List.copyOf(shots);
    }

    /** The name of the last shot that shows the console (the harness closes it after that one). */
    public static String lastConsoleShot(List<Shot> shots) {
        String last = "";
        for (Shot shot : shots) {
            if (shot.tab() >= 0) {
                last = shot.name();
            }
        }
        return last;
    }
}
