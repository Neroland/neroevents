package za.co.neroland.neroevents.creatures;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.event.PayloadContext;

/**
 * {@code neroevents:pirate_raid}: waves of space pirates and rogue machines ({@code nerocreatures:space_pirate},
 * {@code rogue_drone}, {@code rogue_android}) near where players are.
 */
public final class PirateRaidPayload extends WavePayloadBase {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "pirate_raid");

    public PirateRaidPayload(CreaturesBridge bridge) {
        super(ID, ServerCreatureWorld.factory(bridge, SOURCE));
    }

    /** Tests: every seam injected. */
    public PirateRaidPayload(Function<PayloadContext, CreatureWorld> worlds, BooleanSupplier available,
            Predicate<Identifier> knownTypes) {
        super(ID, worlds, available, knownTypes);
    }
}
