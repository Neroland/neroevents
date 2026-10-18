package za.co.neroland.neroevents.storm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.PayloadContext;

/**
 * The world side of a storm, behind one seam so the storm lifecycle is testable on a plain JVM. The
 * running server uses {@link ServerStormOps}; tests use a fake that records what was sent and applied.
 *
 * <p>Players appear here only as UUIDs of the players currently in the storm's dimension, used to decide
 * what the storm reaches (packets, effects); nothing here feeds rewards, votes or pushes, and nothing is
 * saved.
 */
public interface StormOps {

    /** The Nerospace planet a dimension belongs to; empty when unknown or the bridge fails. */
    Optional<Identifier> planetOf(Identifier dimension);

    /** The engine's current settings snapshot (defaults when no engine runs). */
    EngineSettings settings();

    /** The players in the instance's dimension right now, in a stable order. */
    List<UUID> players(PayloadContext ctx);

    /** Whether that player is out in the storm ({@link StormExposure}). */
    boolean exposed(PayloadContext ctx, UUID player);

    /** Sends a {@code neroevents:main} packet to that player (no-op if they left). */
    void send(PayloadContext ctx, UUID player, CustomPacketPayload payload);

    /** Applies the variant's row to that (exposed) player ({@link StormEffects}). */
    void applyEffects(PayloadContext ctx, UUID player, StormVariant variant, int intensity);

    /** One drain pass in the instance's dimension ({@link StormEnergyDrain}); returns storages drained. */
    int drain(PayloadContext ctx, StormVariant variant, int intensity);
}
