package za.co.neroland.neroevents.item;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventRequest;
import za.co.neroland.neroevents.event.RequestResult;

/**
 * What using a Supply Beacon does, as a pure decision: request the configured supply-drop definition with
 * origin {@code REQUEST} at the holder's own position, so protection, the caps, the kill switches and the
 * calm window all apply exactly as for any other request. The beacon is consumed only when the drop is
 * queued; a refusal keeps it and says why.
 */
public final class SupplyBeaconUse {

    /** Refusal codes that have their own message; anything else uses the generic one. */
    static final Set<String> NAMED_REFUSALS = Set.of("protected", "kill_switch", "calm_window", "no_anchor",
            "unknown_definition");

    /** {@code consume}: take one beacon; {@code messageKey}: what the player is told. */
    public record Outcome(boolean consume, String messageKey) {
    }

    private SupplyBeaconUse() {
    }

    public static Outcome decide(Identifier definition, Predicate<Identifier> definitionExists,
            Function<EventRequest, RequestResult> engine, Identifier dimension, BlockPos holderPosition) {
        if (definition == null || !definitionExists.test(definition)) {
            return new Outcome(false, "item.neroevents.supply_beacon.unavailable");
        }
        RequestResult result = engine.apply(new EventRequest(definition, dimension, EventOrigin.REQUEST,
                holderPosition.immutable()));
        if (result instanceof RequestResult.Queued) {
            return new Outcome(true, "item.neroevents.supply_beacon.called");
        }
        String code = result instanceof RequestResult.Refused refused ? refused.code() : "refused";
        return new Outcome(false, "item.neroevents.supply_beacon.refused." + refusalKey(code));
    }

    /** The lang suffix for a refusal code: busy for every cap, the code itself when named, else generic. */
    static String refusalKey(String code) {
        if ("band_cap".equals(code) || "type_cap".equals(code) || "max_concurrent".equals(code)) {
            return "busy";
        }
        return NAMED_REFUSALS.contains(code) ? code : "other";
    }
}
