package za.co.neroland.neroevents.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.ClientConsoleState;
import za.co.neroland.neroevents.client.ClientHudState;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleCancel;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleClaim;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsolePrivacy;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleRefresh;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleVote;
import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.creatures.WavePayloadBase;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EventBounds;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.VoteTally;
import za.co.neroland.neroevents.history.EventHistoryState;
import za.co.neroland.neroevents.history.HistoryEntry;
import za.co.neroland.neroevents.network.EventNetwork;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.privacy.EventsPrivacyActions;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardCrates;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;
import za.co.neroland.neroevents.war.FactionWarPayload;

/**
 * The server side of the Event Console and the HUD: builds each viewer's {@link ConsoleSnapshot}, answers the
 * console's requests and pushes {@link HudState} once a second. Every request is checked against the open
 * console menu and the per-sitting rate limit, then routed through the same engine, crate and privacy calls the
 * {@code /neroevents} commands use. Answers go only to the requesting player and carry only their own data;
 * tallies are numbers and nothing names another player. Server thread only.
 */
public final class ConsoleServer {

    /** Ticks between two HUD pushes while something is announced or running (and console refreshes). */
    public static final int PUSH_TICKS = 20;
    /** Ticks between two "nothing happening" HUD pushes (they clear a stale HUD). */
    public static final int IDLE_PUSH_TICKS = 100;

    private static final String MESSAGE_FAILED = "console.neroevents.deny.no_answer";

    private static int screenTick = Integer.MIN_VALUE;
    private static @Nullable MinecraftServer screenServer;
    private static byte screenSeverity = EventConsoleBlockEntity.NONE;
    private static boolean tickFailureReported;

    private ConsoleServer() {
    }

    /** Declares the console and HUD payloads (called from {@code EventNetwork.init()}). */
    public static void register() {
        EventNetwork.clientbound(ConsoleSnapshot.TYPE, ConsoleSnapshot.STREAM_CODEC, ClientConsoleState::onSnapshot);
        EventNetwork.clientbound(HudState.TYPE, HudState.STREAM_CODEC, ClientHudState::onState);
        EventNetwork.serverbound(ConsoleRefresh.TYPE, ConsoleRefresh.STREAM_CODEC, (p, player) -> guarded(player,
                "refresh", menu -> sendSnapshot(player, menu, true, "", "")));
        EventNetwork.serverbound(ConsoleVote.TYPE, ConsoleVote.STREAM_CODEC, (p, player) -> guarded(player, "vote",
                menu -> onVote(p, player, menu)));
        EventNetwork.serverbound(ConsoleClaim.TYPE, ConsoleClaim.STREAM_CODEC, (p, player) -> guarded(player, "claim",
                menu -> onClaim(player, menu)));
        EventNetwork.serverbound(ConsolePrivacy.TYPE, ConsolePrivacy.STREAM_CODEC, (p, player) -> guarded(player,
                "privacy", menu -> onPrivacy(p, player, menu)));
        EventNetwork.serverbound(ConsoleCancel.TYPE, ConsoleCancel.STREAM_CODEC, (p, player) -> guarded(player,
                "cancel", menu -> onCancel(p, player, menu)));
    }

    // --- tick: HUD push and open-console refresh -----------------------------------------------------------

