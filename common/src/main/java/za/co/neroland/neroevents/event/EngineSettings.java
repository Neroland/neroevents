package za.co.neroland.neroevents.event;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.config.NeroEventsConfig;

/**
 * One frozen copy of every engine config key. Production builds a fresh copy from
 * {@link NeroEventsConfig} once per scheduler pass ({@link #fromConfig()}), so a config reload takes
 * effect at the next pass; tests build {@link #defaults()} and change fields directly, which keeps
 * the engine free of config-class initialisation on a plain JVM.
 *
 * <p>The defaults here equal the config defaults, key for key.
 */
public final class EngineSettings {

    public boolean schedulerEnabled = true;
    public int schedulerIntervalTicks = 600;
    public long schedulerBaseIntervalTicks = 36000L;
    public double schedulerJitterFraction = 0.5D;
    public int schedulerRetryTicks = 6000;
    public long schedulerGlobalCooldownTicks = 6000L;
    public String schedulerBands = BandTable.DEFAULT_SPEC;
    public int schedulerDefinitionsCap = 256;
    public int schedulerPassWarnMicros = 2000;
    public int calmWindowHours = 48;
    public int maxConcurrentEvents = 4;
    public int maxConcurrentBossEvents = 1;
    public int maxConcurrentInvasions = 1;
    public int maxConcurrentRaids = 1;
    public ConsentMode consentMode = ConsentMode.OFF;
    public boolean consentVotingEnabled = true;
    public int voteQuorumPlayers = 2;
    public double voteQuorumFraction = 0.5D;
    public double voteMajorityFraction = 0.5D;
    public int voteMinTicks = 600;
    public boolean voteEarlyCloseEnabled = true;
    public boolean destructiveEventsEnabled = true;
    public boolean combatEventsEnabled = true;
    public boolean economyEventsEnabled = true;
    public Set<Identifier> eventsDisabled = Set.of();
    public String eventExclusionZones = "";
    public int protectSpawnRadiusBlocks = 64;
    public Set<String> protectedBlockNamespaces = Set.of("nerosecurity", "nerocolonies");
    public int protectedNamespaceMarginBlocks = 8;
    public int anchorDensityCellBlocks = 256;
    public int anchorAttemptsPerCell = 16;
    public int anchorMaxCellRolls = 4;
    public int instanceRetainTicks = 1200;
    public int resumableSaveIntervalTicks = 200;

    // Payload keys (read by the payloads through the running engine's snapshot, never by the engine itself).
    public boolean meteorBlockDamage = true;
    public boolean craterAvoidsBlockEntities = true;
    public int craterBlockEntityMarginBlocks = 4;
    public boolean raidBlockDamage = false;
    public int wreckLingerDays = 3;
    public int wreckRecordsMax = 200;
    public double stormDrainFractionPerPass = 0.01D;
    public long stormDrainMaxPerPass = 5000L;
    public double stormDrainFloorFraction = 0.2D;
    public int stormDrainChunkRadius = 3;
    public int stormDrainMaxStoragesPerPass = 64;
    public boolean stormEffectsEnabled = true;
    public int warMinMembersOnline = 2;

    /** Whether the configured consent string was unreadable (logged once per server start). */
    public boolean consentModeInvalid;

    private EngineSettings() {
    }

    /** The shipped defaults. */
    public static EngineSettings defaults() {
        return new EngineSettings();
    }

