package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.command.GalleryLayout.Box;
import za.co.neroland.neroevents.command.GalleryLayout.Offset;
import za.co.neroland.neroevents.payload.debris.WreckLayout;

/**
 * The gallery's event stations, as pure numbers: one labelled bay per shipped payload type north of the
 * exhibit rows, each showing its event in motion (see {@code GalleryShowcase} for what each one places and
 * how its loop runs). Offsets are from the invoking operator's block position, like {@link GalleryLayout}.
 * Nothing here touches a level, so the plan (bays inside the footprint, no overlaps, every payload type
 * covered) is checked in a plain JVM.
 */
public final class GalleryStations {

    /** One station: the payload types it demonstrates, the sibling it needs for the real event, where it is. */
    public record Station(String id, List<Identifier> payloadTypes, @Nullable String needsMod, Offset centre,
            Box bay, String label) {

        public Station {
            payloadTypes = List.copyOf(payloadTypes);
        }
    }

    /** One wreck placed with its north-west corner at {@code origin}, then decayed to {@code stage} (0 = none). */
    public record YardWreck(String variant, Offset centre, Offset origin, int stage, String label) {
    }

    /** What a decayed wreck cell becomes (mirrors the real decay for empty crates). */
    public enum DecayKind { SCORCHED_PANEL, RUBBLE, AIR }

    /** One cell a decay stage rewrites, in layout coordinates. */
    public record DecayCell(int x, int y, int z, DecayKind kind) {
    }

    private static final int BAY_MIN_DY = 1;
    private static final int BAY_MAX_DY = 12;

    public static final Station SUPPLY_DROP = station("supply_drop", List.of("supply_drop"), null,
            new Offset(-14, 1, -32), -18, -36, -10, -28, "Supply Drop — a pod lands here every 20 s");
    public static final Station METEOR_SHOWER = station("meteor_shower", List.of("meteor_shower"), null,
            new Offset(0, 1, -32), -4, -36, 4, -28, "Meteor Shower — a strike every 5 s into a real crater");
    public static final Station DEBRIS_CRASH = station("debris_crash", List.of("debris_crash"), null,
            new Offset(14, 1, -32), 10, -36, 18, -28, "Debris Crash — hull fragment onto the pod-shell wreck");
    public static final Station WRECK_YARD = station("wreck_yard", List.of("debris_crash"), null,
            new Offset(0, 1, -45), -19, -49, 19, -41, "Wreck Yard — the four layouts and the two decay stages");
    public static final Station FACTION_WAR = station("faction_war", List.of("faction_war"), "nerofactions",
            new Offset(-14, 1, -58), -18, -62, -10, -54, "Faction War — capture beacon (demo sides)");
    public static final Station CREATURES = station("creatures", List.of("pirate_raid", "alien_invasion"),
            "nerocreatures", new Offset(0, 1, -58), -6, -62, 6, -54,
            "Pirate Raid / Alien Invasion — waves of raiders and aliens");
    public static final Station BOSS = station("boss", List.of("boss"), "nerocreatures",
            new Offset(14, 1, -58), 10, -62, 18, -54, "World Boss — arena");
    public static final Station STORMS = station("storms", List.of("solar_storm", "planetary_storm"), "nerospace",
            new Offset(-10, 1, -66), -18, -69, -2, -63,
            "Storms — preview on your screen; the real event needs Nerospace");
    public static final Station MARKET_SHOCK = station("market_shock", List.of("market_shock"), "neroeconomy",
            new Offset(10, 1, -66), 6, -69, 14, -63, "Market Shock — prices move at NeroEconomy shops");

    /** Every station, south-west to north-east. */
    public static final List<Station> ALL = List.of(SUPPLY_DROP, METEOR_SHOWER, DEBRIS_CRASH, WRECK_YARD,
            FACTION_WAR, CREATURES, BOSS, STORMS, MARKET_SHOCK);

    /** {@code gallery cue <station>} ids: the four loop stations, the five HUD scenes and the console. */
    public static final List<String> CUE_IDS = List.of("supply_drop", "meteor_shower", "debris_crash", "faction_war",
            "hud_info", "hud_reward", "hud_hazard", "hud_combat", "hud_war", "console");

    /** {@code gallery storm <storm>} values. */
    public static final List<String> STORM_IDS = List.of("solar", "dust", "ion", "cryo", "off");

    // --- the debris station's wreck and the wreck yard ----------------------------------------------------

    /** The debris station's pod shell: its crate (layout cell 2,0,2) is what the debris lands on. */
    public static final YardWreck DEBRIS_WRECK = new YardWreck("wreck_pod_shell", new Offset(14, 1, -32),
            new Offset(12, 1, -34), 0, "");
    /** Where the falling debris lands: the top face of that crate. */
    public static final Offset DEBRIS_IMPACT = new Offset(14, 2, -32);

