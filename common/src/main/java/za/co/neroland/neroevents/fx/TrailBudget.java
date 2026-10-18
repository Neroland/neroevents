package za.co.neroland.neroevents.fx;

/**
 * The particle rates and caps of the payload effects, as pure numbers so they can be checked in a plain JVM:
 * meteor trails (2 smoke + 3 embers per tick, off beyond 96 blocks; with more than 20 meteors in view every
 * second meteor drops to half rate), debris smoke (1 per tick), the impact (one flash, a 24-ember cone and 12
 * ground-dust specks; a soft landing has no flash and half the embers), the landed pod (12 dust on landing, a
 * beacon ring every 20 ticks, one reward spark every 10 ticks while open, nothing beyond 48 blocks).
 */
public final class TrailBudget {

    /** Meteors in view above which every second meteor trails at half rate. */
    public static final int IN_VIEW_CAP = 20;
    /** Trails and the heat pass stop beyond this distance (blocks). */
    public static final double TRAIL_DISTANCE = 96.0D;
    /** Pod particles stop beyond this distance (blocks). */
    public static final double POD_PARTICLE_DISTANCE = 48.0D;

    public static final int SMOKE_PER_TICK = 2;
    /** Embers per tick at full rate (half rate alternates 2 and 1). */
    public static final int EMBERS_PER_TICK = 3;
    public static final int DEBRIS_SMOKE_PER_TICK = 1;
    public static final int IMPACT_EMBERS = 24;
    public static final int IMPACT_DUST = 12;
    public static final int LANDING_DUST = 12;
    public static final int DEBRIS_DUST = 8;
    public static final int BEACON_RING_TICKS = 20;
    public static final int SPARK_TICKS = 10;
    /** The ring starts once the legs have folded out. */
    public static final int BEACON_RING_DELAY = 8;

    private TrailBudget() {
    }

    /** Whether this meteor trails at half rate, given how many meteors were drawn in the last frame. */
    public static boolean halfRate(int meteorsInView, int entityId) {
        return meteorsInView > IN_VIEW_CAP && (entityId & 1) == 1;
    }

    /** Smoke puffs for one meteor this tick. */
    public static int smoke(boolean halfRate, double distanceSq) {
        if (distanceSq > TRAIL_DISTANCE * TRAIL_DISTANCE) {
            return 0;
        }
        return halfRate ? SMOKE_PER_TICK / 2 : SMOKE_PER_TICK;
    }

    /** Embers for one meteor this tick (half rate averages 1.5 by alternating ticks). */
    public static int embers(boolean halfRate, double distanceSq, long tick) {
        if (distanceSq > TRAIL_DISTANCE * TRAIL_DISTANCE) {
            return 0;
        }
        if (!halfRate) {
            return EMBERS_PER_TICK;
        }
        return (tick & 1L) == 0L ? 2 : 1;
    }

    /** Smoke for one piece of debris this tick. */
    public static int debrisSmoke(double distanceSq) {
        return distanceSq > TRAIL_DISTANCE * TRAIL_DISTANCE ? 0 : DEBRIS_SMOKE_PER_TICK;
    }

    /** Embers in the impact cone. */
    public static int impactEmbers(boolean soft) {
        return soft ? IMPACT_EMBERS / 2 : IMPACT_EMBERS;
    }

    /** Whether the impact flash is drawn: never for a soft landing, and only when the player's switch is on. */
    public static boolean impactFlash(boolean soft, boolean flashSwitchOn) {
        return !soft && flashSwitchOn;
    }

    /** Whether a pod within range emits its beacon ring on this tick after landing. */
    public static boolean beaconRingDue(int ticksSinceLanded) {
        return ticksSinceLanded >= BEACON_RING_DELAY && (ticksSinceLanded - BEACON_RING_DELAY) % BEACON_RING_TICKS == 0;
    }

    /** Whether an open pod emits a reward spark on this tick. */
    public static boolean sparkDue(boolean open, int tickCount) {
        return open && tickCount % SPARK_TICKS == 0;
    }

    /** Whether pod particles are drawn at this distance. */
    public static boolean podInRange(double distanceSq) {
        return distanceSq <= POD_PARTICLE_DISTANCE * POD_PARTICLE_DISTANCE;
    }
}
