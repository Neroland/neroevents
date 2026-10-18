package za.co.neroland.neroevents.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventSignal;
import za.co.neroland.neroevents.event.EventSignals;
import za.co.neroland.neroevents.event.Severity;

/**
 * The announcement cues: when an event is announced or starts, every online player hears the cue of its
 * severity (INFO a rising chime pair, REWARD a level-up, HAZARD a beacon drop then a bass note, COMBAT a raid
 * horn, WAR a low horn with a bell), and a vote opening plays a click. "A then B" pairs play their
 * {@code _tail} six ticks later; "A + B" pairs play their {@code _layer} together.
 *
 * <p>Each player gets the cue at their own position from the server, like any other sound; the packet names
 * the sound only. Driven by the engine's signal bus; the tails are queued and played from {@link #tick}, which
 * the engine calls every server tick (and which also tells this class which server is running, so tests that
 * publish signals without a server hear nothing).
 */
public final class EventSounds {

    /** One cue: a {@code neroevents:} sound path and its delay in ticks. */
    public record Cue(String path, int delayTicks) {
    }

    /** "A then B": the tail's delay. */
    public static final int TAIL_DELAY_TICKS = 6;
    public static final String VOTE_OPEN = "event.vote.open";

    private record Pending(String path, int dueTick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static volatile @Nullable MinecraftServer server;
    private static boolean subscribed;

    private EventSounds() {
    }

    /** Common init: listens to the engine's lifecycle signals. */
    public static synchronized void init() {
        if (subscribed) {
            return;
        }
        subscribed = true;
        EventSignals.subscribe(EventSounds::onSignal);
    }

    /** The cues of one severity, in play order. */
    public static List<Cue> announceCues(Severity severity) {
        if (severity == null) {
            return List.of();
        }
        return switch (severity) {
            case INFO -> List.of(new Cue("event.announce.info", 0),
                    new Cue("event.announce.info_tail", TAIL_DELAY_TICKS));
            case REWARD -> List.of(new Cue("event.announce.reward", 0));
            case HAZARD -> List.of(new Cue("event.announce.hazard", 0),
                    new Cue("event.announce.hazard_tail", TAIL_DELAY_TICKS));
            case COMBAT -> List.of(new Cue("event.announce.combat", 0));
            case WAR -> List.of(new Cue("event.announce.war", 0), new Cue("event.announce.war_layer", 0));
        };
    }

    /** The cues for one lifecycle signal (empty when it has none). */
    public static List<Cue> cuesFor(EventSignal.Kind kind, @Nullable Severity severity) {
        if (kind == null) {
            return List.of();
        }
        return switch (kind) {
            case ANNOUNCED, STARTED -> announceCues(severity);
            case VOTING_OPENED -> List.of(new Cue(VOTE_OPEN, 0));
            default -> List.of();
        };
    }

    private static void onSignal(EventSignal signal) {
        MinecraftServer running = server;
        if (running == null || signal == null || signal.view() == null) {
            return;
        }
        if (signal.kind() == EventSignal.Kind.STARTED && signal.view().phase() != EventPhase.ACTIVE) {
            return;
        }
        int now = running.getTickCount();
        for (Cue cue : cuesFor(signal.kind(), signal.view().severity())) {
            if (cue.delayTicks() <= 0) {
                playToAll(running, cue.path());
            } else {
                synchronized (PENDING) {
                    PENDING.add(new Pending(cue.path(), now + cue.delayTicks()));
                }
            }
        }
    }

    /** Every server tick (from the engine): remembers the server and plays the tails that are due. */
    public static void tick(MinecraftServer current) {
        if (current != server) {
            reset(); // a new world: nothing queued on the last one carries over
        }
        server = current;
        if (current == null) {
            return;
        }
        List<String> due = null;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            int now = current.getTickCount();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
                Pending p = it.next();
                if (p.dueTick() <= now) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(p.path());
                    it.remove();
                }
            }
        }
        if (due != null) {
            for (String path : due) {
                playToAll(current, path);
            }
        }
    }

    /** Server stop: forget the server and any queued tail. */
    public static void reset() {
        server = null;
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    private static void playToAll(MinecraftServer running, String path) {
        try {
            SoundEvent event = SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path));
            Holder<SoundEvent> holder = Holder.direct(event);
            for (ServerPlayer player : running.getPlayerList().getPlayers()) {
                // Sent to this player only, at their own position: everyone hears it once.
                player.connection.send(new ClientboundSoundPacket(holder, SoundSource.MASTER, player.getX(),
                        player.getY(), player.getZ(), 0.8F, 1.0F, player.getRandom().nextLong()));
            }
        } catch (RuntimeException | LinkageError e) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] cue {} not played ({})", path, e.getClass().getName());
        }
    }
}