    /** The yard: all four layouts, then the pod shell at both decay stages. */
    public static final List<YardWreck> YARD = List.of(
            new YardWreck("wreck_strut_field", new Offset(-15, 1, -45), new Offset(-18, 1, -48), 0,
                    "Wreck — strut field"),
            new YardWreck("wreck_cargo_spill", new Offset(-6, 1, -45), new Offset(-8, 1, -47), 0,
                    "Wreck — cargo spill"),
            new YardWreck("wreck_hull_rib", new Offset(2, 1, -45), new Offset(0, 1, -47), 0, "Wreck — hull rib"),
            new YardWreck("wreck_pod_shell", new Offset(10, 1, -45), new Offset(8, 1, -47), 1,
                    "Wreck — pod shell, decay stage 1 (after wreckLingerDays)"),
            new YardWreck("wreck_pod_shell", new Offset(17, 1, -45), new Offset(15, 1, -47), 2,
                    "Wreck — pod shell, decay stage 2 (rubble)"));

    // --- the war, storm and market stations -------------------------------------------------------------

    /** West, centre (fought over) and east beacon. */
    public static final List<Offset> WAR_BEACONS = List.of(new Offset(-17, 1, -58), new Offset(-14, 1, -58),
            new Offset(-11, 1, -58));

    /** The storm posts' columns, solar / dust / ion / cryo. */
    public static final int[] STORM_POST_DX = {-16, -12, -8, -4};
    public static final int STORM_POST_DZ = -66;
    public static final List<String> STORM_POST_LABELS = List.of("Solar Storm — any space dimension",
            "Dust Storm — Greenxertz", "Ion Squall — Cindara", "Cryo Blizzard — Glacira");

    /** The market board's wall, frames and labels. */
    public static final int MARKET_WALL_MIN_DX = 7;
    public static final int MARKET_WALL_MAX_DX = 13;
    public static final int MARKET_WALL_DZ = -68;
    public static final int MARKET_FRAME_DZ = -67;
    public static final List<String> MARKET_ITEMS = List.of("minecraft:iron_ingot", "minecraft:copper_ingot",
            "nerolandcore:nero_alloy_ingot", "minecraft:redstone", "nerolandcore:plasma_glass", "minecraft:diamond");
    public static final List<String> MARKET_LABELS = List.of("Iron ×1.8 — shortage 45 min",
            "Copper ×0.5 — glut 45 min", "Nero Alloy ×2.0 — 30 min", "Redstone ×1.6 — 60 min",
            "Plasma Glass ×0.6 — glut 40 min", "Diamond ×1.4 — demand 90 min");
    public static final Offset MARKET_CONSOLE = new Offset(10, 1, -65);

    private GalleryStations() {
    }

    private static Station station(String id, List<String> types, @Nullable String needsMod, Offset centre,
            int minDx, int minDz, int maxDx, int maxDz, String label) {
        List<Identifier> ids = new ArrayList<>();
        for (String type : types) {
            ids.add(Identifier.fromNamespaceAndPath("neroevents", type));
        }
        return new Station(id, ids, needsMod, centre, new Box(minDx, BAY_MIN_DY, minDz, maxDx, BAY_MAX_DY, maxDz),
                label);
    }

    /** The station that demonstrates {@code payloadType} (the first one, for types shown twice). */
    public static Optional<Station> forPayload(Identifier payloadType) {
        for (Station station : ALL) {
            if (station.payloadTypes().contains(payloadType)) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /** The station whose bay covers the floor cell {@code (dx, dz)}. */
    public static Optional<Station> stationAt(int dx, int dz) {
        for (Station station : ALL) {
            if (station.bay().containsColumn(dx, dz)) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /** The station with {@code id}. */
    public static Optional<Station> byId(String id) {
        for (Station station : ALL) {
            if (station.id().equals(id)) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /** A sibling mod's display name for the "needs ..." label line. */
    public static String modName(String modId) {
        return switch (modId) {
            case "nerofactions" -> "NeroFactions";
            case "nerocreatures" -> "NeroCreatures";
            case "nerospace" -> "Nerospace";
            case "neroeconomy" -> "NeroEconomy";
            default -> modId;
        };
    }

    /**
     * The cells a decay stage rewrites for an <b>empty</b> wreck: stage 1 scorches the plating and drops the
     * struts and crates; stage 2 turns plating and panels to rubble and drops the struts and crates. Cells the
     * stage leaves alone are omitted.
     */
    public static List<DecayCell> decay(WreckLayout layout, int stage) {
        List<DecayCell> out = new ArrayList<>();
        if (stage <= 0) {
            return out;
        }
        for (WreckLayout.Placement p : layout.placements()) {
            DecayKind kind = switch (p.kind()) {
                case HULL_PLATING -> stage == 1 ? DecayKind.SCORCHED_PANEL : DecayKind.RUBBLE;
                case SCORCHED_PANEL -> stage == 1 ? null : DecayKind.RUBBLE;
                case BENT_STRUT, SALVAGE_CRATE -> DecayKind.AIR;
            };
            if (kind != null) {
                out.add(new DecayCell(p.x(), p.y(), p.z(), kind));
            }
        }
        return out;
    }
}
