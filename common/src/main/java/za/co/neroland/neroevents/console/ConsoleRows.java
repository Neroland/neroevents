package za.co.neroland.neroevents.console;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.Severity;

/**
 * Turns the engine's public {@link EventView} into the console rows and HUD entries the client draws, and
 * nothing more: the instance UUID becomes an opaque {@link #keyOf key}, a location survives only when the
 * definition makes it public and the event is not a raid or invasion target, and the HUD marker only when the
 * viewer is inside the event's bounds. Pure, so the data-minimisation rules are unit-tested.
 */
public final class ConsoleRows {

    /** Ticks a newly active event shows the banner before collapsing to the pill. */
    public static final int BANNER_TICKS = 100;

    /** Live first, then the most dangerous, then the soonest to change. */
    public static final Comparator<EventView> URGENCY = Comparator
            .comparing((EventView v) -> v.phase() == EventPhase.ACTIVE ? 0 : 1)
            .thenComparing(v -> -rank(severityFor(v.severity(), v.phase())))
            .thenComparingLong(ConsoleRows::ticksLeft);

    private ConsoleRows() {
    }

    /** An opaque, stable per-instance handle; never the UUID itself and never shown on screen. */
    public static int keyOf(UUID id) {
        long msb = id.getMostSignificantBits();
        long lsb = id.getLeastSignificantBits();
        long mixed = (msb * 0x9E3779B97F4A7C15L) ^ Long.rotateLeft(lsb, 31) ^ 0x5DEECE66DL;
        return (int) (mixed ^ (mixed >>> 32));
    }

    /** The one automatic severity change: a REWARD event is announced as INFO and turns REWARD when it starts. */
    public static Severity severityFor(Severity defined, EventPhase phase) {
        if (defined == null) {
            return Severity.INFO;
        }
        return defined == Severity.REWARD && phase != null && phase.upcoming() ? Severity.INFO : defined;
    }

    /** Danger rank used for "most urgent" (combat and war highest). */
    public static int rank(Severity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case INFO -> 1;
            case REWARD -> 2;
            case HAZARD -> 3;
            case COMBAT, WAR -> 4;
        };
    }

    /** Raid and invasion targets are somebody's base: their location is never sent, public flag or not. */
    public static boolean isTargetCategory(EventCategory category) {
        return category == EventCategory.RAID || category == EventCategory.INVASION;
    }

    /** Whether the view's anchor may leave the server at all. */
    public static boolean locationAllowed(EventView view) {
        return view.locationPublic() && view.publicAnchor() != null && !isTargetCategory(view.category());
    }

    /** Ticks until an upcoming event starts or a running one ends (never negative). */
    public static long ticksLeft(EventView view) {
        long target = view.phase().upcoming() ? view.startsAt() : view.endsAt();
        return Math.max(0L, target - view.now());
    }

    /** The type's shipped what-to-do line ({@code event.<ns>.<type>.todo}, {@code %s} = time left). */
    public static String todoKey(Identifier type) {
        return type == null ? "" : "event." + type.getNamespace() + "." + type.getPath() + ".todo";
    }

    /**
     * The definition's own what-to-do line ({@code event.<ns>.<definition path, '/' as '.'>.todo}); the client
     * uses it when a language file has it, so a definition can say something more specific than its type.
     */
    public static String definitionTodoKey(Identifier definition) {
        return definition == null ? ""
                : "event." + definition.getNamespace() + "." + definition.getPath().replace('/', '.') + ".todo";
    }

    /** Votes needed for quorum: the larger of the floor and the online fraction. */
    public static int quorum(int quorumPlayers, double quorumFraction, int online) {
        return Math.max(quorumPlayers, (int) Math.ceil(quorumFraction * Math.max(0, online)));
    }

    /** Whether {@code player} stands inside the horizontal radius around {@code anchor}. */
    public static boolean withinBounds(BlockPos anchor, BlockPos player, int radius) {
        if (anchor == null || player == null || radius <= 0) {
            return false;
        }
        long dx = (long) anchor.getX() - player.getX();
        long dz = (long) anchor.getZ() - player.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static int clampInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    /**
     * The console row for one view.
     *
     * @param needed    votes needed for quorum
     * @param myVote    the viewer's own choice ({@link ConsolePayloads#NO_VOTE}, 0, 1)
     * @param voteBlock why the viewer cannot vote, empty when they can
     */
    public static EventRow row(EventView view, int needed, byte myVote, Optional<ConsoleIntents.Deny> voteBlock) {
        boolean located = locationAllowed(view);
        BlockPos anchor = view.publicAnchor();
        Severity severity = severityFor(view.severity(), view.phase());
        return new EventRow(keyOf(view.instanceId()), (byte) severity.ordinal(), safe(view.titleKey()),
                safe(definitionTodoKey(view.definitionId())), safe(todoKey(view.type())), safe(view.whatToDoKey()), view.dimension(), (byte) view.phase().ordinal(),
                clampInt(ticksLeft(view)), view.participantCount(), located,
                located && anchor != null ? anchor.immutable() : BlockPos.ZERO, view.votingOpen(), view.yes(),
                view.no(), needed, myVote, voteBlock.map(ConsoleIntents.Deny::wire).orElse((byte) 0));
    }

    /**
     * The HUD entry for one view, as the viewer at {@code playerPos} in {@code playerDimension} sees it.
     *
     * @param phaseAge     ticks since the view's current phase began
     * @param remaining    hostiles left in the current wave, -1 when not a wave event
     * @param objective    a ready objective line, "" for none
     * @param boundsRadius the definition's bounds radius (the marker shows only inside it)
     */
    public static HudEntry hud(EventView view, long phaseAge, int remaining, String objective,
            @Nullable BlockPos playerPos, @Nullable Identifier playerDimension, int boundsRadius) {
        BlockPos anchor = view.publicAnchor();
        boolean marker = view.phase() == EventPhase.ACTIVE && locationAllowed(view) && anchor != null
                && view.dimension().equals(playerDimension) && withinBounds(anchor, playerPos, boundsRadius);
        Severity severity = severityFor(view.severity(), view.phase());
        BlockPos markerPos = marker && anchor != null ? anchor.immutable() : BlockPos.ZERO;
        return new HudEntry(keyOf(view.instanceId()), (byte) severity.ordinal(), safe(view.titleKey()),
                safe(definitionTodoKey(view.definitionId())), safe(todoKey(view.type())), view.dimension(),
                (byte) view.phase().ordinal(), clampInt(ticksLeft(view)), clampInt(phaseAge), view.votingOpen(),
                view.yes(), view.no(),
                Math.max(0, view.progressDone()), Math.max(0, view.progressTotal()), remaining,
                objective == null ? "" : truncate(objective), marker, markerPos);
    }

    /** Whether a phase is one the console and HUD show (announced, voting or running). */
    public static boolean shown(EventPhase phase) {
        return phase == EventPhase.ANNOUNCED || phase == EventPhase.VOTING || phase == EventPhase.ACTIVE;
    }

    private static String safe(String key) {
        return key == null ? "" : truncate(key);
    }

    private static String truncate(String text) {
        return text.length() <= ConsolePayloads.MAX_STRING ? text : text.substring(0, ConsolePayloads.MAX_STRING);
    }
}
