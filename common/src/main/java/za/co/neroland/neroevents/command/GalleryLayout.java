package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The creative gallery's floor plan, as pure numbers: every exhibit is an {@link Offset} from the invoking
 * operator's block position (north is {@code -z}, the floor layer is {@code dy = 0}), so the plan can be checked
 * in a plain JVM and the builder only places what it is told. Nothing here touches a level.
 *
 * <pre>
 *   dz
 *   -20            meteor      meteor      debris            (hanging, tumbling in place, dy 4)
 *   -14   pod T1   pod T2   pod T3   pod descending   war beacon
 *    -6   slag crust ore pocket  console console  plating scorched strut crate crate(open)
 *    -2                        [marker]
 *     0                    operator (looking north)
 *         dx -13                    0                    +13
 * </pre>
 */
public final class GalleryLayout {

    /** Tag on every entity the gallery spawns (labels, pods, meteors, debris), so a clear removes only ours. */
    public static final String ENTITY_TAG = "neroevents_gallery";

    public static final int MIN_DX = -13;
    public static final int MAX_DX = 13;
    public static final int MIN_DZ = -24;
    public static final int MAX_DZ = -2;
    /** Height the descending pod starts at. */
    public static final int DESCENDING_POD_DY = 14;
    /** Layers the clear sweeps, floor included (the descending pod's start plus headroom). */
    public static final int CLEAR_DY = DESCENDING_POD_DY + 4;

    /** An integer offset from the operator's position. */
    public record Offset(int dx, int dy, int dz) {
    }

    /** One exhibit: what goes where. */
    public record Exhibit(String id, Offset at, String label) {
    }

    /** The marker block in the floor that says "a gallery stands here". */
    public static final Offset MARKER = new Offset(0, 0, -2);

    public static final int BLOCK_ROW_DZ = -6;
    public static final int POD_ROW_DZ = -14;
    public static final int HANGING_ROW_DZ = -20;
    public static final int HANGING_DY = 4;

    /** The blocks on the front row (ids under {@code neroevents:}; {@code *_open}/{@code *_active} are states). */
    public static final List<Exhibit> BLOCKS = List.of(
            new Exhibit("meteor_slag", new Offset(-12, 1, BLOCK_ROW_DZ), "Meteor Slag"),
            new Exhibit("cooled_crust", new Offset(-10, 1, BLOCK_ROW_DZ), "Cooled Crust"),
            new Exhibit("embedded_meteor_ore", new Offset(-8, 1, BLOCK_ROW_DZ), "Embedded Meteor Ore"),
            new Exhibit("crater_pocket", new Offset(-6, 1, BLOCK_ROW_DZ), "Crater Pocket"),
            new Exhibit("event_console", new Offset(-2, 1, BLOCK_ROW_DZ), "Event Console"),
            new Exhibit("event_console_active", new Offset(0, 1, BLOCK_ROW_DZ), "Event Console (event live)"),
            new Exhibit("hull_plating", new Offset(4, 1, BLOCK_ROW_DZ), "Hull Plating"),
            new Exhibit("scorched_panel", new Offset(6, 1, BLOCK_ROW_DZ), "Scorched Panel"),
            new Exhibit("bent_strut", new Offset(8, 1, BLOCK_ROW_DZ), "Bent Strut"),
            new Exhibit("salvage_crate", new Offset(10, 1, BLOCK_ROW_DZ), "Salvage Crate"),
            new Exhibit("salvage_crate_open", new Offset(12, 1, BLOCK_ROW_DZ), "Salvage Crate (opened)"));

    /** The pods and the war beacon on the middle row. */
    public static final List<Exhibit> POD_ROW = List.of(
            new Exhibit("supply_pod_t1", new Offset(-9, 1, POD_ROW_DZ), "Supply Pod — tier 1"),
            new Exhibit("supply_pod_t2", new Offset(-5, 1, POD_ROW_DZ), "Supply Pod — tier 2"),
            new Exhibit("supply_pod_t3", new Offset(-1, 1, POD_ROW_DZ), "Supply Pod — tier 3"),
            new Exhibit("supply_pod_descending", new Offset(4, DESCENDING_POD_DY, POD_ROW_DZ),
                    "Supply Pod — descending"),
            new Exhibit("war_beacon", new Offset(9, 1, POD_ROW_DZ), "War Beacon"));

    /** The meteors and debris hanging over the back row. */
    public static final List<Exhibit> HANGING = List.of(
            new Exhibit("meteor", new Offset(-6, HANGING_DY, HANGING_ROW_DZ), "Meteor"),
            new Exhibit("meteor", new Offset(0, HANGING_DY, HANGING_ROW_DZ), "Meteor"),
            new Exhibit("debris", new Offset(6, HANGING_DY, HANGING_ROW_DZ), "Falling Debris"));

    private GalleryLayout() {
    }

    /** Whether a floor cell is inside the gallery. */
    public static boolean onFloor(int dx, int dz) {
        return dx >= MIN_DX && dx <= MAX_DX && dz >= MIN_DZ && dz <= MAX_DZ;
    }

    /** Lit floor cells: a grid every four blocks, never under an exhibit. */
    public static boolean lamp(int dx, int dz) {
        if (!onFloor(dx, dz) || Math.floorMod(dx, 4) != 2 || Math.floorMod(dz, 4) != 0) {
            return false;
        }
        for (Exhibit e : all()) {
            if (e.at().dx() == dx && e.at().dz() == dz) {
                return false;
            }
        }
        return !(dx == MARKER.dx() && dz == MARKER.dz());
    }

    /** Every exhibit, front to back. */
    public static List<Exhibit> all() {
        List<Exhibit> out = new ArrayList<>(BLOCKS);
        out.addAll(POD_ROW);
        out.addAll(HANGING);
        return out;
    }
}
