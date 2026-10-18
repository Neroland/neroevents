package za.co.neroland.neroevents.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateScope;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Listener for the <b>hook gates</b> a quest mod opens when a player completes one of NeroEvents' own
 * quests ({@code data/neroevents/neroland_gates/hook/*.json}, named by the pack's {@code mod_hook}
 * rewards). NeroEvents never imports the quest mod: it only hears Core's progression events.
 *
 * <p>{@code neroevents:hook/supply_beacon} — owes the player a one-use <b>Supply Beacon</b> that calls a
 * small supply drop at the holder's position under every event rule. Common init installs the real
 * {@link Delivery} ({@code item/SupplyBeaconDelivery}) with {@link #setSupplyBeaconDelivery}; the first
 * time it hands the item over (on the hook, or on login for a claim that opened while the player was
 * offline) the gate is closed to re-arm it. A full inventory leaves the gate open for the next login. The
 * pending notice below remains the fallback when no delivery is installed (it repeats at most once per
 * login, per server).
 *
 * <p>Delivery is bounded and idempotent: one beacon per hook opening. Logins are detected loader-free
 * from the server tick ({@link #tick}), the same way the other Neroland mods do it.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the gate change's target is the player's UUID string; it is used only
 * to find the online player and to close the gate, never logged or broadcast. Hook gates are records that
 * gate nothing and are never checked for access. The UUID sets kept for join detection and the once-per-
 * login notice are process-local and dropped when the server changes.
 */
public final class QuestHooks {

    /** The hook gate the "Event Log" pack rewards with a Supply Beacon. */
    public static final Identifier SUPPLY_BEACON =
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "hook/supply_beacon");

    /** Hands a hook's reward to an online player. */
    @FunctionalInterface
    public interface Delivery {
        /**
         * @return {@code true} once the reward is in the player's hands, so the hook gate is closed;
         *         {@code false} to leave the gate open as a pending claim
         */
        boolean deliver(ServerPlayer player);
    }

    /** Login sweep cadence (ticks). */
    private static final int JOIN_SWEEP_INTERVAL = 20;

    /** The running server, captured from the server tick so the listener can resolve online players. */
    private static volatile @Nullable MinecraftServer server;
    /** Players seen online at the last sweep — a join is a UUID that was not in it. Process-local. */
    private static final Set<UUID> ONLINE = new HashSet<>();
    /** Players already told this login that a beacon is pending. Cleared when they leave. */
    private static final Set<UUID> NOTIFIED = new HashSet<>();
    private static @Nullable MinecraftServer sweptServer;
    private static volatile Delivery supplyBeaconDelivery = QuestHooks::pendingNotice;
    private static boolean registered;

    private QuestHooks() {
    }

    /** Register the progression listener; call once from common init. */
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        CoreEvents.onProgression(change -> {
            if (!change.open() || change.scope() != GateScope.PLAYER || !SUPPLY_BEACON.equals(change.gate())) {
                return;
            }
            MinecraftServer running = server;
            if (running == null) {
                return; // no server yet: the gate stays open and onJoin delivers it
            }
            UUID id;
            try {
                id = UUID.fromString(change.target());
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerPlayer player = running.getPlayerList().getPlayer(id);
            if (player == null) {
                return; // offline: the gate stays open, see onJoin
            }
            if (supplyBeaconDelivery.deliver(player)) {
                ProgressionGates.close(running, id, SUPPLY_BEACON); // re-arm; the open=false change is ignored above
            }
        });
    }

    /**
     * Installs the real Supply Beacon hand-over. The supply-drop feature calls this from common init once
     * the item exists; {@code null} restores the pending notice.
     */
    public static void setSupplyBeaconDelivery(@Nullable Delivery delivery) {
        supplyBeaconDelivery = delivery != null ? delivery : QuestHooks::pendingNotice;
    }

    /**
     * Server tick: remember the server and, once a second, deliver pending hooks to players who logged in
     * since the last sweep. Cheap — a set diff over the online player list.
     */
    public static void tick(MinecraftServer running) {
        server = running;
        if (running.getTickCount() % JOIN_SWEEP_INTERVAL != 0) {
            return;
        }
        if (sweptServer != running) {
            ONLINE.clear(); // a new server in this process (singleplayer world switch) starts fresh
            NOTIFIED.clear();
            sweptServer = running;
        }
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            now.add(player.getUUID());
            if (ONLINE.add(player.getUUID())) {
                onJoin(player);
            }
        }
        ONLINE.retainAll(now);
        NOTIFIED.retainAll(now);
    }

    /** On login: deliver a hook that opened while the player was offline (or is still pending). */
    public static void onJoin(ServerPlayer player) {
        if (ProgressionGates.isOpen(player, SUPPLY_BEACON) && supplyBeaconDelivery.deliver(player)) {
            ProgressionGates.close(player, SUPPLY_BEACON);
        }
    }

    /**
     * The skeleton's effect: tell the player a Supply Beacon is reserved for them, once per login, and leave
     * the gate open so the real delivery can honour it later.
     */
    static boolean pendingNotice(ServerPlayer player) {
        if (NOTIFIED.add(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.neroevents.supply_beacon_pending"));
        }
        return false;
    }
}
