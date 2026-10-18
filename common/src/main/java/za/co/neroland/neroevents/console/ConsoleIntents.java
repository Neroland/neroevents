package za.co.neroland.neroevents.console;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EventPhase;

/**
 * The console's server-side request rules, pure so they are tested without a server: which deny sentence a
 * request earns, the privacy actions a payload may name, and the per-menu rate limit. The server runs these
 * before touching the engine; the screen mirrors the same reasons on its disabled buttons, but never decides.
 */
public final class ConsoleIntents {

    /** Fewest ticks between two answered requests on one open console. */
    public static final int MIN_INTERVAL_TICKS = 10;

    /** Every refusal the console can show, each with its one plain sentence. */
    public enum Deny {
        VOTE_CLOSED("console.neroevents.deny.vote_closed"),
        ONLINE_ONLY("console.neroevents.deny.online_only"),
        VOTING_OFF("console.neroevents.deny.voting_off"),
        ALREADY_CLAIMED("console.neroevents.deny.already_claimed"),
        NOTHING_TO_CLAIM("console.neroevents.deny.nothing_to_claim"),
        MAKE_ROOM("console.neroevents.deny.make_room"),
        CANCELLED_BY_ADMIN("console.neroevents.deny.cancelled"),
        NEEDS_OPERATOR("console.neroevents.deny.needs_op"),
        EVENT_OVER("console.neroevents.deny.event_over"),
        TOO_FAST("console.neroevents.deny.too_fast"),
        NOT_AT_CONSOLE("console.neroevents.deny.not_at_console");

        private final String key;

        Deny(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** The wire value of {@code ConsolePayloads.EventRow#voteBlock}: 0 = can vote. */
        public byte wire() {
            return (byte) (ordinal() + 1);
        }

        /** The reason a {@code voteBlock} byte names, or {@code null} for "can vote" / an unknown value. */
        public static @Nullable Deny fromWire(byte wire) {
            int index = wire - 1;
            return index >= 0 && index < values().length ? values()[index] : null;
        }
    }

    /** The privacy buttons. Erase is not here: it hands the player Core's own erase command. */
    public enum PrivacyAction {
        EXPORT, OPT_OUT, OPT_IN;

        /** The action a payload byte names, or empty for anything out of range. */
        public static Optional<PrivacyAction> fromWire(byte wire) {
            return wire >= 0 && wire < values().length ? Optional.of(values()[wire]) : Optional.empty();
        }

        public byte wire() {
            return (byte) ordinal();
        }
    }

    private ConsoleIntents() {
    }

    /**
     * Why the viewer cannot vote on an event, or empty when they can.
     *
     * @param found     the key resolved to a live instance
     * @param phase     that instance's phase (ignored when not found)
     * @param tallyOpen its tally is open
     * @param cancelled the key resolved only to a recently cancelled instance
     * @param consent   the consent mode in force
     */
    public static Optional<Deny> checkVote(boolean found, @Nullable EventPhase phase, boolean tallyOpen,
            boolean cancelled, ConsentMode consent) {
        if (!found) {
            return Optional.of(cancelled ? Deny.CANCELLED_BY_ADMIN : Deny.VOTE_CLOSED);
        }
        if (phase == EventPhase.CANCELLED) {
            return Optional.of(Deny.CANCELLED_BY_ADMIN);
        }
        if (consent != ConsentMode.VOTE) {
            return Optional.of(Deny.VOTING_OFF);
        }
        if (phase != EventPhase.VOTING || !tallyOpen) {
            return Optional.of(Deny.VOTE_CLOSED);
        }
        return Optional.empty();
    }

    /**
     * Why the viewer cannot claim, or empty when the claim may run.
     *
     * @param waitingStacks  stacks (or experience entries) waiting across the persisted and session crate
     * @param waitingXp      experience waiting
     * @param hasRoom        at least one free inventory slot
     * @param claimedHere    the viewer already emptied the crate from this console
     */
    public static Optional<Deny> checkClaim(int waitingStacks, long waitingXp, boolean hasRoom, boolean claimedHere) {
        if (waitingStacks <= 0 && waitingXp <= 0L) {
            return Optional.of(claimedHere ? Deny.ALREADY_CLAIMED : Deny.NOTHING_TO_CLAIM);
        }
        if (waitingStacks > 0 && !hasRoom && waitingXp <= 0L) {
            return Optional.of(Deny.MAKE_ROOM);
        }
        return Optional.empty();
    }

    /** Operator controls: refused for anyone without the permission. */
    public static Optional<Deny> checkOperator(boolean operator) {
        return operator ? Optional.empty() : Optional.of(Deny.NEEDS_OPERATOR);
    }

    /**
     * The per-menu rate limit: whether a request at {@code now} may be answered after one at {@code last}
     * ({@link Long#MIN_VALUE} = never). A clock that went backwards resets.
     */
    public static boolean allowed(long last, long now) {
        return last == Long.MIN_VALUE || now < last || now - last >= MIN_INTERVAL_TICKS;
    }
}
