package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.EventConditions.ConditionResult;
import za.co.neroland.neroevents.event.persist.EventEngineState;
import za.co.neroland.neroevents.event.world.WorldView;

/**
 * The scheduler's pure parts: the per-definition eligibility rules, the weighted roll, the dimension
 * choice and the next-roll clock. Deterministic for a seeded {@link RandomGenerator}: the pool is always
 * walked in id order, so insertion order never matters.
 */
public final class EventScheduler {

    /** Every rule, in the order it is checked; the first failing code is what preview and list show. */
    public static final List<String> RULES = List.of("disabled", "request_only", "payload_missing", "kill_switch",
            "calm_window", "consent_admin", "cooldown", "band_cap", "type_cap", "conditions", "boss_dimension_cap",
            "preflight");

    private EventScheduler() {
    }

    /**
     * One definition's eligibility this pass.
     *
     * @param eligible            whether it may be rolled
     * @param code                the first failing rule ({@code null} when eligible)
     * @param candidateDimensions where it may fire (after the per-dimension boss cap)
     * @param effectiveWeight     the reweight override or the definition's weight
     */
    public record Eligibility(boolean eligible, @Nullable String code, List<Identifier> candidateDimensions,
            int effectiveWeight) {
    }

    /** One rule's verdict, for {@code /neroevents preview}. */
    public record RuleCheck(String rule, boolean passed, @Nullable String detail) {
    }

    public static Eligibility eligibility(EventDefinition def, SchedulerSnapshot snap, EngineSettings settings) {
        int weight = snap.reweights().getOrDefault(def.id(), def.weight());
        ConditionResult conditions = null;
        List<Identifier> candidates = List.of();
        for (String rule : RULES) {
            switch (rule) {
                case "conditions" -> {
                    conditions = def.conditions().evaluate(snap);
                    if (!conditions.eligible()) {
                        return new Eligibility(false, "conditions", List.of(), weight);
                    }
                    candidates = conditions.candidateDimensions();
                }
                case "boss_dimension_cap" -> {
                    candidates = bossFreeDimensions(def, snap, candidates);
                    if (candidates.isEmpty()) {
                        return new Eligibility(false, rule, List.of(), weight);
                    }
                }
                default -> {
                    if (fails(rule, def, snap, settings)) {
                        return new Eligibility(false, rule, List.of(), weight);
                    }
                }
            }
        }
        return new Eligibility(true, null, candidates, weight);
    }

    /** Every rule evaluated on its own (preview: shows all of them, not just the first failure). */
    public static List<RuleCheck> explain(EventDefinition def, SchedulerSnapshot snap, EngineSettings settings) {
        List<RuleCheck> out = new ArrayList<>();
        ConditionResult conditions = def.conditions().evaluate(snap);
        for (String rule : RULES) {
            switch (rule) {
                case "conditions" -> out.add(new RuleCheck(rule, conditions.eligible(), conditions.firstFailure()));
                case "boss_dimension_cap" -> out.add(new RuleCheck(rule,
                        !conditions.eligible() || !bossFreeDimensions(def, snap, conditions.candidateDimensions()).isEmpty(),
                        null));
                case "preflight" -> {
                    Optional<String> reason = def.payloadType() == null ? Optional.of("payload_missing")
                            : def.payload().preflight(snap, def);
                    out.add(new RuleCheck(rule, reason.isEmpty(), reason.orElse(null)));
                }
                default -> out.add(new RuleCheck(rule, !fails(rule, def, snap, settings), null));
            }
        }
        return out;
    }

