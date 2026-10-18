package za.co.neroland.neroevents.reward;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.config.NeroEventsConfig;

/**
 * One frozen copy of the participation, reward and privacy config keys. Production reads it fresh from
 * {@link NeroEventsConfig} each time it is needed ({@link #fromConfig()}); tests build {@link #defaults()}
 * and change fields directly, keeping config-class initialisation off the plain JVM. The defaults equal
 * the config defaults, key for key.
 */
public final class RewardConfig {

    public static final Identifier DEFAULT_SUPPLY_BEACON_DEFINITION =
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "core/beacon_supply_drop");

    public int contributionSweepIntervalTicks = 100;
    public long rewardBudgetPerDay = 6000L;
    public double rewardXpValuePerPoint = 0.25D;
    public long rewardItemDefaultValue = 10L;
    public int rewardReputationMaxDeltaPerEvent = 25;
    public int rewardCrateMaxStacksPerPlayer = 27;
    public boolean rewardLedgerEnabled = true;
    public int rewardLedgerMaxEntries = 5000;
    public long bossCreaturesRewardValuePerParticipant = 150L;
    public double bossContributorMinShare = 0.05D;
    public Identifier supplyBeaconDefinition = DEFAULT_SUPPLY_BEACON_DEFINITION;
    public int privacyRewardLedgerRetentionDays = 7;
    public int privacyRewardCrateRetentionDays = 14;
    public int privacyRetentionHardCapDays = 90;
    public int privacyPurgeIntervalMinutes = 60;
    public int privacyExportCooldownSeconds = 60;
    public int historyMaxEntries = 200;

    private RewardConfig() {
    }

    public static RewardConfig defaults() {
        return new RewardConfig();
    }

    /** A snapshot of the live config. */
    public static RewardConfig fromConfig() {
        RewardConfig c = new RewardConfig();
        c.contributionSweepIntervalTicks = NeroEventsConfig.CONTRIBUTION_SWEEP_INTERVAL_TICKS.get();
        c.rewardBudgetPerDay = NeroEventsConfig.REWARD_BUDGET_PER_DAY.get();
        c.rewardXpValuePerPoint = NeroEventsConfig.REWARD_XP_VALUE_PER_POINT.get();
        c.rewardItemDefaultValue = NeroEventsConfig.REWARD_ITEM_DEFAULT_VALUE.get();
        c.rewardReputationMaxDeltaPerEvent = NeroEventsConfig.REWARD_REPUTATION_MAX_DELTA_PER_EVENT.get();
        c.rewardCrateMaxStacksPerPlayer = NeroEventsConfig.REWARD_CRATE_MAX_STACKS_PER_PLAYER.get();
        c.rewardLedgerEnabled = NeroEventsConfig.REWARD_LEDGER_ENABLED.get();
        c.rewardLedgerMaxEntries = NeroEventsConfig.REWARD_LEDGER_MAX_ENTRIES.get();
        c.bossCreaturesRewardValuePerParticipant = NeroEventsConfig.BOSS_CREATURES_REWARD_VALUE_PER_PARTICIPANT.get();
        c.bossContributorMinShare = NeroEventsConfig.BOSS_CONTRIBUTOR_MIN_SHARE.get();
        Identifier beacon = Identifier.tryParse(NeroEventsConfig.SUPPLY_BEACON_DEFINITION.get().trim());
        c.supplyBeaconDefinition = beacon != null ? beacon : DEFAULT_SUPPLY_BEACON_DEFINITION;
        c.privacyRewardLedgerRetentionDays = NeroEventsConfig.PRIVACY_REWARD_LEDGER_RETENTION_DAYS.get();
        c.privacyRewardCrateRetentionDays = NeroEventsConfig.PRIVACY_REWARD_CRATE_RETENTION_DAYS.get();
        c.privacyRetentionHardCapDays = NeroEventsConfig.PRIVACY_RETENTION_HARD_CAP_DAYS.get();
        c.privacyPurgeIntervalMinutes = NeroEventsConfig.PRIVACY_PURGE_INTERVAL_MINUTES.get();
        c.privacyExportCooldownSeconds = NeroEventsConfig.PRIVACY_EXPORT_COOLDOWN_SECONDS.get();
        c.historyMaxEntries = NeroEventsConfig.HISTORY_MAX_ENTRIES.get();
        return c;
    }

    public ValueTable values() {
        return new ValueTable(rewardXpValuePerPoint, rewardItemDefaultValue);
    }

    /** The ledger retention actually applied: configured, clamped to {@code [1, hard cap]}. */
    public int ledgerDays() {
        return clamp(privacyRewardLedgerRetentionDays);
    }

    /** The crate retention actually applied: configured, clamped to {@code [1, hard cap]}. */
    public int crateDays() {
        return clamp(privacyRewardCrateRetentionDays);
    }

    /** Whether a configured retention exceeded the hard cap (one warning per server start). */
    public boolean retentionClamped() {
        return privacyRewardLedgerRetentionDays > hardCap() || privacyRewardCrateRetentionDays > hardCap();
    }

    private int hardCap() {
        return Math.max(1, Math.min(90, privacyRetentionHardCapDays));
    }

    private int clamp(int configured) {
        return Math.min(hardCap(), Math.max(1, configured));
    }
}
