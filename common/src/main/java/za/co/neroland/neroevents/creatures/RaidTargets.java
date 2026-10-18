package za.co.neroland.neroevents.creatures;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The raid-target seam: providers in registration order, the first present answer wins. Empty with no
 * providers (the shipped state), so the engine falls back to the density anchor.
 */
public final class RaidTargets {

    private static final List<RaidTargetProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private RaidTargets() {
    }

    /** Adds a provider (init time). */
    public static void register(RaidTargetProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    /** The first provider's present approach anchor, in registration order; a throwing provider is skipped. */
    public static Optional<BlockPos> pick(ServerPayloadContext ctx, int spawnRadiusMax) {
        for (RaidTargetProvider provider : PROVIDERS) {
            try {
                Optional<BlockPos> anchor = provider.approachAnchor(ctx, spawnRadiusMax);
                if (anchor != null && anchor.isPresent()) {
                    return anchor;
                }
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "payload:waves", "start");
            }
        }
        return Optional.empty();
    }

    /** How many providers are registered. */
    public static int size() {
        return PROVIDERS.size();
    }

    /** Test isolation. */
    static void resetForTests() {
        PROVIDERS.clear();
    }
}
