package za.co.neroland.neroevents.participation;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.progression.Milestones;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * How payloads report participation — the only contribution API payload authors use. Every method credits
 * only an <b>attributable</b> action by a real, online, non-spectator {@link ServerPlayer} (a projectile's
 * owner counts; mod automation's fake players are not in the player list and never count), and is a no-op
 * outside ACTIVE. Never proximity: standing near an event earns nothing.
 */
public final class Contributions {

    private Contributions() {
    }

    private static boolean active(PayloadContext ctx) {
        return ctx != null && ctx.instance().phase() == EventPhase.ACTIVE;
    }

    private static @Nullable MinecraftServer server(PayloadContext ctx) {
        return ctx instanceof ServerPayloadContext s ? s.server() : null;
    }

    /**
     * Damage dealt to an event entity. Credits iff the damage source's attributing entity is a real online
     * player. Returns the weight credited (0 if not attributable).
     */
    public static double creditDamage(PayloadContext ctx, DamageSource source, float amount) {
        if (!active(ctx) || source == null || !(amount > 0.0F)) {
            return 0.0D;
        }
        Entity attacker = source.getEntity();
        if (!attributable(server(ctx), attacker)) {
            return 0.0D;
        }
        UUID id = attacker.getUUID();
        long before = ctx.contributions().weightMilli(id);
        ctx.contributions().credit(id, Kind.DAMAGE, amount);
        return (ctx.contributions().weightMilli(id) - before) / 1000.0D;
    }

    /**
     * A container, pocket or pod opened or mined. {@code onceKey} is the container's own identity (an entity
     * UUID, {@code "pocket:<index>"}, {@code "pod:<uuid>"}), never a player value. {@code false} if this
     * player was already credited for it.
     */
    public static boolean creditLoot(PayloadContext ctx, ServerPlayer player, String onceKey) {
        if (!active(ctx) || !attributable(server(ctx), player)) {
            return false;
        }
        return ctx.contributions().creditOnce(player.getUUID(), Kind.LOOT, onceKey, 1.0D);
    }

    /** An objective captured; {@code captureFraction} in (0, 1] for partial holds. */
    public static void creditObjective(PayloadContext ctx, ServerPlayer player, double captureFraction) {
        if (!active(ctx) || !(captureFraction > 0.0D) || captureFraction > 1.0D || !attributable(server(ctx), player)) {
            return;
        }
        ctx.contributions().credit(player.getUUID(), Kind.OBJECTIVE, captureFraction);
    }

    /** A wave survived inside the bounds (once per wave; needs another kind of contribution first). */
    public static boolean creditSurvival(PayloadContext ctx, ServerPlayer player, int waveIndex) {
        if (!active(ctx) || !attributable(server(ctx), player)) {
            return false;
        }
        return ctx.contributions().creditOnce(player.getUUID(), Kind.SURVIVAL, "wave:" + waveIndex, 1.0D);
    }

    /**
     * A supply pod's <b>first</b> open: credits the opener as loot and records the {@code supply_claimed}
     * milestone. The supply-drop payload calls this on the pod's first open only.
     */
    public static boolean creditSupplyClaim(PayloadContext ctx, ServerPlayer player, UUID podUuid) {
        if (!active(ctx) || !attributable(server(ctx), player)) {
            return false;
        }
        return creditSupplyClaim(ctx, player.getUUID(), podUuid, Milestones.forServer(server(ctx)));
    }

    /** The attribution-free core of {@link #creditSupplyClaim(PayloadContext, ServerPlayer, UUID)}. */
    public static boolean creditSupplyClaim(PayloadContext ctx, UUID player, UUID podUuid, Milestones milestones) {
        if (!active(ctx) || player == null || podUuid == null) {
            return false;
        }
        if (!ctx.contributions().creditOnce(player, Kind.LOOT, "pod:" + podUuid, 1.0D)) {
            return false;
        }
        try {
            milestones.recordSupplyClaimed(player);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "participation", "supply_claimed");
        }
        return true;
    }

    /** The attribution rule: a real online player (in the player list, not a fake player) and not spectating. */
    public static boolean attributable(@Nullable MinecraftServer server, @Nullable Entity entity) {
        return server != null && entity instanceof ServerPlayer player
                && server.getPlayerList().getPlayer(player.getUUID()) == player && !player.isSpectator();
    }
}
