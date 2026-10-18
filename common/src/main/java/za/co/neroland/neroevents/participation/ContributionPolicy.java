package za.co.neroland.neroevents.participation;

import java.util.function.Supplier;

import za.co.neroland.neroevents.config.NeroEventsConfig;
import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.event.EventDefinition;

/**
 * How contributions are weighed, frozen per instance when it is created.
 *
 * @param damageWeight          weight per damage point (half-heart) dealt to an event entity
 * @param lootWeight            weight per credited container, pocket or pod open
 * @param objectiveWeight       weight per full objective capture
 * @param survivalWeight        weight per wave survived inside the bounds (needs another kind first)
 * @param inactivityTicks       outside the bounds or offline this long ⇒ the weight decays per window
 * @param decayFactor           multiplier applied per elapsed window
 * @param minWeight             an entry decayed below this is dropped
 * @param creditFloor           below this an entry is not a participant (no gates, no rewards)
 * @param decay                 whether decay applies at all (a definition can switch it off)
 * @param floorEligibleRawShare the raw share a participant needs to receive the minimum-share floor
 */
public record ContributionPolicy(double damageWeight, double lootWeight, double objectiveWeight,
        double survivalWeight, int inactivityTicks, double decayFactor, double minWeight, double creditFloor,
        boolean decay, double floorEligibleRawShare) {

    /** The shipped defaults (equal to the config defaults, key for key). */
    public static final ContributionPolicy DEFAULT = new ContributionPolicy(1.0D, 25.0D, 50.0D, 10.0D, 6000, 0.5D,
            0.05D, 1.0D, true, 0.01D);

    /** Defaults until the first server switches to the live config (keeps plain-JVM tests config-free). */
    private static volatile Supplier<ContributionPolicy> base = () -> DEFAULT;

    /** Production: read the live config for every new instance. Called once when the first engine is built. */
    public static void useConfig() {
        base = ContributionPolicy::readConfig;
    }

    /** The policy for a new instance of {@code definition} ({@code rewards.decay=false} switches decay off). */
    public static ContributionPolicy fromConfig(EventDefinition definition) {
        ContributionPolicy policy = base.get();
        boolean decay = policy.decay && (definition == null || definition.rewards().decay());
        return decay == policy.decay ? policy : policy.withDecay(decay);
    }

    static ContributionPolicy readConfig() {
        return new ContributionPolicy(NeroEventsConfig.CONTRIBUTION_WEIGHT_DAMAGE.get(),
                NeroEventsConfig.CONTRIBUTION_WEIGHT_LOOT.get(), NeroEventsConfig.CONTRIBUTION_WEIGHT_OBJECTIVE.get(),
                NeroEventsConfig.CONTRIBUTION_WEIGHT_SURVIVAL.get(), NeroEventsConfig.CONTRIBUTION_INACTIVITY_TICKS.get(),
                NeroEventsConfig.CONTRIBUTION_DECAY_FACTOR.get(), NeroEventsConfig.CONTRIBUTION_MIN_WEIGHT.get(),
                NeroEventsConfig.CONTRIBUTION_CREDIT_FLOOR.get(), true,
                NeroEventsConfig.REWARD_FLOOR_ELIGIBLE_RAW_SHARE.get());
    }

    public ContributionPolicy withDecay(boolean on) {
        return new ContributionPolicy(damageWeight, lootWeight, objectiveWeight, survivalWeight, inactivityTicks,
                decayFactor, minWeight, creditFloor, on, floorEligibleRawShare);
    }

    /** {@code amount × kindWeight}. */
    public double weightOf(Kind kind, double amount) {
        return amount * switch (kind) {
            case DAMAGE -> damageWeight;
            case LOOT -> lootWeight;
            case OBJECTIVE -> objectiveWeight;
            case SURVIVAL -> survivalWeight;
        };
    }
}