    /** A snapshot of the live config. Server thread; cheap (a few dozen map reads). */
    public static EngineSettings fromConfig() {
        EngineSettings s = new EngineSettings();
        s.schedulerEnabled = NeroEventsConfig.SCHEDULER_ENABLED.get();
        s.schedulerIntervalTicks = NeroEventsConfig.SCHEDULER_INTERVAL_TICKS.get();
        s.schedulerBaseIntervalTicks = NeroEventsConfig.SCHEDULER_BASE_INTERVAL_TICKS.get();
        s.schedulerJitterFraction = NeroEventsConfig.SCHEDULER_JITTER_FRACTION.get();
        s.schedulerRetryTicks = NeroEventsConfig.SCHEDULER_RETRY_TICKS.get();
        s.schedulerGlobalCooldownTicks = NeroEventsConfig.SCHEDULER_GLOBAL_COOLDOWN_TICKS.get();
        s.schedulerBands = NeroEventsConfig.SCHEDULER_BANDS.get();
        s.schedulerDefinitionsCap = NeroEventsConfig.SCHEDULER_DEFINITIONS_CAP.get();
        s.schedulerPassWarnMicros = NeroEventsConfig.SCHEDULER_PASS_WARN_MICROS.get();
        s.calmWindowHours = NeroEventsConfig.CALM_WINDOW_HOURS.get();
        s.maxConcurrentEvents = NeroEventsConfig.MAX_CONCURRENT_EVENTS.get();
        s.maxConcurrentBossEvents = NeroEventsConfig.MAX_CONCURRENT_BOSS_EVENTS.get();
        s.maxConcurrentInvasions = NeroEventsConfig.MAX_CONCURRENT_INVASIONS.get();
        s.maxConcurrentRaids = NeroEventsConfig.MAX_CONCURRENT_RAIDS.get();
        ConsentMode mode = ConsentMode.parse(NeroEventsConfig.CONSENT_MODE.get());
        s.consentModeInvalid = mode == null;
        s.consentMode = mode == null ? ConsentMode.OFF : mode;
        s.consentVotingEnabled = NeroEventsConfig.CONSENT_VOTING_ENABLED.get();
        s.voteQuorumPlayers = NeroEventsConfig.VOTE_QUORUM_PLAYERS.get();
        s.voteQuorumFraction = NeroEventsConfig.VOTE_QUORUM_FRACTION.get();
        s.voteMajorityFraction = NeroEventsConfig.VOTE_MAJORITY_FRACTION.get();
        s.voteMinTicks = NeroEventsConfig.VOTE_MIN_TICKS.get();
        s.voteEarlyCloseEnabled = NeroEventsConfig.VOTE_EARLY_CLOSE_ENABLED.get();
        s.destructiveEventsEnabled = NeroEventsConfig.DESTRUCTIVE_EVENTS_ENABLED.get();
        s.combatEventsEnabled = NeroEventsConfig.COMBAT_EVENTS_ENABLED.get();
        s.economyEventsEnabled = NeroEventsConfig.ECONOMY_EVENTS_ENABLED.get();
        s.eventsDisabled = parseIds(NeroEventsConfig.EVENTS_DISABLED.get());
        s.eventExclusionZones = NeroEventsConfig.EVENT_EXCLUSION_ZONES.get();
        s.protectSpawnRadiusBlocks = NeroEventsConfig.PROTECT_SPAWN_RADIUS_BLOCKS.get();
        s.protectedBlockNamespaces = parseWords(NeroEventsConfig.PROTECTED_BLOCK_NAMESPACES.get());
        s.protectedNamespaceMarginBlocks = NeroEventsConfig.PROTECTED_NAMESPACE_MARGIN_BLOCKS.get();
        s.anchorDensityCellBlocks = NeroEventsConfig.ANCHOR_DENSITY_CELL_BLOCKS.get();
        s.anchorAttemptsPerCell = NeroEventsConfig.ANCHOR_ATTEMPTS_PER_CELL.get();
        s.anchorMaxCellRolls = NeroEventsConfig.ANCHOR_MAX_CELL_ROLLS.get();
        s.instanceRetainTicks = NeroEventsConfig.INSTANCE_RETAIN_TICKS.get();
        s.resumableSaveIntervalTicks = NeroEventsConfig.RESUMABLE_SAVE_INTERVAL_TICKS.get();
        s.meteorBlockDamage = NeroEventsConfig.METEOR_BLOCK_DAMAGE.get();
        s.craterAvoidsBlockEntities = NeroEventsConfig.CRATER_AVOIDS_BLOCK_ENTITIES.get();
        s.craterBlockEntityMarginBlocks = NeroEventsConfig.CRATER_BLOCK_ENTITY_MARGIN_BLOCKS.get();
        s.raidBlockDamage = NeroEventsConfig.RAID_BLOCK_DAMAGE.get();
        s.wreckLingerDays = NeroEventsConfig.WRECK_LINGER_DAYS.get();
        s.wreckRecordsMax = NeroEventsConfig.WRECK_RECORDS_MAX.get();
        s.stormDrainFractionPerPass = NeroEventsConfig.STORM_DRAIN_FRACTION_PER_PASS.get();
        s.stormDrainMaxPerPass = NeroEventsConfig.STORM_DRAIN_MAX_PER_PASS.get();
        s.stormDrainFloorFraction = NeroEventsConfig.STORM_DRAIN_FLOOR_FRACTION.get();
        s.stormDrainChunkRadius = NeroEventsConfig.STORM_DRAIN_CHUNK_RADIUS.get();
        s.stormDrainMaxStoragesPerPass = NeroEventsConfig.STORM_DRAIN_MAX_STORAGES_PER_PASS.get();
        s.stormEffectsEnabled = NeroEventsConfig.STORM_EFFECTS_ENABLED.get();
        s.warMinMembersOnline = NeroEventsConfig.WAR_MIN_MEMBERS_ONLINE.get();
        return s;
    }

    /** Comma-separated identifiers; malformed entries are skipped. */
    public static Set<Identifier> parseIds(String raw) {
        Set<Identifier> out = new LinkedHashSet<>();
        for (String word : parseWords(raw)) {
            Identifier id = Identifier.tryParse(word);
            if (id != null) {
                out.add(id);
            }
        }
        return Set.copyOf(out);
    }

    /** Comma-separated lower-case words, blanks dropped. */
    public static Set<String> parseWords(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String word = part.trim().toLowerCase(Locale.ROOT);
                if (!word.isEmpty()) {
                    out.add(word);
                }
            }
        }
        return Set.copyOf(out);
    }

    /** The calm window in ticks of overworld game time. */
    public long calmWindowTicks() {
        return calmWindowHours * 72000L;
    }
}
