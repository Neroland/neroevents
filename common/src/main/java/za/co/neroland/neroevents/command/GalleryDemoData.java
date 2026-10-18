package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.console.ConsolePayloads;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.console.ConsoleRows;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.Severity;

/**
 * The gallery's demo HUD and demo console, pure: five scenes (INFO, REWARD, HAZARD, COMBAT, WAR) of
 * {@link #SCENE_TICKS} each, built from fixed small keys, shipped lang keys, constant counts and gallery
 * offsets. Nothing here is a real event, and no field is ever a UUID, a name or a player's position.
 */
public final class GalleryDemoData {

    public static final int SCENES = 5;
    public static final int SCENE_TICKS = 400;

    public static final int SCENE_INFO = 0;
    public static final int SCENE_REWARD = 1;
    public static final int SCENE_HAZARD = 2;
    public static final int SCENE_COMBAT = 3;
    public static final int SCENE_WAR = 4;

    /** Fixed demo keys (never derived from a UUID). */
    public static final int K_MARKET = 1;
    public static final int K_SUPPLY = 2;
    public static final int K_METEOR = 3;
    public static final int K_DUST = 4;
    public static final int K_RAID = 5;
    public static final int K_BOSS = 6;
    public static final int K_WAR = 7;

    public static final int VOTE_YES = 12;
    public static final int VOTE_NO = 3;
    public static final int VOTE_NEEDED = 5;
    public static final String WAR_OBJECTIVE = "Gallery Reds 34 · Gallery Blues 21";
    public static final String DEMO_MESSAGE = "console.neroevents.gallery.demo";

    private static final int[] PARTICIPANTS = {0, 3, 6, 4, 8};

    /** One demo event: its key, definition, type and lang keys. */
    private record Demo(int key, String definition, String type, String titleKey, String whatToDoKey) {

        Identifier definitionId() {
            return Identifier.fromNamespaceAndPath("neroevents", definition);
        }

        Identifier typeId() {
            return Identifier.fromNamespaceAndPath("neroevents", type);
        }
    }

    private static final Demo MARKET = new Demo(K_MARKET, "economy/market_shock", "market_shock",
            "event.neroevents.market_shock.title", "event.neroevents.market_shock.what_to_do");
    private static final Demo SUPPLY = new Demo(K_SUPPLY, "core/supply_drop", "supply_drop",
            "event.neroevents.core.supply_drop.title", "event.neroevents.core.supply_drop.what_to_do");
    private static final Demo METEOR = new Demo(K_METEOR, "core/meteor_shower", "meteor_shower",
            "event.neroevents.core.meteor_shower.title", "event.neroevents.core.meteor_shower.what_to_do");
    private static final Demo DUST = new Demo(K_DUST, "nerospace/dust_storm", "planetary_storm",
            "event.neroevents.dust_storm.title", "event.neroevents.dust_storm.what_to_do");
    private static final Demo RAID = new Demo(K_RAID, "creatures/pirate_raid", "pirate_raid",
            "event.neroevents.pirate_raid.title", "event.neroevents.pirate_raid.what_to_do");
    private static final Demo BOSS = new Demo(K_BOSS, "creatures/cinder_tyrant", "boss",
            "event.neroevents.cinder_tyrant.title", "event.neroevents.cinder_tyrant.what_to_do");
    private static final Demo WAR = new Demo(K_WAR, "factions/faction_war", "faction_war",
            "event.neroevents.faction_war.title", "event.neroevents.faction_war.what_to_do");

    /** One row of a scene, before it becomes a HUD entry or a console row. */
    private record Line(Demo demo, Severity severity, EventPhase phase, int base, boolean voting, int done,
            int total, int remaining, String objective, boolean marker, BlockPos markerPos, boolean located,
            BlockPos location) {
    }

    private GalleryDemoData() {
    }

    /** The scene after {@code scene}. */
    public static int next(int scene) {
        return Math.floorMod(scene + 1, SCENES);
    }

    /** The most severe {@code Severity} ordinal a scene shows (what the gallery's consoles light up with). */
    public static byte topSeverity(int scene) {
        Severity top = switch (Math.floorMod(scene, SCENES)) {
            case SCENE_INFO -> Severity.INFO;
            case SCENE_REWARD -> Severity.REWARD;
            case SCENE_HAZARD -> Severity.HAZARD;
            case SCENE_COMBAT -> Severity.COMBAT;
            default -> Severity.WAR;
        };
        return (byte) top.ordinal();
    }

    private static List<Line> lines(int scene, BlockPos origin) {
        BlockPos none = BlockPos.ZERO;
        List<Line> out = new ArrayList<>();
        switch (Math.floorMod(scene, SCENES)) {
            case SCENE_INFO -> {
                out.add(new Line(MARKET, Severity.INFO, EventPhase.ACTIVE, 54000, false, 0, 0, -1, "", false, none,
                        false, none));
                // A supply drop is announced as INFO and turns REWARD when it starts.
                out.add(new Line(SUPPLY, ConsoleRows.severityFor(Severity.REWARD, EventPhase.ANNOUNCED),
                        EventPhase.ANNOUNCED, 272, true, 0, 0, -1, "", false, none, false, none));
            }
            case SCENE_REWARD -> out.add(new Line(SUPPLY, Severity.REWARD, EventPhase.ACTIVE, 1200, false, 2, 4, -1, "",
                    false, none, false, none));
            case SCENE_HAZARD -> {
                out.add(new Line(METEOR, Severity.HAZARD, EventPhase.ACTIVE, 3600, false, 9, 24, -1, "", false, none,
                        false, none));
                out.add(new Line(DUST, Severity.HAZARD, EventPhase.ACTIVE, 9000, false, 0, 0, -1, "", false, none,
                        false, none));
            }
            case SCENE_COMBAT -> {
                out.add(new Line(RAID, Severity.COMBAT, EventPhase.ACTIVE, 2400, false, 1, 3, 5, "", false, none,
                        false, none));
                out.add(new Line(BOSS, Severity.COMBAT, EventPhase.ANNOUNCED, 600, false, 0, 0, -1, "", false, none,
                        true, GalleryStations.BOSS.centre().at(origin)));
            }
            default -> out.add(new Line(WAR, Severity.WAR, EventPhase.ACTIVE, 4800, false, 0, 0, -1, WAR_OBJECTIVE,
                    true, GalleryStations.FACTION_WAR.centre().at(origin), false, none));
        }
        return out;
    }

