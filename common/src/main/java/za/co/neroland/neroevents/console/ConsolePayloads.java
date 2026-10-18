package za.co.neroland.neroevents.console;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The Event Console and HUD payloads on {@code neroevents:main}. Every field is something a screen draws;
 * nothing names or identifies a player, and an event is addressed by an opaque per-instance {@code key}
 * (a hash, never the instance UUID). Vote tallies are numbers only; a location is sent only for an event
 * whose definition makes it public and that is not a raid or invasion target.
 *
 * <p>Clientbound: {@link ConsoleSnapshot} (the console's five tabs plus the footer answer) and {@link HudState}
 * (what the HUD draws). Serverbound: {@link ConsoleVote}, {@link ConsoleClaim}, {@link ConsolePrivacy},
 * {@link ConsoleCancel} and {@link ConsoleRefresh} — requests the server validates against the open console
 * menu, never facts it trusts.
 */
public final class ConsolePayloads {

    /** Most live/soon rows, history rows and ledger rows a snapshot carries. */
    public static final int MAX_ROWS = 16;
    public static final int MAX_HISTORY = 20;
    public static final int MAX_LEDGER = 20;
    /** Most HUD entries in one state packet. */
    public static final int MAX_HUD = 3;
    /** Longest translation key or short text accepted on the wire. */
    public static final int MAX_STRING = 128;

    /** "No vote yet" for {@link EventRow#myVote()}. */
    public static final byte NO_VOTE = -1;

    private ConsolePayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path));
    }

    // --- shared row types --------------------------------------------------------------------------------

    /**
     * One live or upcoming event, as a console row shows it.
     *
     * @param key          opaque handle the vote / cancel buttons send back (never shown)
     * @param severity     {@code Severity} ordinal after the phase switch (supply drops are INFO until active)
     * @param titleKey     the definition's title translation key
     * @param definitionTodoKey the definition's own "what to do" key ({@code event.<ns>.<definition>.todo}),
     *                     used when a language file has it
     * @param todoKey      the type's "what to do" key ({@code event.neroevents.<type>.todo}, {@code %s} = time)
     * @param whatToDoKey  the definition's longer what-to-do key (fallback when the type has no todo line)
     * @param dimension    where it happens
     * @param phase        {@code EventPhase} ordinal
     * @param ticksLeft    until it starts (upcoming) or ends (active)
     * @param participants credited players so far (a count)
     * @param located      whether {@code location} may be shown (public location, never a raid target)
     * @param location     the public anchor, {@link BlockPos#ZERO} when not located
     * @param votingOpen   a consent vote is open on this event
     * @param yes          yes votes (a number)
     * @param no           no votes (a number)
     * @param needed       votes needed for quorum (a number)
     * @param myVote       the viewer's own vote: {@link #NO_VOTE}, 0 = no, 1 = yes
     * @param voteBlock    {@code ConsoleIntents.Deny} ordinal + 1 when the viewer cannot vote, 0 when they can
     */
    public record EventRow(int key, byte severity, String titleKey, String definitionTodoKey, String todoKey,
            String whatToDoKey, Identifier dimension, byte phase, int ticksLeft, int participants, boolean located,
            BlockPos location, boolean votingOpen, int yes, int no, int needed, byte myVote, byte voteBlock) {

        static void write(FriendlyByteBuf buf, EventRow row) {
            buf.writeInt(row.key);
            buf.writeByte(row.severity);
            buf.writeUtf(row.titleKey, MAX_STRING);
            buf.writeUtf(row.definitionTodoKey, MAX_STRING);
            buf.writeUtf(row.todoKey, MAX_STRING);
            buf.writeUtf(row.whatToDoKey, MAX_STRING);
            buf.writeIdentifier(row.dimension);
            buf.writeByte(row.phase);
            buf.writeVarInt(Math.max(0, row.ticksLeft));
            buf.writeVarInt(Math.max(0, row.participants));
            buf.writeBoolean(row.located);
            if (row.located) {
                buf.writeBlockPos(row.location);
            }
            buf.writeBoolean(row.votingOpen);
            buf.writeVarInt(Math.max(0, row.yes));
            buf.writeVarInt(Math.max(0, row.no));
            buf.writeVarInt(Math.max(0, row.needed));
            buf.writeByte(row.myVote);
            buf.writeByte(row.voteBlock);
        }

        static EventRow read(FriendlyByteBuf buf) {
            int key = buf.readInt();
            byte severity = buf.readByte();
            String title = buf.readUtf(MAX_STRING);
            String definitionTodo = buf.readUtf(MAX_STRING);
            String todo = buf.readUtf(MAX_STRING);
            String whatToDo = buf.readUtf(MAX_STRING);
            Identifier dimension = buf.readIdentifier();
            byte phase = buf.readByte();
            int ticksLeft = buf.readVarInt();
            int participants = buf.readVarInt();
            boolean located = buf.readBoolean();
            BlockPos location = located ? buf.readBlockPos() : BlockPos.ZERO;
            boolean votingOpen = buf.readBoolean();
            int yes = buf.readVarInt();
            int no = buf.readVarInt();
            int needed = buf.readVarInt();
            byte myVote = buf.readByte();
            byte voteBlock = buf.readByte();
            return new EventRow(key, severity, title, definitionTodo, todo, whatToDo, dimension, phase, ticksLeft,
                    participants, located, location, votingOpen, yes, no, needed, myVote, voteBlock);
        }
    }

    /**
     * One past event for the Past tab: enough for {@code HistorySentences} to build a plain sentence, nothing
     * that identifies anyone (no anchor, no participant list).
     *
     * @param typePath     the payload type's path (selects the sentence template)
     * @param titleKey     the definition title key (fallback template)
     * @param dimension    where it happened
     * @param terminal     {@code EventPhase} ordinal (DONE, SKIPPED or CANCELLED)
     * @param outcome      {@code EventOutcome} ordinal, or -1 for none
     * @param ageSeconds   seconds since it ended
     * @param participants how many players took part (a count)
     */
    public record HistoryRow(String typePath, String titleKey, Identifier dimension, byte terminal, byte outcome,
            long ageSeconds, int participants) {

        static void write(FriendlyByteBuf buf, HistoryRow row) {
            buf.writeUtf(row.typePath, MAX_STRING);
            buf.writeUtf(row.titleKey, MAX_STRING);
            buf.writeIdentifier(row.dimension);
            buf.writeByte(row.terminal);
            buf.writeByte(row.outcome);
            buf.writeVarLong(Math.max(0L, row.ageSeconds));
            buf.writeVarInt(Math.max(0, row.participants));
        }

        static HistoryRow read(FriendlyByteBuf buf) {
            String typePath = buf.readUtf(MAX_STRING);
            String titleKey = buf.readUtf(MAX_STRING);
            Identifier dimension = buf.readIdentifier();
            byte terminal = buf.readByte();
            byte outcome = buf.readByte();
            long age = buf.readVarLong();
            int participants = buf.readVarInt();
            return new HistoryRow(typePath, titleKey, dimension, terminal, outcome, age, participants);
        }
    }

    /** One of the viewer's own reward ledger rows (only ever sent to that player). */
    public record LedgerRow(String titleKey, int items, long credits, long xp, long ageSeconds) {

        static void write(FriendlyByteBuf buf, LedgerRow row) {
            buf.writeUtf(row.titleKey, MAX_STRING);
            buf.writeVarInt(Math.max(0, row.items));
            buf.writeVarLong(Math.max(0L, row.credits));
            buf.writeVarLong(Math.max(0L, row.xp));
            buf.writeVarLong(Math.max(0L, row.ageSeconds));
        }

        static LedgerRow read(FriendlyByteBuf buf) {
            String titleKey = buf.readUtf(MAX_STRING);
            int items = buf.readVarInt();
            long credits = buf.readVarLong();
            long xp = buf.readVarLong();
            long age = buf.readVarLong();
            return new LedgerRow(titleKey, items, credits, xp, age);
        }
    }

    /**
     * One HUD element group: the countdown pill / banner of an event, its objective lines and (only within the
     * event's bounds, only for a public, non-raid location) the world marker target.
     *
     * @param definitionTodoKey the definition's own "what to do" key, used when a language file has it
     * @param phaseAge      ticks since the event entered its current phase (the banner shows for the first 100)
     * @param progressDone  objective progress (waves fought, ...), 0 when none
     * @param progressTotal objective total, 0 when none
     * @param remaining     hostiles left in the current wave, -1 when not a wave event
     * @param objective     a ready objective line (faction war: faction display names and numbers), "" for none
     * @param marker        whether {@code markerPos} is set
     */
    public record HudEntry(int key, byte severity, String titleKey, String definitionTodoKey, String todoKey,
            Identifier dimension, byte phase, int ticksLeft, int phaseAge, boolean votingOpen, int yes, int no,
            int progressDone, int progressTotal, int remaining, String objective, boolean marker,
            BlockPos markerPos) {

        static void write(FriendlyByteBuf buf, HudEntry e) {
            buf.writeInt(e.key);
            buf.writeByte(e.severity);
            buf.writeUtf(e.titleKey, MAX_STRING);
            buf.writeUtf(e.definitionTodoKey, MAX_STRING);
            buf.writeUtf(e.todoKey, MAX_STRING);
            buf.writeIdentifier(e.dimension);
            buf.writeByte(e.phase);
            buf.writeVarInt(Math.max(0, e.ticksLeft));
            buf.writeVarInt(Math.max(0, e.phaseAge));
            buf.writeBoolean(e.votingOpen);
            buf.writeVarInt(Math.max(0, e.yes));
            buf.writeVarInt(Math.max(0, e.no));
            buf.writeVarInt(Math.max(0, e.progressDone));
            buf.writeVarInt(Math.max(0, e.progressTotal));
            buf.writeVarInt(Math.max(-1, e.remaining) + 1);
            buf.writeUtf(e.objective, MAX_STRING);
            buf.writeBoolean(e.marker);
            if (e.marker) {
                buf.writeBlockPos(e.markerPos);
            }
        }

        static HudEntry read(FriendlyByteBuf buf) {
            int key = buf.readInt();
            byte severity = buf.readByte();
            String title = buf.readUtf(MAX_STRING);
            String definitionTodo = buf.readUtf(MAX_STRING);
            String todo = buf.readUtf(MAX_STRING);
            Identifier dimension = buf.readIdentifier();
            byte phase = buf.readByte();
            int ticksLeft = buf.readVarInt();
            int phaseAge = buf.readVarInt();
            boolean votingOpen = buf.readBoolean();
            int yes = buf.readVarInt();
            int no = buf.readVarInt();
            int done = buf.readVarInt();
            int total = buf.readVarInt();
            int remaining = buf.readVarInt() - 1;
            String objective = buf.readUtf(MAX_STRING);
            boolean marker = buf.readBoolean();
            BlockPos pos = marker ? buf.readBlockPos() : BlockPos.ZERO;
            return new HudEntry(key, severity, title, definitionTodo, todo, dimension, phase, ticksLeft, phaseAge,
                    votingOpen, yes, no, done, total, remaining, objective, marker, pos);
        }
    }

    // --- clientbound ---------------------------------------------------------------------------------------

    /**
     * Everything the console shows for one viewer, plus the answer to their last request.
     *
     * @param operator        the viewer may use the operator controls (display only; the server re-checks)
     * @param consent         {@code ConsentMode} ordinal in force
     * @param singlePlayer    an integrated world with one player (no vote strip)
     * @param waitingItems    items waiting in the viewer's reward crate
     * @param waitingXp       experience waiting in the crate
     * @param roomInInventory the viewer has at least one free inventory slot
     * @param alreadyClaimed  the viewer emptied the crate from this console in this sitting
     * @param ledgerEnabled   the server keeps a reward ledger
     * @param optedOut        the viewer opted out of the ledger
     * @param ledgerDays      ledger retention
     * @param crateDays       crate retention
     * @param ok              the footer answer is a success
     * @param messageKey      footer message key, "" for none
     * @param messageArg      the message's one argument, "" for none (a number or a duration)
     */
    public record ConsoleSnapshot(List<EventRow> live, List<EventRow> soon, List<HistoryRow> past,
            boolean operator, byte consent, boolean singlePlayer, int waitingItems, long waitingXp,
            boolean roomInInventory, boolean alreadyClaimed, boolean ledgerEnabled, boolean optedOut,
            int ledgerDays, int crateDays, List<LedgerRow> ledger, boolean ok, String messageKey,
            String messageArg) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ConsoleSnapshot> TYPE = typeOf("console_snapshot");

        public static final StreamCodec<FriendlyByteBuf, ConsoleSnapshot> STREAM_CODEC =
                StreamCodec.of(ConsoleSnapshot::write, ConsoleSnapshot::read);

        public ConsoleSnapshot {
            live = List.copyOf(live);
            soon = List.copyOf(soon);
            past = List.copyOf(past);
            ledger = List.copyOf(ledger);
        }

        private static void write(FriendlyByteBuf buf, ConsoleSnapshot s) {
            writeList(buf, s.live, MAX_ROWS, EventRow::write);
            writeList(buf, s.soon, MAX_ROWS, EventRow::write);
            writeList(buf, s.past, MAX_HISTORY, HistoryRow::write);
            buf.writeBoolean(s.operator);
            buf.writeByte(s.consent);
            buf.writeBoolean(s.singlePlayer);
            buf.writeVarInt(Math.max(0, s.waitingItems));
            buf.writeVarLong(Math.max(0L, s.waitingXp));
            buf.writeBoolean(s.roomInInventory);
            buf.writeBoolean(s.alreadyClaimed);
            buf.writeBoolean(s.ledgerEnabled);
            buf.writeBoolean(s.optedOut);
            buf.writeVarInt(Math.max(0, s.ledgerDays));
            buf.writeVarInt(Math.max(0, s.crateDays));
            writeList(buf, s.ledger, MAX_LEDGER, LedgerRow::write);
            buf.writeBoolean(s.ok);
            buf.writeUtf(s.messageKey, MAX_STRING);
            buf.writeUtf(s.messageArg, MAX_STRING);
        }

        private static ConsoleSnapshot read(FriendlyByteBuf buf) {
            List<EventRow> live = readList(buf, MAX_ROWS, EventRow::read);
            List<EventRow> soon = readList(buf, MAX_ROWS, EventRow::read);
            List<HistoryRow> past = readList(buf, MAX_HISTORY, HistoryRow::read);
            boolean operator = buf.readBoolean();
            byte consent = buf.readByte();
            boolean singlePlayer = buf.readBoolean();
            int waitingItems = buf.readVarInt();
            long waitingXp = buf.readVarLong();
            boolean room = buf.readBoolean();
            boolean claimed = buf.readBoolean();
            boolean ledgerEnabled = buf.readBoolean();
            boolean optedOut = buf.readBoolean();
            int ledgerDays = buf.readVarInt();
            int crateDays = buf.readVarInt();
            List<LedgerRow> ledger = readList(buf, MAX_LEDGER, LedgerRow::read);
            boolean ok = buf.readBoolean();
            String key = buf.readUtf(MAX_STRING);
            String arg = buf.readUtf(MAX_STRING);
            return new ConsoleSnapshot(live, soon, past, operator, consent, singlePlayer, waitingItems, waitingXp,
                    room, claimed, ledgerEnabled, optedOut, ledgerDays, crateDays, ledger, ok, key, arg);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What the HUD draws, re-sent every second while anything is announced or running. */
    public record HudState(List<HudEntry> entries) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<HudState> TYPE = typeOf("hud_state");

        public static final StreamCodec<FriendlyByteBuf, HudState> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> writeList(buf, s.entries, MAX_HUD, HudEntry::write),
                buf -> new HudState(readList(buf, MAX_HUD, HudEntry::read)));

        public static final HudState EMPTY = new HudState(List.of());

        public HudState {
            entries = List.copyOf(entries);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // --- serverbound ---------------------------------------------------------------------------------------

    /** Cast or change the viewer's vote on the event {@code key}. */
    public record ConsoleVote(int key, boolean yes) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ConsoleVote> TYPE = typeOf("console_vote");

        public static final StreamCodec<FriendlyByteBuf, ConsoleVote> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.key);
                    buf.writeBoolean(p.yes);
                },
                buf -> new ConsoleVote(buf.readInt(), buf.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Hand over the viewer's reward crate. */
    public record ConsoleClaim() implements CustomPacketPayload {

        public static final ConsoleClaim INSTANCE = new ConsoleClaim();
        public static final CustomPacketPayload.Type<ConsoleClaim> TYPE = typeOf("console_claim");
        public static final StreamCodec<FriendlyByteBuf, ConsoleClaim> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A privacy action for the viewer: {@code ConsoleIntents.PrivacyAction} ordinal. */
    public record ConsolePrivacy(byte action) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ConsolePrivacy> TYPE = typeOf("console_privacy");

        public static final StreamCodec<FriendlyByteBuf, ConsolePrivacy> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByte(p.action), buf -> new ConsolePrivacy(buf.readByte()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Operator only: call off the event {@code key}. */
    public record ConsoleCancel(int key) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ConsoleCancel> TYPE = typeOf("console_cancel");

        public static final StreamCodec<FriendlyByteBuf, ConsoleCancel> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeInt(p.key), buf -> new ConsoleCancel(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Ask for a fresh snapshot (the console sends this when it opens). */
    public record ConsoleRefresh() implements CustomPacketPayload {

        public static final ConsoleRefresh INSTANCE = new ConsoleRefresh();
        public static final CustomPacketPayload.Type<ConsoleRefresh> TYPE = typeOf("console_refresh");
        public static final StreamCodec<FriendlyByteBuf, ConsoleRefresh> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // --- list helpers (26.3 dropped FriendlyByteBuf#readList/writeCollection) ------------------------------

    static <T> void writeList(FriendlyByteBuf buf, List<T> list, int max, BiConsumer<FriendlyByteBuf, T> writer) {
        int n = Math.min(max, list.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new IllegalArgumentException("list of " + n + " exceeds " + max);
        }
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(reader.apply(buf));
        }
        return out;
    }
}
