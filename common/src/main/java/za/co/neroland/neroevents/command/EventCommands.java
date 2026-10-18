package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.CompatRegistry;
import za.co.neroland.neroevents.economy.MarketShockPayload;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EngineMetrics;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventDefinitions;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventRequest;
import za.co.neroland.neroevents.event.EventScheduler;
import za.co.neroland.neroevents.event.EventScheduler.Eligibility;
import za.co.neroland.neroevents.event.EventScheduler.RuleCheck;
import za.co.neroland.neroevents.event.RequestResult;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.ValidationIssue;
import za.co.neroland.neroevents.event.VoteTally;
import za.co.neroland.neroevents.event.anchor.AnchorPicker;
import za.co.neroland.neroevents.history.EventHistoryState;
import za.co.neroland.neroevents.privacy.EventsPrivacy;
import za.co.neroland.neroevents.privacy.EventsPrivacyActions;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.reward.DailyBudget;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardCrates;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.reward.RewardLine;
import za.co.neroland.neroevents.protect.ExclusionZones;
import za.co.neroland.neroevents.protect.ExclusionZones.ExclusionZone;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionSources;
import za.co.neroland.neroevents.storm.ActiveStorms;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The {@code /neroevents} command tree, registered identically from all three loaders and built from
 * {@link #SYNTAX} (one row per command form; the wiki's Commands page lists the same rows).
 *
 * <p>Permissions: {@code list}, {@code status} and {@code vote} are for everyone; everything else needs
 * {@code LEVEL_GAMEMASTERS}. Feedback goes to the invoker only. Suggestions are definition ids and
 * instance short ids, never names. Anchor coordinates appear only in operator output.
 */
public final class EventCommands {

    /** Every command form, exactly as the wiki lists it. {@link #register} is built from this list. */
    public static final List<String> SYNTAX = List.of(
            "/neroevents list",
            "/neroevents status",
            "/neroevents vote yes",
            "/neroevents vote no",
            "/neroevents vote yes <instance>",
            "/neroevents vote no <instance>",
            "/neroevents claim",
            "/neroevents rewards",
            "/neroevents privacy optout",
            "/neroevents privacy optin",
            "/neroevents privacy status",
            "/neroevents privacy export",
            "/neroevents preview <definition>",
            "/neroevents start <definition>",
            "/neroevents start <definition> <dimension>",
            "/neroevents start <definition> <dimension> <anchor>",
            "/neroevents cancel <instance>",
            "/neroevents skip",
            "/neroevents skip <instance>",
            "/neroevents reweight <definition> <weight>",
            "/neroevents reweight <definition> reset",
            "/neroevents zones add <name> <from> <to>",
            "/neroevents zones add <name> <from> <to> <dimension>",
            "/neroevents zones remove <name>",
            "/neroevents zones exclusions",
            "/neroevents budget",
            "/neroevents gallery",
            "/neroevents gallery clear");

    /** First-level literals anyone may use; every other branch is operator-only. */
    public static final Set<String> PLAYER_LITERALS = Set.of("list", "status", "vote", "claim", "rewards", "privacy");

    /** Every {@code <argument>} a row may use. */
    public static final Set<String> ARGUMENTS = Set.of("instance", "definition", "dimension", "anchor", "weight",
            "name", "from", "to");

    private EventCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String row : SYNTAX) {
            Command<CommandSourceStack> exec = executor(row);
            String[] tokens = row.substring("/neroevents ".length()).split(" ");
            ArgumentBuilder<CommandSourceStack, ?> chain = null;
            for (int i = tokens.length - 1; i >= 0; i--) {
                ArgumentBuilder<CommandSourceStack, ?> node = node(tokens[i]);
                if (chain == null) {
                    node.executes(exec);
                } else {
                    node.then(chain);
                }
                if (i == 0 && !PLAYER_LITERALS.contains(tokens[0])) {
                    node.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
                }
                chain = node;
            }
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("neroevents");
            dispatcher.register(root.then(chain));
        }
    }

    private static ArgumentBuilder<CommandSourceStack, ?> node(String token) {
        if (!token.startsWith("<")) {
            return Commands.literal(token);
        }
        String name = token.substring(1, token.length() - 1);
        return switch (name) {
            case "instance" -> Commands.argument(name, InstanceArgument.type()).suggests(InstanceArgument.SUGGEST);
            case "definition" -> Commands.argument(name, DefinitionArgument.type()).suggests(DefinitionArgument.SUGGEST);
            case "dimension" -> Commands.argument(name, DimensionArgument.dimension());
            case "anchor" -> Commands.argument(name, BlockPosArgument.blockPos());
            case "weight" -> Commands.argument(name, IntegerArgumentType.integer(0, 1000));
            case "name" -> Commands.argument(name, StringArgumentType.word());
            case "from", "to" -> Commands.argument(name, ColumnPosArgument.columnPos());
            default -> throw new IllegalStateException("Unknown command argument " + token);
        };
    }

    private static Command<CommandSourceStack> executor(String row) {
        String sub = row.substring("/neroevents ".length());
        return ctx -> runSafely(ctx.getSource(), sub.split(" ")[0], () -> switch (sub) {
            case "list" -> list(ctx);
            case "status" -> status(ctx);
            case "vote yes" -> vote(ctx, true, false);
            case "vote no" -> vote(ctx, false, false);
            case "vote yes <instance>" -> vote(ctx, true, true);
            case "vote no <instance>" -> vote(ctx, false, true);
            case "claim" -> claim(ctx);
            case "rewards" -> rewards(ctx);
            case "privacy optout" -> privacy(ctx, EventsPrivacyActions::optOut);
            case "privacy optin" -> privacy(ctx, EventsPrivacyActions::optIn);
            case "privacy status" -> privacy(ctx, EventsPrivacyActions::status);
            case "privacy export" -> privacy(ctx, EventsPrivacyActions::export);
            case "budget" -> budget(ctx);
            case "preview <definition>" -> preview(ctx);
            case "start <definition>" -> start(ctx, false, false);
            case "start <definition> <dimension>" -> start(ctx, true, false);
            case "start <definition> <dimension> <anchor>" -> start(ctx, true, true);
            case "cancel <instance>" -> cancel(ctx);
            case "skip" -> skipAll(ctx);
            case "skip <instance>" -> skipOne(ctx);
            case "reweight <definition> <weight>" -> reweight(ctx, false);
            case "reweight <definition> reset" -> reweight(ctx, true);
            case "zones add <name> <from> <to>" -> zoneAdd(ctx, false);
            case "zones add <name> <from> <to> <dimension>" -> zoneAdd(ctx, true);
            case "zones remove <name>" -> zoneRemove(ctx);
            case "zones exclusions" -> zoneList(ctx);
            case "gallery" -> GalleryCommands.build(ctx.getSource());
            case "gallery clear" -> GalleryCommands.clear(ctx.getSource());
            default -> throw new IllegalStateException("No executor for " + row);
        });
    }

    // --- list / status ------------------------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EventEngine engine = EventEngine.get(server);
        Map<Identifier, EventDefinition> defs = new TreeMap<>(EventDefinitions.definitionsForServer(server));
        boolean op = isOp(source);
        if (defs.isEmpty()) {
            reply(source, Component.translatable("command.neroevents.list.empty"));
        } else {
            reply(source, Component.translatable("command.neroevents.list.header", defs.size()));
        }
        SchedulerSnapshot snap = op ? engine.snapshot() : null;
        for (EventDefinition def : defs.values()) {
            int weight = engine.state().reweight(def.id()).orElse(def.weight());
            Component enabled = Component.translatable(def.enabled()
                    ? "command.neroevents.list.enabled" : "command.neroevents.list.disabled");
            Component row = Component.translatable("command.neroevents.list.row", def.id().toString(),
                    def.category().serializedName(), def.severity().name(), enabled, weight);
            if (snap != null) {
                Eligibility e = EventScheduler.eligibility(def, snap, engine.settings());
                Component verdict = e.eligible() ? Component.translatable("command.neroevents.list.eligible")
                        : Component.translatable("command.neroevents.list.ineligible", e.code());
                row = Component.translatable("command.neroevents.list.row_op", row, verdict);
            }
            reply(source, row);
        }
        if (op) {
            List<ValidationIssue> issues = EventDefinitions.issuesForServer(server);
            long dropped = issues.stream().filter(ValidationIssue::dropped).count();
            if (!issues.isEmpty()) {
                reply(source, Component.translatable("command.neroevents.list.issues", dropped, issues.size() - dropped));
            }
        }
        return defs.size();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EventEngine engine = EventEngine.get(source.getServer());
        EngineSettings settings = engine.settings();
        long now = engine.now();
        boolean op = isOp(source);
        ConsentMode effective = engine.effectiveConsent();
        reply(source, Component.translatable("command.neroevents.status.consent", settings.consentMode.configName(),
                effective.configName()));
        int online = engine.world().onlinePlayers();
        reply(source, Component.translatable("command.neroevents.status.band",
                engine.bands().bandFor(online).name(), online));
        long calmLeft = settings.calmWindowTicks() - now;
        reply(source, calmLeft > 0
                ? Component.translatable("command.neroevents.status.calm", minutes(calmLeft))
                : Component.translatable("command.neroevents.status.calm_over"));
        List<EventInstance> live = engine.live();
        if (live.isEmpty()) {
            reply(source, Component.translatable("command.neroevents.status.none"));
        }
        for (EventInstance instance : live) {
            Component timer = instance.phase().upcoming()
                    ? Component.translatable("command.neroevents.status.starts_in", minutes(instance.startsAt() - now))
                    : Component.translatable("command.neroevents.status.ends_in", minutes(instance.endsAt() - now));
            reply(source, Component.translatable("command.neroevents.status.row", instance.shortId(),
                    Component.translatable(instance.definition().display().title()), phaseName(instance.phase()), timer));
            if (instance.phase() == EventPhase.VOTING) {
                reply(source, Component.translatable("command.neroevents.status.vote", instance.shortId(),
                        instance.votes().yes(), instance.votes().no()));
            }
        }
        // Storms are weather: everyone may see them (dimension, variant, intensity; no position, no player).
        for (Map.Entry<Identifier, ActiveStorms.StormStatus> storm : new TreeMap<>(ActiveStorms.all()).entrySet()) {
            reply(source, Component.translatable("command.neroevents.status.storm", storm.getKey().toString(),
                    storm.getValue().variant().serializedName(), storm.getValue().intensity()));
        }
        if (!op) {
            return live.size();
        }
        reply(source, Component.translatable("command.neroevents.status.integrations", integrations()));
        if (MarketShockPayload.isPaused()) {
            reply(source, Component.translatable("command.neroevents.status.shocks_paused"));
        }
        reply(source, Component.translatable("command.neroevents.status.next_roll",
                settings.schedulerEnabled ? minutes(engine.state().nextRollAt() - now) : "-"));
        for (Map.Entry<Identifier, Long> cooldown : new TreeMap<>(engine.state().cooldowns()).entrySet()) {
            if (cooldown.getValue() > now) {
                reply(source, Component.translatable("command.neroevents.status.cooldown", cooldown.getKey().toString(),
                        minutes(cooldown.getValue() - now)));
            }
        }
        for (Map.Entry<Identifier, Integer> reweight : new TreeMap<>(engine.state().reweights()).entrySet()) {
            reply(source, Component.translatable("command.neroevents.status.reweight", reweight.getKey().toString(),
                    reweight.getValue()));
        }
        RewardConfig cfg = RewardConfig.fromConfig();
        MinecraftServer server = source.getServer();
        long lastPurge = EventsPrivacy.lastPurgeAt();
        reply(source, Component.translatable("command.neroevents.status.rewards", engine.remainingDailyBudget(),
                cfg.rewardBudgetPerDay, RewardLedgerState.get(server).size(), RewardCrateState.get(server).size(),
                lastPurge == Long.MIN_VALUE ? "-" : minutes(now - lastPurge)));
        EngineMetrics m = engine.metrics();
        reply(source, Component.translatable("command.neroevents.status.metrics", m.lastTickMicros(),
                m.ewmaTickMicros(), m.maxTickMicros(), m.lastPassMicros(), m.maxPassMicros(), m.passes()));
        for (Map.Entry<String, String> health : ProtectionSources.health().entrySet()) {
            if ("ok".equals(health.getValue())) {
                reply(source, Component.translatable("command.neroevents.status.protection_ok", health.getKey()));
            } else {
                reply(source, Component.translatable("command.neroevents.status.protection_failing", health.getKey(),
                        health.getValue()));
            }
        }
        return live.size();
    }

    /** "nerospace ✔ nerocreatures ✘ …" in the façades' fixed order. */
    static String integrations() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : CompatRegistry.availability().entrySet()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(entry.getKey()).append(' ').append(entry.getValue() ? '✔' : '✘');
        }
        return out.toString();
    }

    // --- vote -----------------------------------------------------------------------------------------

    private static int vote(CommandContext<CommandSourceStack> ctx, boolean yes, boolean named) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neroevents.players_only"));
            return 0;
        }
        EventEngine engine = EventEngine.get(source.getServer());
        EventInstance target;
        if (named) {
            Optional<EventInstance> found = InstanceArgument.resolve(ctx, engine);
            if (found.isEmpty()) {
                return 0;
            }
            target = found.get();
        } else {
            List<EventInstance> open = engine.live().stream().filter(i -> i.phase() == EventPhase.VOTING).toList();
            if (open.isEmpty()) {
                source.sendFailure(Component.translatable("command.neroevents.vote.none"));
                return 0;
            }
            if (open.size() > 1) {
                source.sendFailure(Component.translatable("command.neroevents.vote.which"));
                return 0;
            }
            target = open.get(0);
        }
        if (target.phase() != EventPhase.VOTING) {
            source.sendFailure(Component.translatable("command.neroevents.vote.not_voting", target.shortId()));
            return 0;
        }
        VoteTally.CastResult result = engine.vote(target, player.getUUID(), yes);
        String key = switch (result) {
            case RECORDED -> "command.neroevents.vote.recorded";
            case CHANGED -> "command.neroevents.vote.changed";
            case CLOSED -> "command.neroevents.vote.closed";
        };
        if (result == VoteTally.CastResult.CLOSED) {
            source.sendFailure(Component.translatable(key, target.shortId()));
            return 0;
        }
        reply(source, Component.translatable(key, target.shortId(),
                Component.translatable(target.definition().display().title())));
        return 1;
    }

    // --- preview / start --------------------------------------------------------------------------------

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<EventDefinition> found = DefinitionArgument.resolve(ctx);
        if (found.isEmpty()) {
            return 0;
        }
        EventDefinition def = found.get();
        EventEngine engine = EventEngine.get(source.getServer());
        long now = engine.now();
        int weight = engine.state().reweight(def.id()).orElse(def.weight());
        long cooldownLeft = engine.state().cooldownUntil(def.id()) - now;
        reply(source, Component.translatable("command.neroevents.preview.definition", def.id().toString(),
                def.type().toString(), def.category().serializedName(), def.severity().name(), def.weight(), weight));
        reply(source, Component.translatable("command.neroevents.preview.timing",
                cooldownLeft > 0 ? minutes(cooldownLeft) : "ready", minutes(def.announceTicks()),
                minutes(def.durationTicks())));
        reply(source, Component.translatable("command.neroevents.preview.flags", def.destructive(),
                def.consentExempt(), def.calmExempt(), def.locationPublic()));
        SchedulerSnapshot snap = engine.snapshot();
        for (RuleCheck check : EventScheduler.explain(def, snap, engine.settings())) {
            reply(source, Component.translatable(check.passed()
                    ? "command.neroevents.preview.rule_ok" : "command.neroevents.preview.rule_fail",
                    check.rule(), check.detail() == null ? "" : check.detail()));
        }
        Eligibility e = EventScheduler.eligibility(def, snap, engine.settings());
        List<Identifier> candidates = def.conditions().evaluate(snap).candidateDimensions();
        for (Identifier dimension : candidates) {
            reply(source, Component.translatable("command.neroevents.preview.dimension", dimension.toString(),
                    snap.onlinePerDimension().getOrDefault(dimension, 0)));
        }
        if (!candidates.isEmpty()) {
            Identifier pick = EventScheduler.pickDimension(candidates, snap.onlinePerDimension(),
                    new java.util.Random(now));
            reply(source, Component.translatable("command.neroevents.preview.would_pick", pick.toString()));
            previewAnchor(source, engine, def, pick, now);
        }
        boolean votes = !def.consentExempt() && snap.effectiveConsent() == ConsentMode.VOTE;
        if (votes) {
            int quorum = Math.max(engine.settings().voteQuorumPlayers,
                    (int) Math.ceil(engine.settings().voteQuorumFraction * snap.online()));
            reply(source, Component.translatable("command.neroevents.preview.consent_vote", quorum));
        } else {
            reply(source, Component.translatable(def.consentExempt()
                    ? "command.neroevents.preview.consent_exempt" : "command.neroevents.preview.consent_off"));
        }
        if (votes && def.announceTicks() < engine.settings().voteMinTicks) {
            reply(source, Component.translatable("command.neroevents.preview.vote_too_short",
                    def.announceTicks(), engine.settings().voteMinTicks));
        }
        previewRewards(source, engine, def);
        for (String warning : def.loadWarnings()) {
            reply(source, Component.translatable("command.neroevents.preview.warning", warning));
        }
        return e.eligible() ? 1 : 0;
    }

    private static void previewRewards(CommandSourceStack source, EventEngine engine, EventDefinition def) {
        RewardConfig cfg = RewardConfig.fromConfig();
        List<RewardLine> lines = new ArrayList<>();
        def.rewards().lines().forEach(l -> lines.add(RewardLine.of(l)));
        def.rewards().topBonus().forEach(l -> lines.add(RewardLine.of(l)));
        String pays = def.rewards().payOnExpiry() ? "success, ended, expired" : "success, ended";
        reply(source, Component.translatable("command.neroevents.preview.rewards", def.rewards().budget(),
                cfg.values().total(lines), engine.remainingDailyBudget(), pays));
    }

    // --- claim / rewards / privacy / budget ------------------------------------------------------------

    private static int claim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neroevents.players_only"));
            return 0;
        }
        EventEngine engine = EventEngine.get(source.getServer());
        RewardCrates crates = engine.crates().orElse(null);
        if (crates == null) {
            reply(source, Component.translatable("command.neroevents.claim.empty"));
            return 0;
        }
        RewardCrates.ClaimResult result = crates.claim(player);
        switch (result.status()) {
            case CLAIMED -> reply(source, Component.translatable("command.neroevents.claim.claimed",
                    result.stacksGiven(), result.xpGiven()));
            case PARTIAL -> reply(source, Component.translatable("command.neroevents.claim.partial",
                    result.stacksGiven(), result.stacksRemaining()));
            default -> reply(source, Component.translatable("command.neroevents.claim.empty"));
        }
        return result.stacksGiven();
    }

    private static int rewards(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neroevents.players_only"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        RewardConfig cfg = RewardConfig.fromConfig();
        EventEngine engine = EventEngine.get(server);
        int waiting = engine.crates().map(c -> c.waitingStacks(player.getUUID())).orElse(0);
        if (!cfg.rewardLedgerEnabled) {
            reply(source, Component.translatable("command.neroevents.rewards.disabled"));
        } else if (PrivacyOptOuts.get(server).isOptedOut(player.getUUID())) {
            reply(source, Component.translatable("command.neroevents.rewards.opted_out"));
        } else {
            List<RewardLedgerEntry> rows = RewardLedgerState.get(server).rowsFor(player.getUUID());
            if (rows.isEmpty()) {
                reply(source, Component.translatable("command.neroevents.rewards.none", cfg.ledgerDays()));
            } else {
                reply(source, Component.translatable("command.neroevents.rewards.header", cfg.ledgerDays()));
                long nowMs = System.currentTimeMillis();
                Map<Identifier, EventDefinition> defs = EventDefinitions.definitionsForServer(server);
                for (int i = rows.size() - 1, shown = 0; i >= 0 && shown < 10; i--, shown++) {
                    RewardLedgerEntry row = rows.get(i);
                    EventDefinition def = defs.get(row.definitionId());
                    Component title = def != null ? Component.translatable(def.display().title())
                            : Component.literal(row.definitionId().toString());
                    long hours = Math.max(0L, (nowMs - row.at()) / 3_600_000L);
                    reply(source, Component.translatable("command.neroevents.rewards.row", title, hours,
                            row.summary().currencyPaid(), row.summary().xpGranted(), row.summary().itemCount()));
                }
                reply(source, Component.translatable("reward.neroevents.summary_offline_note"));
            }
        }
        reply(source, Component.translatable("command.neroevents.rewards.crate", waiting));
        return waiting;
    }

    private interface PrivacyAction {
        Component run(MinecraftServer server, java.util.UUID player);
    }

    private static int privacy(CommandContext<CommandSourceStack> ctx, PrivacyAction action) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neroevents.players_only"));
            return 0;
        }
        reply(source, action.run(source.getServer(), player.getUUID()));
        return 1;
    }

    private static int budget(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        RewardConfig cfg = RewardConfig.fromConfig();
        long nowMs = System.currentTimeMillis();
        EventHistoryState history = EventHistoryState.get(server);
        DailyBudget today = history.budget(nowMs);
        long paidToday = history.latest(cfg.historyMaxEntries).stream()
                .filter(e -> DailyBudget.epochDay(e.endedAt()) == today.epochDay() && e.rewardsTotalValue() > 0L)
                .count();
        reply(source, Component.translatable("command.neroevents.budget",
                java.time.LocalDate.ofEpochDay(today.epochDay()).toString(), cfg.rewardBudgetPerDay, today.spent(),
                today.remaining(cfg.rewardBudgetPerDay), paidToday));
        return 1;
    }

    private static void previewAnchor(CommandSourceStack source, EventEngine engine, EventDefinition def,
            Identifier dimension, long now) {
        switch (def.bounds().kind()) {
            case DIMENSION -> reply(source, Component.translatable("command.neroevents.preview.anchor_none"));
            case TARGET -> {
                // Mirrors the real path: the payload's target, protection-checked; the density pick only
                // when the definition allows the fallback.
                EventEngine.TargetPreview target = engine.previewTarget(def, dimension, now);
                if (target.target().isPresent() && target.verdict() != null && target.verdict().allowed()) {
                    BlockPos anchor = target.target().get();
                    Footprint fp = def.payload().footprint(def, def.payloadConfig(), dimension, anchor);
                    reply(source, Component.translatable("command.neroevents.preview.anchor", anchor.getX(),
                            anchor.getY(), anchor.getZ(), fp.minX() + "," + fp.minZ() + " to " + fp.maxX()
                                    + "," + fp.maxZ()));
                    return;
                }
                if (target.verdict() != null && !target.verdict().allowed()) {
                    reply(source, Component.translatable("command.neroevents.preview.anchor_denied",
                            target.verdict().deniedBy() == null ? "-" : target.verdict().deniedBy(), ""));
                }
                if (def.bounds().fallbackToDensity()) {
                    previewDensity(source, engine, def, dimension, now);
                } else if (target.target().isEmpty()) {
                    reply(source, Component.translatable("command.neroevents.preview.anchor_denied", "-",
                            "no_target"));
                }
            }
            default -> previewDensity(source, engine, def, dimension, now);
        }
    }

    private static void previewDensity(CommandSourceStack source, EventEngine engine, EventDefinition def,
            Identifier dimension, long now) {
        EngineSettings s = engine.settings();
        AnchorPicker.Result result = AnchorPicker.pick(engine.world(), engine.protection(), dimension,
                a -> def.payload().footprint(def, def.payloadConfig(), dimension, a), now,
                s.anchorDensityCellBlocks, s.anchorAttemptsPerCell, s.anchorMaxCellRolls);
        if (result instanceof AnchorPicker.Found f) {
            Footprint fp = def.payload().footprint(def, def.payloadConfig(), dimension, f.anchor());
            reply(source, Component.translatable("command.neroevents.preview.anchor", f.anchor().getX(),
                    f.anchor().getY(), f.anchor().getZ(), fp.minX() + "," + fp.minZ() + " to " + fp.maxX()
                            + "," + fp.maxZ()));
        } else if (result instanceof AnchorPicker.Empty empty) {
            reply(source, Component.translatable("command.neroevents.preview.anchor_denied",
                    empty.deniedBy() == null ? "-" : empty.deniedBy(),
                    empty.detail() == null ? "" : empty.detail()));
        }
    }

    private static int start(CommandContext<CommandSourceStack> ctx, boolean hasDimension, boolean hasAnchor)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Optional<EventDefinition> found = DefinitionArgument.resolve(ctx);
        if (found.isEmpty()) {
            return 0;
        }
        Identifier dimension;
        if (hasDimension) {
            ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
            dimension = level.dimension().identifier();
        } else {
            dimension = source.getEntity() != null ? source.getLevel().dimension().identifier()
                    : Level.OVERWORLD.identifier();
        }
        BlockPos anchor = hasAnchor ? BlockPosArgument.getBlockPos(ctx, "anchor") : null;
        EventEngine engine = EventEngine.get(source.getServer());
        RequestResult result = engine.request(new EventRequest(found.get().id(), dimension, EventOrigin.ADMIN, anchor));
        if (result instanceof RequestResult.Queued queued) {
            reply(source, Component.translatable("command.neroevents.start.queued", found.get().id().toString(),
                    queued.instance().shortId(), dimension.toString()));
            return 1;
        }
        RequestResult.Refused refused = (RequestResult.Refused) result;
        source.sendFailure(Component.translatable("command.neroevents.start.refused", found.get().id().toString(),
                refused.code(), refused.message() == null ? "" : " (" + refused.message() + ")"));
        return 0;
    }

    // --- cancel / skip / reweight -------------------------------------------------------------------------

    private static int cancel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EventEngine engine = EventEngine.get(source.getServer());
        Optional<EventInstance> found = InstanceArgument.resolve(ctx, engine);
        if (found.isEmpty()) {
            return 0;
        }
        EventInstance instance = found.get();
        EventPhase was = instance.phase();
        if (!was.live() || !engine.cancel(instance.id(), "admin")) {
            source.sendFailure(Component.translatable("command.neroevents.cancel.not_live", instance.shortId()));
            return 0;
        }
        reply(source, Component.translatable("command.neroevents.cancel.done", instance.shortId(), phaseName(was)));
        return 1;
    }

    private static int skipAll(CommandContext<CommandSourceStack> ctx) {
        int count = EventEngine.get(ctx.getSource().getServer()).skipUpcoming("admin");
        reply(ctx.getSource(), Component.translatable("command.neroevents.skip.count", count));
        return count;
    }

    private static int skipOne(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EventEngine engine = EventEngine.get(source.getServer());
        Optional<EventInstance> found = InstanceArgument.resolve(ctx, engine);
        if (found.isEmpty()) {
            return 0;
        }
        EventInstance instance = found.get();
        if (!instance.phase().upcoming()) {
            source.sendFailure(Component.translatable(instance.phase().live()
                    ? "command.neroevents.skip.active" : "command.neroevents.cancel.not_live", instance.shortId()));
            return 0;
        }
        engine.cancel(instance.id(), "admin");
        reply(source, Component.translatable("command.neroevents.skip.one", instance.shortId()));
        return 1;
    }

    private static int reweight(CommandContext<CommandSourceStack> ctx, boolean reset) {
        CommandSourceStack source = ctx.getSource();
        Optional<EventDefinition> found = DefinitionArgument.resolve(ctx);
        if (found.isEmpty()) {
            return 0;
        }
        EventEngine engine = EventEngine.get(source.getServer());
        Identifier id = found.get().id();
        if (reset) {
            boolean removed = engine.state().clearReweight(id);
            reply(source, Component.translatable(removed ? "command.neroevents.reweight.reset"
                    : "command.neroevents.reweight.none", id.toString()));
            return removed ? 1 : 0;
        }
        int weight = IntegerArgumentType.getInteger(ctx, "weight");
        engine.state().setReweight(id, weight);
        reply(source, Component.translatable("command.neroevents.reweight.set", id.toString(), weight));
        return 1;
    }

    // --- zones ------------------------------------------------------------------------------------------------

    private static int zoneAdd(CommandContext<CommandSourceStack> ctx, boolean hasDimension)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        if (!ExclusionZones.NAME.matcher(name).matches()) {
            source.sendFailure(Component.translatable("command.neroevents.zones.bad_name"));
            return 0;
        }
        ColumnPos from = ColumnPosArgument.getColumnPos(ctx, "from");
        ColumnPos to = ColumnPosArgument.getColumnPos(ctx, "to");
        Identifier dimension = hasDimension ? DimensionArgument.getDimension(ctx, "dimension").dimension().identifier()
                : source.getLevel().dimension().identifier();
        EventEngine engine = EventEngine.get(source.getServer());
        boolean clash = engine.state().zone(name).isPresent()
                || ExclusionZones.configZones(engine.settings().eventExclusionZones).stream()
                        .anyMatch(z -> z.name().equals(name));
        if (clash || !engine.state().addZone(ExclusionZone.of(name, dimension, from.x(), from.z(), to.x(), to.z()))) {
            source.sendFailure(Component.translatable("command.neroevents.zones.exists", name));
            return 0;
        }
        reply(source, Component.translatable("command.neroevents.zones.added", name, dimension.toString()));
        return 1;
    }

    private static int zoneRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        EventEngine engine = EventEngine.get(source.getServer());
        if (!engine.state().removeZone(name)) {
            source.sendFailure(Component.translatable("command.neroevents.zones.unknown", name));
            return 0;
        }
        reply(source, Component.translatable("command.neroevents.zones.removed", name));
        return 1;
    }

    private static int zoneList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EventEngine engine = EventEngine.get(source.getServer());
        Map<ExclusionZone, Boolean> zones = new LinkedHashMap<>();
        engine.state().zones().forEach(z -> zones.put(z, false));
        ExclusionZones.configZones(engine.settings().eventExclusionZones).forEach(z -> zones.put(z, true));
        if (zones.isEmpty()) {
            reply(source, Component.translatable("command.neroevents.zones.none"));
            return 0;
        }
        for (Map.Entry<ExclusionZone, Boolean> entry : zones.entrySet()) {
            ExclusionZone z = entry.getKey();
            reply(source, Component.translatable("command.neroevents.zones.row", z.name(), z.dimension().toString(),
                    z.minX(), z.minZ(), z.maxX(), z.maxZ(), entry.getValue()
                            ? Component.translatable("command.neroevents.zones.config") : Component.empty()));
        }
        return zones.size();
    }

    // --- plumbing ---------------------------------------------------------------------------------------------

    private static boolean isOp(CommandSourceStack source) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private static Component phaseName(EventPhase phase) {
        return Component.translatable("phase.neroevents." + phase.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String minutes(long ticks) {
        long minutes = Math.max(0L, (ticks + 1199L) / 1200L);
        return Long.toString(minutes);
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private interface Body {
        int run() throws CommandSyntaxException;
    }

    private static int runSafely(CommandSourceStack source, String subcommand, Body body) throws CommandSyntaxException {
        try {
            return body.run();
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "command", "/neroevents " + subcommand);
            NeroEventsCommon.LOGGER.error("[NeroEvents] /neroevents {} failed ({})", subcommand, e.getClass().getName());
            source.sendFailure(Component.translatable("command.neroevents.failed", subcommand));
            return 0;
        }
    }

    /** The syntax rows' tokens (for the syntax test). */
    static List<String[]> tokens() {
        List<String[]> out = new ArrayList<>();
        for (String row : SYNTAX) {
            out.add(row.substring("/neroevents ".length()).split(" "));
        }
        return out;
    }
}