    private static int ticksLeft(Line line, long age) {
        return (int) Math.max(0L, line.base() - Math.max(0L, age));
    }

    private static int clampAge(long age) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, age));
    }

    /**
     * The demo HUD for scene {@code scene}, {@code age} ticks after it began, for a viewer in {@code dimension}.
     * {@code origin} is where the gallery was built (the war marker sits on its war station).
     */
    public static HudState hud(int scene, long age, Identifier dimension, BlockPos origin) {
        List<HudEntry> entries = new ArrayList<>();
        for (Line line : lines(scene, origin)) {
            if (entries.size() >= ConsolePayloads.MAX_HUD) {
                break;
            }
            Demo d = line.demo();
            entries.add(new HudEntry(d.key(), (byte) line.severity().ordinal(), d.titleKey(),
                    ConsoleRows.definitionTodoKey(d.definitionId()), ConsoleRows.todoKey(d.typeId()), dimension,
                    (byte) line.phase().ordinal(), ticksLeft(line, age), clampAge(age), line.voting(),
                    line.voting() ? VOTE_YES : 0, line.voting() ? VOTE_NO : 0, line.done(), line.total(),
                    line.remaining(), line.objective(), line.marker(),
                    line.marker() ? line.markerPos() : BlockPos.ZERO));
        }
        return new HudState(entries);
    }

    /** The demo console for scene {@code scene}, with the default demo footer. */
    public static ConsoleSnapshot console(int scene, long age, Identifier dimension, BlockPos origin) {
        return console(scene, age, dimension, origin, true, DEMO_MESSAGE, "");
    }

    /** The demo console with a footer answer ({@code key} "" falls back to the demo footer). */
    public static ConsoleSnapshot console(int scene, long age, Identifier dimension, BlockPos origin, boolean ok,
            String key, String arg) {
        int participants = PARTICIPANTS[Math.floorMod(scene, SCENES)];
        List<EventRow> live = new ArrayList<>();
        List<EventRow> soon = new ArrayList<>();
        for (Line line : lines(scene, origin)) {
            Demo d = line.demo();
            EventRow row = new EventRow(d.key(), (byte) line.severity().ordinal(), d.titleKey(),
                    ConsoleRows.definitionTodoKey(d.definitionId()), ConsoleRows.todoKey(d.typeId()), d.whatToDoKey(),
                    dimension, (byte) line.phase().ordinal(), ticksLeft(line, age), participants, line.located(),
                    line.located() ? line.location() : BlockPos.ZERO, line.voting(), line.voting() ? VOTE_YES : 0,
                    line.voting() ? VOTE_NO : 0, VOTE_NEEDED, ConsolePayloads.NO_VOTE, (byte) 0);
            (line.phase() == EventPhase.ACTIVE ? live : soon).add(row);
        }
        byte done = (byte) EventPhase.DONE.ordinal();
        List<HistoryRow> past = List.of(
                new HistoryRow("meteor_shower", METEOR.titleKey(), dimension, done,
                        (byte) EventOutcome.SUCCESS.ordinal(), 7200L, 6),
                new HistoryRow("pirate_raid", RAID.titleKey(), dimension, done,
                        (byte) EventOutcome.FAILURE.ordinal(), 10800L, 4),
                new HistoryRow("supply_drop", SUPPLY.titleKey(), dimension, done,
                        (byte) EventOutcome.SUCCESS.ordinal(), 14400L, 3),
                new HistoryRow("faction_war", WAR.titleKey(), dimension, done,
                        (byte) EventOutcome.SUCCESS.ordinal(), 21600L, 8),
                new HistoryRow("boss", BOSS.titleKey(), dimension, done,
                        (byte) EventOutcome.ESCAPED.ordinal(), 43200L, 5),
                new HistoryRow("market_shock", MARKET.titleKey(), dimension, done,
                        (byte) EventOutcome.ENDED.ordinal(), 86400L, 0));
        List<LedgerRow> ledger = List.of(
                new LedgerRow(SUPPLY.titleKey(), 3, 0L, 60L, 3600L),
                new LedgerRow(METEOR.titleKey(), 0, 120L, 40L, 7200L),
                new LedgerRow(RAID.titleKey(), 0, 350L, 250L, 86000L));
        String footer = key == null || key.isEmpty() ? DEMO_MESSAGE : key;
        boolean footerOk = key == null || key.isEmpty() || ok;
        return new ConsoleSnapshot(live, soon, past, true, (byte) ConsentMode.VOTE.ordinal(), false, 3, 120L, true,
                false, true, false, 7, 14, ledger, footerOk, footer, arg == null ? "" : arg);
    }
}
