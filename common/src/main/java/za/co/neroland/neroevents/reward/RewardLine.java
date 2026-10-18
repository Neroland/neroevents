package za.co.neroland.neroevents.reward;

import java.util.OptionalLong;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.RewardSpec;

/**
 * One reward line as the resolver pays it. {@code rank} 0 is a main line (split over every credited
 * participant); {@code rank ≥ 1} is a top-N bonus line for that rank only.
 */
public sealed interface RewardLine {

    int rank();

    /** Credits through NeroEconomy (1 credit = 1 value unit). */
    record Currency(long amount, int rank) implements RewardLine {
    }

    /** Experience points. */
    record Xp(long amount, int rank) implements RewardLine {
    }

    /** {@code count} of {@code item}; {@code value} overrides the default value per item. */
    record Item(Identifier item, int count, OptionalLong value, int rank) implements RewardLine {
    }

    /** A reputation change with a faction (only with a real reputation provider). */
    record Reputation(Identifier faction, int delta, int rank) implements RewardLine {
    }

    /** The resolver's view of a definition line. */
    static RewardLine of(RewardSpec.RewardLine raw) {
        return switch (raw.kind()) {
            case CURRENCY -> new Currency(raw.amount(), raw.rank());
            case XP -> new Xp(raw.amount(), raw.rank());
            case ITEM -> new Item(raw.item().orElseThrow(), raw.count(), raw.value(), raw.rank());
            case REPUTATION -> new Reputation(raw.faction().orElseThrow(), raw.delta(), raw.rank());
        };
    }
}