    private static boolean fails(String rule, EventDefinition def, SchedulerSnapshot snap, EngineSettings s) {
        return switch (rule) {
            case "disabled" -> !def.enabled() || s.eventsDisabled.contains(def.id());
            case "request_only" -> def.weight() == 0;
            case "payload_missing" -> def.payloadType() == null || PayloadTypes.get(def.type()).isEmpty();
            case "kill_switch" -> killSwitch(def, s).isPresent();
            case "calm_window" -> snap.calmWindow() && !def.calmExempt();
            case "consent_admin" -> snap.effectiveConsent() == ConsentMode.ADMIN;
            case "cooldown" -> snap.cooldownUntil().getOrDefault(def.id(), Long.MIN_VALUE) > snap.now();
            case "band_cap" -> bandCapReached(snap, s);
            case "type_cap" -> typeCapReached(def.category(), snap, s);
            case "preflight" -> def.payloadType() == null || def.payload().preflight(snap, def).isPresent();
            default -> false;
        };
    }

    /** The kill switch that removes this definition, if any: absolute for every origin. */
    public static Optional<String> killSwitch(EventDefinition def, EngineSettings s) {
        if (def.destructive() && !s.destructiveEventsEnabled) {
            return Optional.of("destructiveEventsEnabled");
        }
        if (def.category().isCombat() && !s.combatEventsEnabled) {
            return Optional.of("combatEventsEnabled");
        }
        if (def.category() == EventCategory.ECONOMY && !s.economyEventsEnabled) {
            return Optional.of("economyEventsEnabled");
        }
        return Optional.empty();
    }

    /** Whether the band's concurrent cap (or the hard ceiling) is reached. */
    public static boolean bandCapReached(SchedulerSnapshot snap, EngineSettings s) {
        return snap.activeTotal() >= snap.band().concurrentCap() || snap.activeTotal() >= s.maxConcurrentEvents;
    }

    /** Whether the per-type cap for this category is reached (one war at a time, always). */
    public static boolean typeCapReached(EventCategory category, SchedulerSnapshot snap, EngineSettings s) {
        return switch (category) {
            case BOSS -> snap.activeIn(EventCategory.BOSS) >= s.maxConcurrentBossEvents;
            case INVASION -> snap.activeIn(EventCategory.INVASION) >= s.maxConcurrentInvasions;
            case RAID -> snap.activeIn(EventCategory.RAID) >= s.maxConcurrentRaids;
            case WAR -> snap.activeIn(EventCategory.WAR) >= 1;
            default -> false;
        };
    }

    /** For a boss definition: the candidates without a live boss (one boss per dimension). */
    public static List<Identifier> bossFreeDimensions(EventDefinition def, SchedulerSnapshot snap,
            List<Identifier> candidates) {
        if (def.category() != EventCategory.BOSS) {
            return candidates;
        }
        List<Identifier> free = new ArrayList<>();
        for (Identifier dimension : candidates) {
            if (snap.activeBossPerDimension().getOrDefault(dimension, 0) < 1) {
                free.add(dimension);
            }
        }
        return List.copyOf(free);
    }

    /**
     * The weighted roll: walks the ids in sorted order, so the same seed gives the same pick regardless
     * of insertion order. Zero and negative weights never win.
     */
    public static Optional<Identifier> pick(Map<Identifier, Integer> weights, RandomGenerator rng) {
        TreeMap<Identifier, Integer> sorted = new TreeMap<>();
        long total = 0L;
        for (Map.Entry<Identifier, Integer> entry : weights.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                sorted.put(entry.getKey(), entry.getValue());
                total += entry.getValue();
            }
        }
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        long r = rng.nextLong(total);
        for (Map.Entry<Identifier, Integer> entry : sorted.entrySet()) {
            r -= entry.getValue();
            if (r < 0) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.of(sorted.lastKey());
    }

    /**
     * A candidate dimension weighted by its online count (a dimension allowed to be empty weighs 1).
     */
    public static Identifier pickDimension(List<Identifier> candidates, Map<Identifier, Integer> online,
            RandomGenerator rng) {
        Map<Identifier, Integer> weights = new LinkedHashMap<>();
        for (Identifier dimension : candidates) {
            weights.put(dimension, Math.max(1, online.getOrDefault(dimension, 0)));
        }
        return pick(weights, rng).orElse(candidates.get(0));
    }

