package za.co.neroland.neroevents.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroevents.client.ClientConsoleState;
import za.co.neroland.neroevents.client.ClientHudState;
import za.co.neroland.neroevents.console.ConsoleServer;
import za.co.neroland.neroevents.fx.EventFx;
import za.co.neroland.neroevents.payload.meteor.ImpactFlashPayload;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.storm.StormExposurePayload;
import za.co.neroland.neroevents.storm.StormSyncPayload;
import za.co.neroland.neroevents.storm.client.ClientStormState;

/**
 * The cross-loader payload registry for NeroEvents' own channel, {@code neroevents:main}. Payloads
 * are declared here once (type + stream codec + handler); each loader module iterates the lists and
 * wires them to its own networking API — NeoForge's {@code PayloadRegistrar}, Forge's
 * {@code ChannelBuilder}, Fabric's {@code PayloadTypeRegistry} + {@code Client/ServerPlayNetworking}.
 * Sending goes through the {@link Services#NETWORK} seam.
 *
 * <p>This build declares five server &rarr; client payloads (the meteor impact flash, the two storm packets,
 * the console snapshot and the HUD state) and five client &rarr; server console requests. Adding one is a one-line
 * {@link #clientbound}/{@link #serverbound} call in {@link #init()}. NeroEvents never adds to Neroland
 * Core's network registry (see
 * {@link za.co.neroland.neroevents.platform.NetworkPlatform} for why).
 *
 * <p>NeroEvents is server-authoritative: anything a client sends is a request the server validates,
 * never a fact it trusts. Payloads must carry no more personal data than the receiving screen needs
 * (POPIA/GDPR data minimisation).
 */
public final class EventNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client &rarr; server payload plus the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    /** The channel path under the {@code neroevents} namespace ({@code neroevents:main}). */
    public static final String CHANNEL = "main";

    /** Wire protocol version (NeoForge registrar version). Bump on any incompatible payload change. */
    public static final String PROTOCOL_VERSION = "1";

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private static boolean declared;

    private EventNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return Collections.unmodifiableList(CLIENTBOUND);
    }

    /** Every declared client &rarr; server payload, for each loader's registration pass. */
    public static List<Serverbound<?>> serverbound() {
        return Collections.unmodifiableList(SERVERBOUND);
    }

    /** True when at least one payload has been declared (Forge only builds its channel then). */
    public static boolean hasPayloads() {
        return !CLIENTBOUND.isEmpty() || !SERVERBOUND.isEmpty();
    }

    /**
     * Declares the payloads. Called once from common init, before any loader registers them (each
     * loader entry point runs common init first, then its own network registration). On Forge the
     * channel is sealed at {@code build()}, so a payload declared later would never exist.
     */
    public static void init() {
        if (declared) {
            return; // defensive: a second call must not duplicate registrations
        }
        declared = true;
        // Meteor impact: the flash, ember cone and ground dust. The handler goes through the EventFx seam, whose
        // client sink each loader installs, so nothing client-only is named here.
        clientbound(ImpactFlashPayload.TYPE, ImpactFlashPayload.STREAM_CODEC, EventFx::impact);
        // Storms: the storm in a dimension (re-sent periodically) and this player's exposure. The handlers
        // only touch ClientStormState, which references no client-only class.
        clientbound(StormSyncPayload.TYPE, StormSyncPayload.STREAM_CODEC, ClientStormState::onSync);
        clientbound(StormExposurePayload.TYPE, StormExposurePayload.STREAM_CODEC, ClientStormState::onExposure);
        // The Event Console (snapshot + five requests) and the HUD state. The clientbound handlers only touch
        // ClientConsoleState / ClientHudState, which reference no client-only class.
        ConsoleServer.register();
    }

    /**
     * Drops every client-side mirror, so one session's state can never bleed into the next. Each loader
     * wires this to its client-disconnect event.
     */
    public static void clearClientCaches() {
        ClientStormState.clear();
        ClientConsoleState.clear();
        ClientHudState.clear();
    }
}
