package za.co.neroland.neroevents.command;

/**
 * The gallery's war beacon script, pure: over {@link #PERIOD} ticks the centre beacon is swept from side A to
 * side B, held, swept back to A and held again, so the capture ring, the contested look, the rising capture
 * tick and the captured cue are all on show. Demo sides only; no faction, player or score is involved.
 */
public final class GalleryWarScript {

    public static final int PERIOD = 1600;
    /** Ticks a sweep takes (the beacon's capture time). */
    public static final int CAPTURE_TICKS = 600;
    /** Where {@code gallery cue faction_war} jumps to: B sweeping at 70 %, contested. */
    public static final int CUE_T = 420;

    public static final byte SIDE_A = 0;
    public static final byte SIDE_B = 1;
    public static final byte NONE = -1;

    /** One tick's beacon state. */
    public record Frame(byte owner, byte progressSide, int progress, boolean contested, boolean capturedNow,
            boolean tickSound) {
    }

    private GalleryWarScript() {
    }

    /** The frame at script time {@code t} (taken modulo {@link #PERIOD}). */
    public static Frame at(long t) {
        int s = (int) Math.floorMod(t, (long) PERIOD);
        if (s < 600) {
            return new Frame(SIDE_A, SIDE_B, s, true, false, s % 20 == 0);
        }
        if (s == 600) {
            return new Frame(SIDE_B, NONE, 0, false, true, false);
        }
        if (s < 800) {
            return new Frame(SIDE_B, NONE, 0, false, false, false);
        }
        if (s < 1400) {
            int progress = s - 800;
            return new Frame(SIDE_B, SIDE_A, progress, true, false, progress % 20 == 0);
        }
        if (s == 1400) {
            return new Frame(SIDE_A, NONE, 0, false, true, false);
        }
        return new Frame(SIDE_A, NONE, 0, false, false, false);
    }
}
