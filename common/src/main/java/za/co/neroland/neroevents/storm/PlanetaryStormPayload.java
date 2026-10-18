package za.co.neroland.neroevents.storm;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.PayloadContext;

/**
 * {@code neroevents:planetary_storm}: a planet's own storm. With {@code variant: auto} (the default) the
 * planet decides: Greenxertz dust, Cindara ion, Glacira cryo. The station or an unknown planet has no
 * storm: {@code no_storm_variant} (logged once per dimension, since that is a definition error). An
 * explicit variant runs as written.
 */
public final class PlanetaryStormPayload extends StormPayloadBase {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "planetary_storm");

    public PlanetaryStormPayload(StormOps ops) {
        super(ops);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<String> validate(Config config) {
        if (StormVariant.SOLAR.serializedName().equals(config.variant())) {
            return List.of("planetary_storm with variant 'solar': use the solar_storm type instead");
        }
        return List.of();
    }

    @Override
    protected Resolution resolveVariant(PayloadContext ctx, Config config) {
        if (!Config.AUTO.equals(config.variant())) {
            Optional<StormVariant> named = StormVariant.byName(config.variant());
            return named.map(Resolution::of).orElseGet(() -> Resolution.skip("no_storm_variant"));
        }
        Optional<StormVariant> byPlanet = ops.planetOf(ctx.dimension()).flatMap(StormVariant::forPlanet);
        return byPlanet.map(Resolution::of).orElseGet(() -> Resolution.skip("no_storm_variant"));
    }
}
