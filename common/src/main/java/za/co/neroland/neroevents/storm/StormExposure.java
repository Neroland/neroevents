package za.co.neroland.neroevents.storm;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroevents.compat.nerospace.NerospaceBridge;

/**
 * Whether a player is out in the storm. The rule: the player can see the sky from the block above their
 * feet <b>and</b> is not in a pressurised atmosphere. Breathable air is not shelter; vanilla glass does not
 * block sky light, so a glass roof does not count.
 *
 * <p>Fail-safe toward the player: a bridge failure or an unloaded chunk (no atmosphere reading) counts as
 * pressurised, so a storm that cannot read the environment hurts nobody. Spectators and creative players
 * are never exposed; a rider is judged at its root vehicle's position.
 */
public final class StormExposure {

    public static final String PRESSURIZED = "PRESSURIZED";

    private StormExposure() {
    }

    /** The pure rule. {@code atmosphere} empty = unreadable = sheltered. */
    public static boolean exposed(boolean canSeeSky, Optional<String> atmosphere, boolean spectatorOrCreative) {
        if (spectatorOrCreative || !canSeeSky) {
            return false;
        }
        return !PRESSURIZED.equals(atmosphere.orElse(PRESSURIZED));
    }

    /** The Minecraft adapter. Server thread. */
    public static boolean exposed(ServerLevel level, ServerPlayer player, NerospaceBridge bridge) {
        boolean immune = player.isSpectator() || player.isCreative();
        if (immune) {
            return false;
        }
        BlockPos pos = player.getRootVehicle().blockPosition();
        boolean sky = level.canSeeSky(pos.above());
        if (!sky) {
            return false;
        }
        return exposed(true, atmosphere(bridge, level, pos), false);
    }

    private static Optional<String> atmosphere(NerospaceBridge bridge, ServerLevel level, BlockPos pos) {
        if (bridge == null) {
            return Optional.empty();
        }
        try {
            Optional<String> reading = bridge.atmosphereAt(level, pos);
            return reading == null ? Optional.empty() : reading;
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }
}
