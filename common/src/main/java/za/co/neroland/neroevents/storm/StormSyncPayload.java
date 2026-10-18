package za.co.neroland.neroevents.storm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Server &rarr; client: the storm in one dimension, for sky tint, fog, particles and the ambient loop.
 * Sent on {@code neroevents:main} to every player in the dimension at start, on resume, to players who
 * enter mid-storm, as a periodic refresh, and with {@code variant = 0} (none) at cleanup. Carries no
 * position and no player data.
 *
 * @param dimension the dimension the storm is in
 * @param variant   {@code 0} none, {@code 1} solar, {@code 2} dust, {@code 3} ion, {@code 4} cryo
 *                  ({@link StormVariant#wireId()})
 * @param intensity 1..3 ({@code 0} with none)
 * @param rampTicks client lerp length for the fade in or out
 * @param windYaw   particle wind direction in degrees (dust and cryo)
 */
public record StormSyncPayload(Identifier dimension, byte variant, byte intensity, int rampTicks, short windYaw)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StormSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "storm_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StormSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, StormSyncPayload::dimension,
            ByteBufCodecs.BYTE, StormSyncPayload::variant,
            ByteBufCodecs.BYTE, StormSyncPayload::intensity,
            ByteBufCodecs.VAR_INT, StormSyncPayload::rampTicks,
            ByteBufCodecs.SHORT, StormSyncPayload::windYaw,
            StormSyncPayload::new);

    /** The packet for an active storm. */
    public static StormSyncPayload active(Identifier dimension, StormVariant variant, int intensity, int rampTicks,
            int windYaw) {
        return new StormSyncPayload(dimension, variant.wireId(), (byte) StormVariant.clampIntensity(intensity),
                rampTicks, (short) Math.floorMod(windYaw, 360));
    }

    /** The "storm over" packet: fades out over 200 ticks. */
    public static StormSyncPayload none(Identifier dimension) {
        return new StormSyncPayload(dimension, StormVariant.NONE_WIRE_ID, (byte) 0, 200, (short) 0);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
