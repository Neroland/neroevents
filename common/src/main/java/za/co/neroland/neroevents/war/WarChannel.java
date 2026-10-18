package za.co.neroland.neroevents.war;

import java.util.function.Consumer;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * {@code neroevents:war_resolved}, fired through Neroland Core's {@link ThresholdEvents} once per participating
 * faction when a war resolves: {@code scope} = the faction id, {@code value} = its final score,
 * {@code threshold} = 1, {@code rising} = {@code true} for the victor and {@code false} for the loser (and for
 * both sides on a draw). Nothing per player — scope is a faction, never a person.
 */
public final class WarChannel {

    /** The channel id. */
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "war_resolved");

    private static final Consumer<ThresholdEvents.ThresholdCrossing> CORE =
            crossing -> ThresholdEvents.fire(NeroEventsCommon.MOD_ID, crossing);

    private static volatile Consumer<ThresholdEvents.ThresholdCrossing> sink = CORE;

    private WarChannel() {
    }

    /** Fires one faction's resolution. Never throws. */
    public static void resolved(Identifier faction, long score, boolean victor) {
        if (faction == null) {
            return;
        }
        try {
            sink.accept(new ThresholdEvents.ThresholdCrossing(CHANNEL, faction.toString(), Math.max(0L, score), 1L,
                    victor));
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] war_resolved could not be fired ({}).",
                    failure.getClass().getName());
        }
    }

    /** Test seam: records crossings instead of firing them ({@code null} restores Core). */
    static void useForTests(Consumer<ThresholdEvents.ThresholdCrossing> recorder) {
        sink = recorder == null ? CORE : recorder;
    }
}
