package za.co.neroland.neroevents.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.nerospace.NerospaceBridge;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.platform.Services;

/** The running-server {@link StormOps}: the level, the network seam, effects and the drain pass. */
final class ServerStormOps implements StormOps {

    private final NerospaceBridge bridge;

    ServerStormOps(NerospaceBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Optional<Identifier> planetOf(Identifier dimension) {
        try {
            Optional<Identifier> planet = bridge.planetId(dimension);
            return planet == null ? Optional.empty() : planet;
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    @Override
    public EngineSettings settings() {
        EventEngine engine = EventEngine.current();
        return engine == null ? EngineSettings.defaults() : engine.settings();
    }

    @Override
    public List<UUID> players(PayloadContext ctx) {
        ServerLevel level = level(ctx);
        if (level == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            out.add(player.getUUID());
        }
        return out;
    }

    @Override
    public boolean exposed(PayloadContext ctx, UUID player) {
        ServerLevel level = level(ctx);
        ServerPlayer p = player(ctx, player);
        return level != null && p != null && p.level() == level && StormExposure.exposed(level, p, bridge);
    }

    @Override
    public void send(PayloadContext ctx, UUID player, CustomPacketPayload payload) {
        ServerPlayer p = player(ctx, player);
        if (p != null) {
            Services.NETWORK.sendToPlayer(p, payload);
        }
    }

    @Override
    public void applyEffects(PayloadContext ctx, UUID player, StormVariant variant, int intensity) {
        ServerPlayer p = player(ctx, player);
        if (p != null) {
            StormEffects.apply(variant, intensity, p, ctx.now());
        }
    }

    @Override
    public int drain(PayloadContext ctx, StormVariant variant, int intensity) {
        ServerLevel level = level(ctx);
        if (level == null) {
            return 0;
        }
        return StormEnergyDrain.pass(level, ctx.dimension(), ctx.protection(), variant, intensity, settings());
    }

    private static @Nullable ServerLevel level(PayloadContext ctx) {
        return ctx instanceof ServerPayloadContext s ? s.level() : null;
    }

    private static @Nullable ServerPlayer player(PayloadContext ctx, UUID player) {
        return ctx instanceof ServerPayloadContext s ? s.server().getPlayerList().getPlayer(player) : null;
    }
}
