package za.co.neroland.neroevents.protect;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;

/**
 * The protection contract: <i>may this event affect this block, entity or volume?</i> Every block or
 * entity effect of every payload goes through an evaluation from here — craters, wreck placement, raid
 * damage, storm effects, spawn positions. A payload that changes the world without asking fails review.
 */
public final class ProtectionQuery {

    private static final ProtectionQuery LIVE = new ProtectionQuery(null);

    private final @Nullable List<ProtectionSource> fixed;

    /** A query over a fixed, ordered source list (tests); {@code null} reads {@link ProtectionSources}. */
    public ProtectionQuery(@Nullable List<ProtectionSource> ordered) {
        this.fixed = ordered == null ? null : List.copyOf(ordered);
    }

    /** Uses {@link ProtectionSources}' ordered list, read fresh per evaluation. */
    public static ProtectionQuery live() {
        return LIVE;
    }

    /** A fresh evaluation; its memo lives as long as it does. */
    public ProtectionEvaluation evaluation(WorldView world) {
        return new ProtectionEvaluation(world, fixed != null ? fixed : ProtectionSources.ordered());
    }
}
