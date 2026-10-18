package za.co.neroland.neroevents.creatures;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.event.PayloadContext;

/**
 * {@code neroevents:alien_invasion}: waves of hostile alien life ({@code nerocreatures:void_crawler},
 * {@code lunar_stalker}, {@code asteroid_worm}, {@code plasma_slime}) near where players are.
 */
public final class AlienInvasionPayload extends WavePayloadBase {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "alien_invasion");

    public AlienInvasionPayload(CreaturesBridge bridge) {
        super(ID, ServerCreatureWorld.factory(bridge, SOURCE));
    }

    /** Tests: every seam injected. */
    public AlienInvasionPayload(Function<PayloadContext, CreatureWorld> worlds, BooleanSupplier available,
            Predicate<Identifier> knownTypes) {
        super(ID, worlds, available, knownTypes);
    }
}
