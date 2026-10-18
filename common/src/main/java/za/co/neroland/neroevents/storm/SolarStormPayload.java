package za.co.neroland.neroevents.storm;

import java.util.List;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.PayloadContext;

/**
 * {@code neroevents:solar_storm}: a solar storm over any space dimension (dimension type in
 * {@code neroland:space/dimensions}). Always the {@link StormVariant#SOLAR} row; the {@code variant} key is
 * ignored. Open sky on a breathable planet is still exposed.
 */
public final class SolarStormPayload extends StormPayloadBase {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "solar_storm");

    public SolarStormPayload(StormOps ops) {
        super(ops);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<String> validate(Config config) {
        if (!Config.AUTO.equals(config.variant()) && !StormVariant.SOLAR.serializedName().equals(config.variant())) {
            return List.of("variant '" + config.variant() + "' is ignored by solar_storm (always solar)");
        }
        return List.of();
    }

    @Override
    protected Resolution resolveVariant(PayloadContext ctx, Config config) {
        if (!ctx.world().dimensionInTag(ctx.dimension(), SPACE_DIMENSIONS)) {
            return Resolution.skip("not_space");
        }
        return Resolution.of(StormVariant.SOLAR);
    }
}
