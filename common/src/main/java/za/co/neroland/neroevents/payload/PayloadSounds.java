package za.co.neroland.neroevents.payload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The world sounds the server plays itself: pod landing and opening, meteor whoosh and impact, debris impact,
 * the war beacon's capture tick and capture. They are {@code sounds.json} entries re-pointing vanilla events,
 * sent as direct (unregistered) fixed-range sound events, so no sound registry is needed; "A + B" pairs play
 * their {@code _layer} together.
 *
 * <p>Range: the fixed range decides who is sent the sound, and the volume carries it on the client, which fades
 * a sound out linearly over 16 blocks times its volume (capped at full loudness) — so a 128-block impact is
 * played at volume 8.
 */
public final class PayloadSounds {

    public static final String SUPPLY_POD_LAND = "entity.supply_pod.land";
    public static final String METEOR_IMPACT = "entity.meteor.impact";
    public static final String DEBRIS_IMPACT = "entity.debris.impact";
    public static final String SUPPLY_POD_OPEN = "entity.supply_pod.open";
    public static final String METEOR_WHOOSH = "entity.meteor.whoosh";
    public static final String WAR_BEACON_CAPTURE_TICK = "block.war_beacon.capture_tick";
    public static final String WAR_BEACON_CAPTURED = "block.war_beacon.captured";

    /** The client's attenuation distance for a volume-1 sound. */
    static final float BASE_RANGE = 16.0F;

    private PayloadSounds() {
    }

    /** Plays {@code path} and its {@code _layer} at the position, audible to {@code range} blocks. */
    public static void playLayered(ServerLevel level, double x, double y, double z, String path, float range) {
        play(level, x, y, z, path, range);
        play(level, x, y, z, path + "_layer", range);
    }

    /** Plays one {@code neroevents:<path>} event as a fixed-range event audible to {@code range} blocks. */
    public static void play(ServerLevel level, double x, double y, double z, String path, float range) {
        play(level, x, y, z, path, range, 1.0F);
    }

    /** {@link #play(ServerLevel, double, double, double, String, float)} with a pitch multiplier. */
    public static void play(ServerLevel level, double x, double y, double z, String path, float range, float pitch) {
        try {
            SoundEvent event = SoundEvent.createFixedRangeEvent(
                    Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path), range);
            level.playSound(null, x, y, z, event, SoundSource.BLOCKS, volumeFor(range), pitch);
        } catch (RuntimeException | LinkageError e) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] sound {} not played ({})", path, e.getClass().getName());
        }
    }

    /** The volume that makes the client hear a sound out to {@code range} blocks (never below 1). */
    public static float volumeFor(float range) {
        return Math.max(1.0F, range / BASE_RANGE);
    }

    /** The war beacon's capture-tick pitch: 1.0 rising by 0.05 per tenth of the capture (to 1.5). */
    public static float captureTickPitch(int progressTicks, int captureTicks) {
        if (captureTicks <= 0 || progressTicks <= 0) {
            return 1.0F;
        }
        int tenths = Math.min(10, (int) (progressTicks * 10L / captureTicks));
        return 1.0F + 0.05F * tenths;
    }
}
