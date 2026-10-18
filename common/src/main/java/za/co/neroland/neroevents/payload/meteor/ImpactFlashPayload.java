package za.co.neroland.neroevents.payload.meteor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Server → client on {@code neroevents:main}: a meteor hit here. The client spawns the impact particles
 * (the {@code impact_flash} only when its own {@code hudImpactFlash} setting is on; embers and dust always).
 * Carries a block position, a severity ordinal and whether it was a soft landing — nothing about any player.
 * Sent only to players within 128 blocks of the impact.
 */
public record ImpactFlashPayload(BlockPos pos, byte severityOrdinal, boolean soft) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImpactFlashPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "impact_flash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactFlashPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ImpactFlashPayload::pos,
            ByteBufCodecs.BYTE, ImpactFlashPayload::severityOrdinal,
            ByteBufCodecs.BOOL, ImpactFlashPayload::soft,
            ImpactFlashPayload::new);

    @Override
    public CustomPacketPayload.Type<ImpactFlashPayload> type() {
        return TYPE;
    }
}
