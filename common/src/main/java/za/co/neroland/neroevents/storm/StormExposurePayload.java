package za.co.neroland.neroevents.storm;

import net.minecraft.network.RegistryFriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?}
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Server &rarr; client: whether this player is out in the storm. Drives the HUD vignette, the "Shelter
 * from the storm" line and the ambient loop volume (1.0 exposed, 0.3 sheltered). Sent only when the
 * value changes. Carries one boolean and nothing else.
 */
public record StormExposurePayload(boolean exposed) implements CustomPacketPayload {

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<StormExposurePayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "storm_exposure"));
    //?} else {
    /*public static final CustomPacketPayload.Type<StormExposurePayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "storm_exposure"), StormExposurePayload.class);
    *///?}

    //? if >=1.20.5 {
    public static final StreamCodec<RegistryFriendlyByteBuf, StormExposurePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StormExposurePayload::exposed,
            StormExposurePayload::new);
    //?} else {
    /*public static final StreamCodec<RegistryFriendlyByteBuf, StormExposurePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.exposed()),
            buffer -> new StormExposurePayload(buffer.readBoolean()));
    *///?}

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
