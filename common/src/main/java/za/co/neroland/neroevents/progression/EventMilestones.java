package za.co.neroland.neroevents.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * NeroEvents' progression signals: the <b>personal milestone records</b> and <b>world channels</b> a
 * quest pack can read. This is a seam — the event scheduler does not exist yet, so nothing in this
 * build calls it; when participation tracking and event resolution land they call one method here per
 * milestone and the shipped pack ({@code data/neroevents/neroquests/}) starts completing with no
 * further data changes.
 *
 * <p><b>Records</b> are player-scope Neroland Core gates ({@code data/neroevents/neroland_gates/*.json})
 * opened once for a player: they are history, not locks. Nothing in NeroEvents checks them for access,
 * they have no {@code requires}, and re-opening an open gate is a no-op in Core. Every {@code record...}
 * method takes the server and a UUID so the resolve step can credit participants who have since logged
 * off (Core 1.15.0).
 *
 * <p><b>Channels</b> are anonymous threshold crossings on Core's {@link ThresholdEvents} bus. Their
 * {@code scope} is the <i>type</i> of event or boss ({@code neroevents:meteor_shower}), never the
 * participant list, the top contributor, the event's position or its region. A crossing can never
 * credit one player, which is why personal milestones are gates.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the UUID goes only into {@link ProgressionGates#open}; it is never
 * logged or broadcast. Gate rows live in Core's progression store and are erased through Core's shared
 * erasure hook. Participation data the scheduler keeps to decide <i>who</i> to credit must stay minimal
 * and erasable; it is not this class's concern.
 */
public final class EventMilestones {

    /** The player took part in their first event (any type) and met its contribution floor. */
    public static final Identifier FIRST_PARTICIPATION = gate("first_participation");
    /** The player claimed a supply drop. */
    public static final Identifier SUPPLY_CLAIMED = gate("supply_claimed");
    /** The player helped repel a raid-type event. */
    public static final Identifier RAID_REPELLED = gate("raid_repelled");
    /** The player met the contribution floor on a world-boss event. */
    public static final Identifier BOSS_CONTRIBUTOR = gate("boss_contributor");

    /** World channel: an event resolved. Scope = event type id, value = world resolved count. */
    public static final Identifier EVENT_RESOLVED = channel("event_resolved");
    /** World channel: a world boss was defeated. Scope = boss event type id, falling (pressure resolved). */
    public static final Identifier BOSS_DEFEATED = channel("boss_defeated");

    /**
     * Rungs of the world resolved-event count, in ascending order. A resolve fires {@link #EVENT_RESOLVED}
     * with the highest rung at or below the new count as its threshold; the pack's "world log" quests read
     * {@code min_value} on the value, so every rung in between still counts.
     */
    static final long[] EVENT_RUNGS = {1L, 5L, 10L, 25L, 50L, 100L, 250L, 500L, 1000L};

    private EventMilestones() {
    }

    private static Identifier gate(String path) {
        return Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path);
    }

    private static Identifier channel(String path) {
        return Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path);
    }

    // ---- records (one per gate) -------------------------------------------------------------------

    /** Records {@link #FIRST_PARTICIPATION} for a participant who met the contribution floor. */
    public static boolean recordFirstParticipation(MinecraftServer server, UUID participant) {
        return record(server, participant, FIRST_PARTICIPATION);
    }

    /** Records {@link #SUPPLY_CLAIMED} for the player who claimed a supply drop. */
    public static boolean recordSupplyClaimed(MinecraftServer server, UUID participant) {
        return record(server, participant, SUPPLY_CLAIMED);
    }

    /** Records {@link #RAID_REPELLED} for a participant who helped repel a raid. */
    public static boolean recordRaidRepelled(MinecraftServer server, UUID participant) {
        return record(server, participant, RAID_REPELLED);
    }

    /** Records {@link #BOSS_CONTRIBUTOR} for a participant who met a world boss's contribution floor. */
    public static boolean recordBossContributor(MinecraftServer server, UUID participant) {
        return record(server, participant, BOSS_CONTRIBUTOR);
    }

    /**
     * Opens a record gate for a player, online or offline. Server thread only.
     *
     * @return {@code true} if the gate was newly opened
     */
    static boolean record(MinecraftServer server, UUID participant, Identifier gate) {
        if (server == null || participant == null) return false;
        return ProgressionGates.open(server, participant, gate);
    }

    // ---- channels (one per channel) ---------------------------------------------------------------

    /**
     * Publishes one {@link #EVENT_RESOLVED} crossing for a resolved event. Call once per resolve, after the
     * persisted world count has been incremented.
     *
     * @param eventTypeId       the event <i>definition</i> id (e.g. {@code neroevents:meteor_shower}) — the
     *                          scope; never a place, a participant or a position
     * @param worldResolvedCount the world's total of resolved events, this one included (≥ 1)
     * @return {@code true} if a crossing was fired
     */
    public static boolean fireEventResolved(Identifier eventTypeId, long worldResolvedCount) {
        if (eventTypeId == null || worldResolvedCount < 1L) return false;
        fire(new ThresholdEvents.ThresholdCrossing(EVENT_RESOLVED, eventTypeId.toString(),
                worldResolvedCount, rungFor(worldResolvedCount), true));
        return true;
    }

    /**
     * Publishes one {@link #BOSS_DEFEATED} crossing: value {@code 0} against threshold {@code 1}, falling,
     * meaning the boss's pressure on the world has been resolved (the same shape NeroCreatures uses).
     *
     * @param bossEventId the boss event's definition id — the scope; never who dealt the blow or where
     * @return {@code true} if a crossing was fired
     */
    public static boolean fireBossDefeated(Identifier bossEventId) {
        if (bossEventId == null) return false;
        fire(new ThresholdEvents.ThresholdCrossing(BOSS_DEFEATED, bossEventId.toString(), 0L, 1L, false));
        return true;
    }

    /** The highest rung at or below {@code count} (at least the first rung). */
    static long rungFor(long count) {
        long rung = EVENT_RUNGS[0];
        for (long candidate : EVENT_RUNGS) {
            if (candidate <= count) rung = candidate;
        }
        return rung;
    }

    private static void fire(ThresholdEvents.ThresholdCrossing crossing) {
        ThresholdEvents.fire(NeroEventsCommon.MOD_ID, crossing);
    }
}
