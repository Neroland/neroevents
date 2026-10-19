package za.co.neroland.neroevents.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * NeroEvents config schema, built on Neroland Core's config framework (file
 * {@code config/neroevents.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroEventsCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Core has no sections, so keys use prefixes ({@code scheduler...}, {@code vote...},
 * {@code anchor...}). Every gameplay key is server-authoritative; the event engine reads them
 * through {@link za.co.neroland.neroevents.event.EngineSettings#fromConfig()} once per scheduler
 * pass, so a config reload takes effect at the next pass.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroEventsConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroEventsCommon.MOD_ID, "NeroEvents configuration.");

    // --- Crash telemetry (client-local opt-out) -----------------------------

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroEvents-only crash reports (Sentry, EU servers) - ON by default. Sends the "
                    + "stack trace, mod/MC/loader/OS/Java versions and your other installed mods' ids "
                    + "and versions; never your IP, username, UUID, world data or chat; file paths are "
                    + "scrubbed of your account name. Set false to opt out of all of it (takes effect "
                    + "on restart). See PRIVACY.md");

    // --- Scheduler ------------------------------------------------------------

    public static final ConfigValue<Boolean> SCHEDULER_ENABLED = SCHEMA.bool(
            "schedulerEnabled", true, true,
            "roll events automatically; false stops automatic events but /neroevents start still works");
    public static final ConfigValue<Integer> SCHEDULER_INTERVAL_TICKS = SCHEMA.intRange(
            "schedulerIntervalTicks", 600, 100, 6000, true,
            "how often (ticks) the scheduler checks whether an event is due");
    public static final ConfigValue<Long> SCHEDULER_BASE_INTERVAL_TICKS = SCHEMA.longRange(
            "schedulerBaseIntervalTicks", 36000L, 1200L, 1728000L, true,
            "average ticks between automatic events at a population multiplier of 1.0 (36000 = 30 minutes)");
    public static final ConfigValue<Double> SCHEDULER_JITTER_FRACTION = SCHEMA.doubleRange(
            "schedulerJitterFraction", 0.5D, 0.0D, 0.9D, true,
            "random spread applied to the time until the next automatic event (0.5 = plus or minus 50%)");
    public static final ConfigValue<Integer> SCHEDULER_RETRY_TICKS = SCHEMA.intRange(
            "schedulerRetryTicks", 6000, 200, 72000, true,
            "ticks until the next try when no event was eligible");
    public static final ConfigValue<Long> SCHEDULER_GLOBAL_COOLDOWN_TICKS = SCHEMA.longRange(
            "schedulerGlobalCooldownTicks", 6000L, 0L, 172800L, true,
            "minimum ticks between two automatic events");
    public static final ConfigValue<String> SCHEDULER_BANDS = SCHEMA.string(
            "schedulerBands", "0-1:2.0:1;2-4:1.0:1;5-9:0.8:2;10+:0.6:3", true,
            "population bands as name:intervalMultiplier:concurrentCap separated by ';' (names a-b or a+, "
                    + "contiguous from 0); an invalid table falls back to the default");
    public static final ConfigValue<Integer> SCHEDULER_DEFINITIONS_CAP = SCHEMA.intRange(
            "schedulerDefinitionsCap", 256, 16, 1024, true,
            "most event definitions loaded from datapacks; extras (by id) are skipped with a warning");
    public static final ConfigValue<Integer> SCHEDULER_PASS_WARN_MICROS = SCHEMA.intRange(
            "schedulerPassWarnMicros", 2000, 100, 100000, true,
            "a scheduler pass slower than this (microseconds) logs one debug line per server start");
    public static final ConfigValue<Integer> CALM_WINDOW_HOURS = SCHEMA.intRange(
            "calmWindowHours", 48, 0, 720, true,
            "hours of server uptime (world time) before automatic events other than calm-exempt ones start");
    public static final ConfigValue<Integer> MAX_CONCURRENT_EVENTS = SCHEMA.intRange(
            "maxConcurrentEvents", 4, 1, 8, true,
            "hard ceiling on events running at once, above every population band's cap");
    public static final ConfigValue<Integer> MAX_CONCURRENT_BOSS_EVENTS = SCHEMA.intRange(
            "maxConcurrentBossEvents", 1, 0, 8, true,
            "most world-boss events at once (never more than one per dimension)");
    public static final ConfigValue<Integer> MAX_CONCURRENT_INVASIONS = SCHEMA.intRange(
            "maxConcurrentInvasions", 1, 0, 8, true,
            "most invasion events at once");
    public static final ConfigValue<Integer> MAX_CONCURRENT_RAIDS = SCHEMA.intRange(
            "maxConcurrentRaids", 1, 0, 8, true,
            "most raid events at once");

    // --- Single-player -----------------------------------------------------------

    public static final ConfigValue<Boolean> SINGLE_PLAYER_PACING = SCHEMA.bool(
            "singlePlayerPacing", true, true,
            "use the singlePlayer* keys below when one player is alone on an integrated (non-dedicated) server; "
                    + "false keeps dedicated-server pacing in single-player too");
    public static final ConfigValue<Integer> SINGLE_PLAYER_FIRST_EVENT_MINUTES = SCHEMA.intRange(
            "singlePlayerFirstEventMinutes", 8, 0, 720, true,
            "minutes of play (world time) in a single-player world before the first automatic event is rolled; "
                    + "0 = no wait");
    public static final ConfigValue<Integer> SINGLE_PLAYER_CALM_WINDOW_MINUTES = SCHEMA.intRange(
            "singlePlayerCalmWindowMinutes", 30, 0, 1440, true,
            "minutes of play (world time) in a single-player world before automatic events other than "
                    + "calm-exempt ones start; replaces calmWindowHours while alone");
    public static final ConfigValue<Double> SINGLE_PLAYER_INTERVAL_MULTIPLIER = SCHEMA.doubleRange(
            "singlePlayerIntervalMultiplier", 1.0D, 0.1D, 10.0D, true,
            "multiplies schedulerBaseIntervalTicks while alone in a single-player world, in place of the "
                    + "population band's multiplier (1.0 = about 30 minutes between events)");
    public static final ConfigValue<Double> SINGLE_PLAYER_COOLDOWN_MULTIPLIER = SCHEMA.doubleRange(
            "singlePlayerCooldownMultiplier", 0.5D, 0.1D, 1.0D, true,
            "multiplies every event definition's cooldown when it fires while alone in a single-player world");

    // --- Consent ---------------------------------------------------------------

    public static final ConfigValue<String> CONSENT_MODE = SCHEMA.string(
            "consentMode", "off", true,
            "who decides whether an automatic event happens: off (it just happens), vote (online players "
                    + "vote during the countdown) or admin (only /neroevents start fires events); a "
                    + "single-player world is always off");
    public static final ConfigValue<Boolean> CONSENT_VOTING_ENABLED = SCHEMA.bool(
            "consentVotingEnabled", true, true,
            "master switch for voting; false makes consentMode=vote behave like off");
    public static final ConfigValue<Integer> VOTE_QUORUM_PLAYERS = SCHEMA.intRange(
            "voteQuorumPlayers", 2, 1, 100, true,
            "minimum number of votes for a vote to count");
    public static final ConfigValue<Double> VOTE_QUORUM_FRACTION = SCHEMA.doubleRange(
            "voteQuorumFraction", 0.5D, 0.0D, 1.0D, true,
            "minimum share of online players that must vote (the larger of this and voteQuorumPlayers applies)");
    public static final ConfigValue<Double> VOTE_MAJORITY_FRACTION = SCHEMA.doubleRange(
            "voteMajorityFraction", 0.5D, 0.5D, 1.0D, true,
            "an event goes ahead when yes votes are more than this share of votes cast (a tie fails)");
    public static final ConfigValue<Integer> VOTE_MIN_TICKS = SCHEMA.intRange(
            "voteMinTicks", 600, 100, 6000, true,
            "after a restart, a vote with less countdown than this left is skipped instead of resumed");
    public static final ConfigValue<Boolean> VOTE_EARLY_CLOSE_ENABLED = SCHEMA.bool(
            "voteEarlyCloseEnabled", true, true,
            "end voting as soon as every online player has voted (the event still starts when the countdown ends)");

    // --- Kill switches -----------------------------------------------------------

    public static final ConfigValue<Boolean> DESTRUCTIVE_EVENTS_ENABLED = SCHEMA.bool(
            "destructiveEventsEnabled", true, true,
            "allow events that can change blocks; false removes every destructive event from the pool and "
                    + "refuses /neroevents start for them. Destructive events keep clear of Neroland security "
                    + "and colony blocks and of claims from supported claim mods. They cannot recognise an "
                    + "unclaimed build made of ordinary blocks - use eventExclusionZones or leave block damage off");
    public static final ConfigValue<Boolean> COMBAT_EVENTS_ENABLED = SCHEMA.bool(
            "combatEventsEnabled", true, true,
            "allow combat, raid, invasion, boss and war events");
    public static final ConfigValue<Boolean> ECONOMY_EVENTS_ENABLED = SCHEMA.bool(
            "economyEventsEnabled", true, true,
            "allow economy events");
    public static final ConfigValue<Boolean> PUSH_NOTIFICATIONS_ENABLED = SCHEMA.bool(
            "pushNotificationsEnabled", true, true,
            "allow companion-app notifications and stored alerts about events; false sends none to anyone (live event "
                    + "rows in the app still update). Has no effect until a NeroLink module is present");
    public static boolean isPushNotificationsEnabled() { return PUSH_NOTIFICATIONS_ENABLED.get(); }
    public static final ConfigValue<String> EVENTS_DISABLED = SCHEMA.string(
            "eventsDisabled", "", true,
            "comma-separated event definition ids that never fire automatically");

    // --- Protection ---------------------------------------------------------------

    public static final ConfigValue<String> EVENT_EXCLUSION_ZONES = SCHEMA.string(
            "eventExclusionZones", "", true,
            "areas events never touch, as name@dimension:x1,z1,x2,z2 separated by ';' "
                    + "(for example base@minecraft:overworld:-100,-100,100,100)");
    public static final ConfigValue<Integer> PROTECT_SPAWN_RADIUS_BLOCKS = SCHEMA.intRange(
            "protectSpawnRadiusBlocks", 64, 0, 512, true,
            "events keep this many blocks away from world spawn (the server's own spawn protection applies "
                    + "when it is larger)");
    public static final ConfigValue<String> PROTECTED_BLOCK_NAMESPACES = SCHEMA.string(
            "protectedBlockNamespaces", "nerosecurity,nerocolonies", true,
            "comma-separated mod ids whose block entities keep destructive events away (loaded chunks only; "
                    + "an unloaded chunk in the way blocks the effect)");
    public static final ConfigValue<Integer> PROTECTED_NAMESPACE_MARGIN_BLOCKS = SCHEMA.intRange(
            "protectedNamespaceMarginBlocks", 8, 0, 64, true,
            "extra blocks around an effect that are checked for protected block entities");

    // --- Anchors and instances -----------------------------------------------------

    public static final ConfigValue<Integer> ANCHOR_DENSITY_CELL_BLOCKS = SCHEMA.intRange(
            "anchorDensityCellBlocks", 256, 64, 1024, true,
            "size of the square areas used to place events where players are (counts only, never positions)");
    public static final ConfigValue<Integer> ANCHOR_ATTEMPTS_PER_CELL = SCHEMA.intRange(
            "anchorAttemptsPerCell", 16, 1, 64, true,
            "random spots tried inside a chosen area before trying another area");
    public static final ConfigValue<Integer> ANCHOR_MAX_CELL_ROLLS = SCHEMA.intRange(
            "anchorMaxCellRolls", 4, 1, 16, true,
            "areas tried before an event is skipped for lack of a place");
    public static final ConfigValue<Integer> ANCHOR_MIN_PLAYER_DISTANCE_BLOCKS = SCHEMA.intRange(
            "anchorMinPlayerDistanceBlocks", 24, 0, 128, true,
            "events never anchor within this many blocks of a player (0 = off); counts only, never positions");
    public static final ConfigValue<Integer> INSTANCE_RETAIN_TICKS = SCHEMA.intRange(
            "instanceRetainTicks", 1200, 0, 72000, true,
            "ticks a finished event stays visible in /neroevents status");
    public static final ConfigValue<Integer> RESUMABLE_SAVE_INTERVAL_TICKS = SCHEMA.intRange(
            "resumableSaveIntervalTicks", 200, 20, 6000, true,
            "how often (ticks) running events save their resumable state");

    // --- Participation ------------------------------------------------------------------

    public static final ConfigValue<Double> CONTRIBUTION_WEIGHT_DAMAGE = SCHEMA.doubleRange(
            "contributionWeightDamage", 1.0D, 0.0D, 100.0D, true,
            "contribution per point of damage (half a heart) dealt to an event's creatures");
    public static final ConfigValue<Double> CONTRIBUTION_WEIGHT_LOOT = SCHEMA.doubleRange(
            "contributionWeightLoot", 25.0D, 0.0D, 1000.0D, true,
            "contribution per event container, pocket or pod opened (each counts once per player)");
    public static final ConfigValue<Double> CONTRIBUTION_WEIGHT_OBJECTIVE = SCHEMA.doubleRange(
            "contributionWeightObjective", 50.0D, 0.0D, 1000.0D, true,
            "contribution per objective captured");
    public static final ConfigValue<Double> CONTRIBUTION_WEIGHT_SURVIVAL = SCHEMA.doubleRange(
            "contributionWeightSurvival", 10.0D, 0.0D, 1000.0D, true,
            "contribution per wave survived inside the event area (only after taking part in another way)");
    public static final ConfigValue<Integer> CONTRIBUTION_INACTIVITY_TICKS = SCHEMA.intRange(
            "contributionInactivityTicks", 6000, 200, 72000, true,
            "a player outside the event area or offline this long loses part of their contribution per further "
                    + "period of this length");
    public static final ConfigValue<Double> CONTRIBUTION_DECAY_FACTOR = SCHEMA.doubleRange(
            "contributionDecayFactor", 0.5D, 0.1D, 0.95D, true,
            "what is kept of an absent player's contribution per period (0.5 = half)");
    public static final ConfigValue<Double> CONTRIBUTION_MIN_WEIGHT = SCHEMA.doubleRange(
            "contributionMinWeight", 0.05D, 0.001D, 100.0D, true,
            "a contribution that decays below this is forgotten");
    public static final ConfigValue<Double> CONTRIBUTION_CREDIT_FLOOR = SCHEMA.doubleRange(
            "contributionCreditFloor", 1.0D, 0.0D, 100.0D, true,
            "the least contribution that counts as taking part (one stray hit does not)");
    public static final ConfigValue<Integer> CONTRIBUTION_SWEEP_INTERVAL_TICKS = SCHEMA.intRange(
            "contributionSweepIntervalTicks", 100, 20, 1200, true,
            "how often (ticks) absent players' contributions are checked");

    // --- Rewards ---------------------------------------------------------------------------

    public static final ConfigValue<Long> REWARD_BUDGET_PER_DAY = SCHEMA.longRange(
            "rewardBudgetPerDay", 6000L, 0L, 10000000L, true,
            "!!! 0 MEANS NO EVENT REWARDS AT ALL !!! the most value (credits, XP and items in value units) all "
                    + "events together pay out per day (UTC calendar day of the server clock)");
    public static final ConfigValue<Double> REWARD_XP_VALUE_PER_POINT = SCHEMA.doubleRange(
            "rewardXpValuePerPoint", 0.25D, 0.0D, 10.0D, true,
            "value units one experience point counts as against the budgets");
    public static final ConfigValue<Long> REWARD_ITEM_DEFAULT_VALUE = SCHEMA.longRange(
            "rewardItemDefaultValue", 10L, 1L, 100000L, true,
            "value units one reward item counts as when its definition gives no value");
    public static final ConfigValue<Double> REWARD_FLOOR_ELIGIBLE_RAW_SHARE = SCHEMA.doubleRange(
            "rewardFloorEligibleRawShare", 0.01D, 0.0D, 0.5D, true,
            "the share of an event's contribution a player needs before the minimum reward share applies to them");
    public static final ConfigValue<Integer> REWARD_REPUTATION_MAX_DELTA_PER_EVENT = SCHEMA.intRange(
            "rewardReputationMaxDeltaPerEvent", 25, 1, 1000, true,
            "the largest reputation change one event can give (needs NeroFactions)");
    public static final ConfigValue<Integer> REWARD_CRATE_MAX_STACKS_PER_PLAYER = SCHEMA.intRange(
            "rewardCrateMaxStacksPerPlayer", 27, 1, 108, true,
            "most unclaimed reward stacks a player can have waiting; rewards beyond it are lost");
    public static final ConfigValue<Boolean> REWARD_LEDGER_ENABLED = SCHEMA.bool(
            "rewardLedgerEnabled", true, true,
            "keep the short reward history per player (paid-once protection across restarts and /neroevents "
                    + "rewards); false keeps none and protects only until the next restart");
    public static final ConfigValue<Integer> REWARD_LEDGER_MAX_ENTRIES = SCHEMA.intRange(
            "rewardLedgerMaxEntries", 5000, 100, 100000, true,
            "most reward history rows kept for the whole server (oldest go first)");
    public static final ConfigValue<Long> BOSS_CREATURES_REWARD_VALUE_PER_PARTICIPANT = SCHEMA.longRange(
            "bossCreaturesRewardValuePerParticipant", 150L, 0L, 100000L, true,
            "value units a world boss's own drops count as per participant against the daily budget");
    public static final ConfigValue<Double> BOSS_CONTRIBUTOR_MIN_SHARE = SCHEMA.doubleRange(
            "bossContributorMinShare", 0.05D, 0.0D, 0.5D, true,
            "the share of a boss fight a player needs for the boss contributor record");
    public static final ConfigValue<String> SUPPLY_BEACON_DEFINITION = SCHEMA.string(
            "supplyBeaconDefinition", "neroevents:core/beacon_supply_drop", true,
            "the event a Supply Beacon calls");

    // --- Meteors, craters and wrecks -------------------------------------------------------------

    public static final ConfigValue<Boolean> METEOR_BLOCK_DAMAGE = SCHEMA.bool(
            "meteorBlockDamage", true, true,
            "meteors dig craters (only where nothing protects the ground, and never with destructiveEventsEnabled "
                    + "false); false makes every impact a soft landing that leaves its loot pocket on the surface");
    public static final ConfigValue<Boolean> CRATER_AVOIDS_BLOCK_ENTITIES = SCHEMA.bool(
            "craterAvoidsBlockEntities", true, true,
            "any block entity (chest, machine, sign...) within craterBlockEntityMarginBlocks of a crater turns that "
                    + "impact into a soft landing");
    public static final ConfigValue<Integer> CRATER_BLOCK_ENTITY_MARGIN_BLOCKS = SCHEMA.intRange(
            "craterBlockEntityMarginBlocks", 4, 0, 16, true,
            "blocks around a crater that are checked for block entities");
    public static final ConfigValue<Integer> WRECK_LINGER_DAYS = SCHEMA.intRange(
            "wreckLingerDays", 3, 0, 30, true,
            "real days a crashed wreck stays before it starts to decay; 0 = wrecks never decay");
    public static final ConfigValue<Integer> WRECK_RECORDS_MAX = SCHEMA.intRange(
            "wreckRecordsMax", 200, 20, 1000, true,
            "most wrecks remembered for decay; the oldest is forgotten first and its blocks then never decay");

    // --- Storms ------------------------------------------------------------------------------------

    public static final ConfigValue<Double> STORM_DRAIN_FRACTION_PER_PASS = SCHEMA.doubleRange(
            "stormDrainFractionPerPass", 0.01D, 0.0D, 0.2D, true,
            "share of an exposed energy storage a storm drains every 200 ticks at intensity 3 (on Fabric only "
                    + "Neroland storages are drained)");
    public static final ConfigValue<Long> STORM_DRAIN_MAX_PER_PASS = SCHEMA.longRange(
            "stormDrainMaxPerPass", 5000L, 0L, 1000000L, true,
            "most energy (NE) drained from one storage per pass");
    public static final ConfigValue<Double> STORM_DRAIN_FLOOR_FRACTION = SCHEMA.doubleRange(
            "stormDrainFloorFraction", 0.2D, 0.0D, 0.9D, true,
            "a storm never drains a storage below this share of its capacity");
    public static final ConfigValue<Integer> STORM_DRAIN_CHUNK_RADIUS = SCHEMA.intRange(
            "stormDrainChunkRadius", 3, 1, 8, true,
            "chunks around each player in which exposed storages are drained");
    public static final ConfigValue<Integer> STORM_DRAIN_MAX_STORAGES_PER_PASS = SCHEMA.intRange(
            "stormDrainMaxStoragesPerPass", 64, 1, 512, true,
            "most storages drained per pass");
    public static final ConfigValue<Boolean> STORM_EFFECTS_ENABLED = SCHEMA.bool(
            "stormEffectsEnabled", true, true,
            "storms give status effects to exposed players; false switches them off server-wide, whatever the "
                    + "event definitions say");

    // --- Raids and wars ----------------------------------------------------------------------------

    public static final ConfigValue<Boolean> RAID_BLOCK_DAMAGE = SCHEMA.bool(
            "raidBlockDamage", false, true,
            "reserved: 0.5.0 NeroEvents never breaks blocks for a raid; keep false until a siege mechanic exists");
    public static final ConfigValue<Integer> WAR_MIN_MEMBERS_ONLINE = SCHEMA.intRange(
            "warMinMembersOnline", 2, 1, 50, true,
            "members each faction needs online before a faction war can start (needs NeroFactions)");

    // --- Privacy and history -------------------------------------------------------------------

    public static final ConfigValue<Integer> PRIVACY_REWARD_LEDGER_RETENTION_DAYS = SCHEMA.intRange(
            "privacyRewardLedgerRetentionDays", 7, 1, 90, true,
            "days a player's reward history rows are kept (never longer than privacyRetentionHardCapDays)");
    public static final ConfigValue<Integer> PRIVACY_REWARD_CRATE_RETENTION_DAYS = SCHEMA.intRange(
            "privacyRewardCrateRetentionDays", 14, 1, 90, true,
            "days unclaimed rewards wait in a player's reward crate before they are lost (never longer than "
                    + "privacyRetentionHardCapDays)");
    public static final ConfigValue<Integer> PRIVACY_RETENTION_HARD_CAP_DAYS = SCHEMA.intRange(
            "privacyRetentionHardCapDays", 90, 1, 90, true,
            "!!! HARD CAP !!! nothing player-keyed is kept longer than this, whatever the keys above say; can "
                    + "only be lowered");
    public static final ConfigValue<Integer> PRIVACY_PURGE_INTERVAL_MINUTES = SCHEMA.intRange(
            "privacyPurgeIntervalMinutes", 60, 5, 1440, true,
            "how often (minutes of server time) expired reward rows, crate entries and export files are removed "
                    + "(also on every world load)");
    public static final ConfigValue<Integer> PRIVACY_EXPORT_COOLDOWN_SECONDS = SCHEMA.intRange(
            "privacyExportCooldownSeconds", 60, 10, 86400, true,
            "seconds between two /neroevents privacy export requests by the same player");
    public static final ConfigValue<Integer> HISTORY_MAX_ENTRIES = SCHEMA.intRange(
            "historyMaxEntries", 200, 10, 2000, true,
            "how many past events the event history keeps (counts only, never players)");

    private NeroEventsConfig() {
    }

    /**
     * Whether anonymous NeroEvents-only crash reporting is on (default true, opt-out). Read once at
     * bootstrap by {@code NeroEventsTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