    /** Each loader calls this from its server tick. Cheap when nothing is announced or running. */
    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % PUSH_TICKS != 0) {
            return;
        }
        try {
            EventEngine engine = EventEngine.current();
            if (engine == null) {
                return;
            }
            List<EventInstance> shown = shownInstances(engine);
            boolean idle = shown.isEmpty();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!idle || tick % IDLE_PUSH_TICKS == 0) {
                    Services.NETWORK.sendToPlayer(player, hudFor(engine, shown, player, server));
                }
                if (player.containerMenu instanceof EventConsoleMenu menu && menu.stillValid(player)) {
                    sendSnapshot(player, menu, true, "", "");
                }
            }
        } catch (RuntimeException | LinkageError e) {
            if (!tickFailureReported) {
                tickFailureReported = true;
                NeroEventsTelemetry.captureHandledException(e, "console", "tick");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] The console/HUD refresh failed ({}).", e.getClass().getName());
            }
        }
    }

    /** The severity the console screens show right now ({@code NONE} when nothing is announced or running). */
    static byte screenSeverity(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick == screenTick && server == screenServer) {
            return screenSeverity;
        }
        screenTick = tick;
        screenServer = server;
        byte result = EventConsoleBlockEntity.NONE;
        EventEngine engine = EventEngine.current();
        if (engine != null) {
            long now = engine.now();
            Optional<EventView> top = engine.live().stream().filter(i -> ConsoleRows.shown(i.phase()))
                    .map(i -> i.view(now)).min(ConsoleRows.URGENCY);
            if (top.isPresent()) {
                result = (byte) ConsoleRows.severityFor(top.get().severity(), top.get().phase()).ordinal();
            }
        }
        screenSeverity = result;
        return result;
    }

    private static List<EventInstance> shownInstances(EventEngine engine) {
        List<EventInstance> out = new ArrayList<>();
        for (EventInstance instance : engine.live()) {
            if (ConsoleRows.shown(instance.phase())) {
                out.add(instance);
            }
        }
        return out;
    }

    private static HudState hudFor(EventEngine engine, List<EventInstance> shown, ServerPlayer player,
            MinecraftServer server) {
        if (shown.isEmpty()) {
            return HudState.EMPTY;
        }
        long now = engine.now();
        int needed = needed(engine, server);
        BlockPos pos = player.blockPosition();
        Identifier dimension = player.level().dimension().identifier();
        List<EventInstance> sorted = new ArrayList<>(shown);
        sorted.sort((a, b) -> ConsoleRows.URGENCY.compare(a.view(now), b.view(now)));
        List<HudEntry> entries = new ArrayList<>();
        for (EventInstance instance : sorted) {
            if (entries.size() >= ConsolePayloads.MAX_HUD) {
                break;
            }
            EventView view = instance.view(now);
            EventBounds bounds = instance.definition().bounds();
            int radius = bounds.kind() == EventBounds.Kind.DIMENSION ? 0 : bounds.radius();
            entries.add(ConsoleRows.hud(view, Math.max(0L, now - instance.phaseSince()), needed, remaining(instance),
                    FactionWarPayload.objectiveText(instance.id()).orElse(""), pos, dimension, radius));
        }
        return new HudState(entries);
    }

    /** Hostiles left in the current wave, or -1 for anything that is not a wave event. */
    private static int remaining(EventInstance instance) {
        return instance.payloadState() instanceof WavePayloadBase.State waves ? waves.alive().size() : -1;
    }

    private static int needed(EventEngine engine, MinecraftServer server) {
        return ConsoleRows.quorum(engine.settings().voteQuorumPlayers, engine.settings().voteQuorumFraction,
                server.getPlayerCount());
    }

    // --- snapshot ------------------------------------------------------------------------------------------

    /** Sends {@code player} their snapshot with a footer answer ({@code key} "" for none). */
    public static void sendSnapshot(ServerPlayer player, EventConsoleMenu menu, boolean ok, String key, String arg) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, snapshot(server, player, menu, ok, key, arg));
    }

    private static ConsoleSnapshot snapshot(MinecraftServer server, ServerPlayer player, EventConsoleMenu menu,
            boolean ok, String key, String arg) {
        EventEngine engine = EventEngine.get(server);
        UUID viewer = player.getUUID();
        long now = engine.now();
        ConsentMode consent = engine.effectiveConsent();
        int needed = needed(engine, server);
        Map<Identifier, EventDefinition> defs = engine.definitions();

        List<EventInstance> live = new ArrayList<>();
        List<EventInstance> soon = new ArrayList<>();
        for (EventInstance instance : engine.live()) {
            if (instance.phase() == EventPhase.ACTIVE) {
                live.add(instance);
            } else if (instance.phase() == EventPhase.ANNOUNCED || instance.phase() == EventPhase.VOTING) {
                soon.add(instance);
            }
        }
        List<EventRow> liveRows = rows(live, now, needed, viewer, consent);
        List<EventRow> soonRows = rows(soon, now, needed, viewer, consent);

        List<HistoryRow> past = new ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (HistoryEntry entry : EventHistoryState.get(server).latest(ConsolePayloads.MAX_HISTORY)) {
            EventDefinition def = defs.get(entry.definitionId());
            past.add(new HistoryRow(entry.type().getPath(), def == null ? "" : def.display().title(),
                    entry.dimension(), (byte) entry.terminal().ordinal(),
                    (byte) entry.outcome().map(Enum::ordinal).orElse(-1).intValue(),
                    Math.max(0L, (nowMs - entry.endedAt()) / 1000L), entry.participantCount()));
        }

        int items = 0;
        long xp = 0L;
        List<CrateEntry> crate = new ArrayList<>(RewardCrateState.get(server).entriesFor(viewer));
        engine.crates().ifPresent(c -> crate.addAll(c.sessionEntries(viewer)));
        for (CrateEntry entry : crate) {
            items += entry.itemCount();
            xp += Math.max(0L, entry.xp());
        }

        RewardConfig cfg = RewardConfig.fromConfig();
        boolean optedOut = PrivacyOptOuts.get(server).isOptedOut(viewer);
        List<LedgerRow> ledger = new ArrayList<>();
        if (cfg.rewardLedgerEnabled && !optedOut) {
            List<RewardLedgerEntry> rows = RewardLedgerState.get(server).rowsFor(viewer);
            for (int i = rows.size() - 1; i >= 0 && ledger.size() < ConsolePayloads.MAX_LEDGER; i--) {
                RewardLedgerEntry row = rows.get(i);
                EventDefinition def = defs.get(row.definitionId());
                ledger.add(new LedgerRow(def == null ? "" : def.display().title(), row.summary().itemCount(),
                        row.summary().currencyPaid(), row.summary().xpGranted(),
                        Math.max(0L, (nowMs - row.at()) / 1000L)));
            }
        }

        boolean single = !server.isDedicatedServer() && server.getPlayerCount() <= 1;
        return new ConsoleSnapshot(liveRows, soonRows, past, isOperator(player), (byte) consent.ordinal(), single,
                items, xp, player.getInventory().getFreeSlot() >= 0, menu.claimedHere(), cfg.rewardLedgerEnabled,
                optedOut, cfg.ledgerDays(), cfg.crateDays(), ledger, ok, key, arg);
    }

    private static List<EventRow> rows(List<EventInstance> instances, long now, int needed, UUID viewer,
            ConsentMode consent) {
        List<EventInstance> sorted = new ArrayList<>(instances);
        sorted.sort((a, b) -> ConsoleRows.URGENCY.compare(a.view(now), b.view(now)));
        List<EventRow> out = new ArrayList<>();
        for (EventInstance instance : sorted) {
            if (out.size() >= ConsolePayloads.MAX_ROWS) {
                break;
            }
            VoteTally votes = instance.votes();
            byte mine = votes.choiceOf(viewer).map(yes -> (byte) (yes ? 1 : 0)).orElse(ConsolePayloads.NO_VOTE);
            Optional<ConsoleIntents.Deny> block = ConsoleIntents.checkVote(true, instance.phase(), votes.open(), false,
                    consent);
            out.add(ConsoleRows.row(instance.view(now), needed, mine, block));
        }
        return out;
    }

    // --- requests ------------------------------------------------------------------------------------------

    private interface MenuAction {
        void run(EventConsoleMenu menu);
    }

    /**
     * Every request: the sender must have the console menu open and still be at the console; one answer per
     * {@link ConsoleIntents#MIN_INTERVAL_TICKS}. A failure answers "try again" and never reaches the player as a
     * stack trace.
     */
    private static void guarded(ServerPlayer player, String operation, MenuAction action) {
        if (!(player.containerMenu instanceof EventConsoleMenu menu) || !menu.stillValid(player)) {
            return; // not at a console: nothing to answer on
        }
        if (!menu.tryRequest(player.level().getGameTime())) {
            sendSnapshot(player, menu, false, ConsoleIntents.Deny.TOO_FAST.key(), "");
            return;
        }
        try {
            action.run(menu);
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "console", operation);
            NeroEventsCommon.LOGGER.warn("[NeroEvents] A console {} request failed ({}).", operation,
                    e.getClass().getName());
            sendSnapshot(player, menu, false, MESSAGE_FAILED, "");
        }
    }

    private static @Nullable EventInstance find(List<EventInstance> instances, int key) {
        for (EventInstance instance : instances) {
            if (ConsoleRows.keyOf(instance.id()) == key) {
                return instance;
            }
        }
        return null;
    }

    private static void onVote(ConsoleVote payload, ServerPlayer player, EventConsoleMenu menu) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        EventEngine engine = EventEngine.get(server);
        EventInstance target = find(engine.live(), payload.key());
        EventInstance recent = target == null ? find(engine.recent(), payload.key()) : null;
        boolean cancelled = recent != null && recent.phase() == EventPhase.CANCELLED;
        Optional<ConsoleIntents.Deny> deny = ConsoleIntents.checkVote(target != null,
                target == null ? null : target.phase(), target != null && target.votes().open(), cancelled,
                engine.effectiveConsent());
        if (deny.isPresent() || target == null) {
            sendSnapshot(player, menu, false, deny.orElse(ConsoleIntents.Deny.VOTE_CLOSED).key(), "");
            return;
        }
        // The same engine path as /neroevents vote.
        VoteTally.CastResult result = engine.vote(target, player.getUUID(), payload.yes());
        switch (result) {
            case RECORDED -> sendSnapshot(player, menu, true,
                    payload.yes() ? "console.neroevents.vote.recorded_yes" : "console.neroevents.vote.recorded_no", "");
            case CHANGED -> sendSnapshot(player, menu, true,
                    payload.yes() ? "console.neroevents.vote.changed_yes" : "console.neroevents.vote.changed_no", "");
            case CLOSED -> sendSnapshot(player, menu, false, ConsoleIntents.Deny.VOTE_CLOSED.key(), "");
        }
    }

    private static void onClaim(ServerPlayer player, EventConsoleMenu menu) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        EventEngine engine = EventEngine.get(server);
        RewardCrates crates = engine.crates().orElse(null);
        UUID viewer = player.getUUID();
        int stacks = crates == null ? 0 : crates.waitingStacks(viewer);
        long xp = 0L;
        for (CrateEntry entry : RewardCrateState.get(server).entriesFor(viewer)) {
            xp += Math.max(0L, entry.xp());
        }
        if (crates != null) {
            for (CrateEntry entry : crates.sessionEntries(viewer)) {
                xp += Math.max(0L, entry.xp());
            }
        }
        Optional<ConsoleIntents.Deny> deny = ConsoleIntents.checkClaim(stacks, xp,
                player.getInventory().getFreeSlot() >= 0, menu.claimedHere());
        if (deny.isPresent() || crates == null) {
            sendSnapshot(player, menu, false, deny.orElse(ConsoleIntents.Deny.NOTHING_TO_CLAIM).key(), "");
            return;
        }
        // The same crate path as /neroevents claim.
        RewardCrates.ClaimResult result = crates.claim(player);
        switch (result.status()) {
            case CLAIMED -> {
                menu.markClaimed();
                sendSnapshot(player, menu, true, "console.neroevents.claim.done", Integer.toString(result.stacksGiven()));
            }
            case PARTIAL -> {
                if (result.stacksGiven() > 0 || result.xpGiven() > 0L) {
                    sendSnapshot(player, menu, true, "console.neroevents.claim.partial",
                            Integer.toString(result.stacksRemaining()));
                } else {
                    sendSnapshot(player, menu, false, ConsoleIntents.Deny.MAKE_ROOM.key(), "");
                }
            }
            default -> sendSnapshot(player, menu, false, ConsoleIntents.Deny.NOTHING_TO_CLAIM.key(), "");
        }
    }

    private static void onPrivacy(ConsolePrivacy payload, ServerPlayer player, EventConsoleMenu menu) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Optional<ConsoleIntents.PrivacyAction> action = ConsoleIntents.PrivacyAction.fromWire(payload.action());
        if (action.isEmpty()) {
            return; // not a button the console has: ignore
        }
        UUID viewer = player.getUUID();
        // The same calls as /neroevents privacy …
        Component answer = switch (action.get()) {
            case EXPORT -> EventsPrivacyActions.export(server, viewer);
            case OPT_OUT -> EventsPrivacyActions.optOut(server, viewer);
            case OPT_IN -> EventsPrivacyActions.optIn(server, viewer);
        };
        String key = answer.getContents() instanceof TranslatableContents t ? t.getKey() : "";
        switch (key) {
            // The command's export line names the file, whose name carries the player's id: never on a screen.
            case "privacy.neroevents.export.done" -> sendSnapshot(player, menu, true,
                    "console.neroevents.privacy.export_done", "");
            case "privacy.neroevents.export.cooldown" -> sendSnapshot(player, menu, false,
                    "console.neroevents.privacy.export_wait", firstArg(answer));
            case "privacy.neroevents.export.failed" -> sendSnapshot(player, menu, false,
                    "console.neroevents.privacy.export_failed", "");
            case "privacy.neroevents.optout.done" -> sendSnapshot(player, menu, true,
                    "console.neroevents.privacy.optout_done", "");
            case "privacy.neroevents.optout.already" -> sendSnapshot(player, menu, true,
                    "console.neroevents.privacy.optout_already", "");
            case "privacy.neroevents.optin.done" -> sendSnapshot(player, menu, true,
                    "console.neroevents.privacy.optin_done", "");
            case "privacy.neroevents.optin.already" -> sendSnapshot(player, menu, true,
                    "console.neroevents.privacy.optin_already", "");
            default -> sendSnapshot(player, menu, false, MESSAGE_FAILED, "");
        }
    }

    private static String firstArg(Component answer) {
        if (answer.getContents() instanceof TranslatableContents t && t.getArgs().length > 0) {
            Object first = t.getArgs()[0];
            return first instanceof Number || first instanceof String ? String.valueOf(first) : "";
        }
        return "";
    }

    private static void onCancel(ConsoleCancel payload, ServerPlayer player, EventConsoleMenu menu) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Optional<ConsoleIntents.Deny> deny = ConsoleIntents.checkOperator(isOperator(player));
        if (deny.isPresent()) {
            sendSnapshot(player, menu, false, deny.get().key(), "");
            return;
        }
        EventEngine engine = EventEngine.get(server);
        EventInstance target = find(engine.live(), payload.key());
        // The same engine path as /neroevents cancel.
        if (target == null || !target.phase().live() || !engine.cancel(target.id(), "admin")) {
            sendSnapshot(player, menu, false, ConsoleIntents.Deny.EVENT_OVER.key(), "");
            return;
        }
        sendSnapshot(player, menu, true, "console.neroevents.cancel.done", "");
    }

    private static boolean isOperator(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }
}