    /** {@code now + max(globalCooldown, round(base × multiplier × (1 + jitter)))}. */
    public static long nextRollAt(long now, EngineSettings s, PopulationBand band, RandomGenerator rng) {
        double jitter = rng.nextDouble() * 2.0D * s.schedulerJitterFraction - s.schedulerJitterFraction;
        long interval = Math.round(s.schedulerBaseIntervalTicks * band.intervalMultiplier() * (1.0D + jitter));
        return now + Math.max(s.schedulerGlobalCooldownTicks, interval);
    }

    /**
     * Builds one pass's snapshot. Gate holders and tag members are computed only for gates and tags the
     * enabled definitions reference.
     */
    public static SchedulerSnapshot snapshot(WorldView world, long now, EngineSettings s, BandTable bands,
            Collection<EventDefinition> definitions, Collection<EventInstance> live, EventEngineState state) {
        return snapshot(world, now, s, bands, definitions, live, state, Long.MAX_VALUE);
    }

    /** As above, with today's remaining reward budget (value units) for the boss pre-check. */
    public static SchedulerSnapshot snapshot(WorldView world, long now, EngineSettings s, BandTable bands,
            Collection<EventDefinition> definitions, Collection<EventInstance> live, EventEngineState state,
            long remainingDailyBudget) {
        int online = world.onlinePlayers();
        PopulationBand band = bands.bandFor(online);
        boolean dedicated = world.isDedicatedServer();
        ConsentMode consent = ConsentMode.effective(s.consentMode, s.consentVotingEnabled, dedicated, online);
        List<Identifier> dimensions = List.copyOf(world.loadedDimensions());
        Map<Identifier, Long> dayTimes = new HashMap<>();
        for (Identifier dimension : dimensions) {
            dayTimes.put(dimension, world.dayTime(dimension));
        }
        Set<Identifier> gates = new LinkedHashSet<>();
        Set<Identifier> tags = new LinkedHashSet<>();
        Set<String> mods = new LinkedHashSet<>();
        for (EventDefinition def : definitions) {
            def.conditions().requiresGate().ifPresent(gates::add);
            def.conditions().dimensionTag().ifPresent(tags::add);
            mods.addAll(def.conditions().requiresMods());
        }
        Map<Identifier, Integer> holders = new HashMap<>();
        for (Identifier gate : gates) {
            holders.put(gate, world.playersWithGateOpen(gate));
        }
        Map<Identifier, Set<Identifier>> tagMembers = new HashMap<>();
        for (Identifier tag : tags) {
            Set<Identifier> members = new LinkedHashSet<>();
            for (Identifier dimension : dimensions) {
                if (world.dimensionInTag(dimension, tag)) {
                    members.add(dimension);
                }
            }
            tagMembers.put(tag, Set.copyOf(members));
        }
        Set<String> loadedMods = new LinkedHashSet<>();
        for (String mod : mods) {
            if (world.isModLoaded(mod)) {
                loadedMods.add(mod);
            }
        }
        Map<EventCategory, Integer> byCategory = new EnumMap<>(EventCategory.class);
        Map<Identifier, Integer> bossPerDimension = new HashMap<>();
        int active = 0;
        for (EventInstance instance : live) {
            if (!instance.phase().live()) {
                continue;
            }
            active++;
            byCategory.merge(instance.definition().category(), 1, Integer::sum);
            if (instance.definition().category() == EventCategory.BOSS) {
                bossPerDimension.merge(instance.dimension(), 1, Integer::sum);
            }
        }
        return new SchedulerSnapshot(now, online, band, now < s.calmWindowTicks(), consent, dedicated,
                Map.copyOf(world.onlinePlayersPerDimension()), dimensions, Map.copyOf(dayTimes), Map.copyOf(holders),
                Map.copyOf(tagMembers), Set.copyOf(loadedMods), Map.copyOf(byCategory), Map.copyOf(bossPerDimension),
                active, state.lastFiredAt(), state.cooldowns(), state.reweights(), remainingDailyBudget);
    }
}
