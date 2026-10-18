package za.co.neroland.neroevents.storm.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.storm.StormExposurePayload;
import za.co.neroland.neroevents.storm.StormSyncPayload;
import za.co.neroland.neroevents.storm.StormVariant;

/**
 * The client's mirror of the storms it was told about: one entry per dimension, faded in and out over the
 * packet's ramp, plus this player's exposure. Fed by the two storm packet handlers; read by the sky, fog,
 * particle and sound code.
 *
 * <p>Pure: no client class is referenced, so the packet handlers can name it from common code without
 * loading anything client-only on a dedicated server. The loaders hop to the client thread before running
 * a handler; the methods are synchronized anyway because the sky and fog layers may be sampled from the
 * render thread.
 */
public final class ClientStormState {

    /** What the client draws for the storm in one dimension. {@code strength} is 0..1 (fade × intensity/3). */
    public record View(StormVariant variant, int intensity, float strength, int windYaw, boolean exposed,
            float loopVolume) {
    }

    /** Ambient loop volume when sheltered (1.0 exposed). */
    public static final float SHELTERED_VOLUME = 0.3F;
    /** Ticks for the loop volume to follow a change of exposure. */
    public static final int VOLUME_LERP_TICKS = 20;
    /** Shortest fade the client accepts (a packet asking for less is lengthened). */
    public static final int MIN_RAMP_TICKS = 20;

    private static final class Entry {
        StormVariant variant;
        int intensity;
        int windYaw;
        boolean active;
        int rampTicks = 200;
        float mix;
    }

    private static final Map<Identifier, Entry> BY_DIMENSION = new HashMap<>();
    private static boolean exposed;
    private static float loopVolume = SHELTERED_VOLUME;

    private ClientStormState() {
    }

    /** {@link StormSyncPayload} handler. */
    public static synchronized void onSync(StormSyncPayload payload) {
        if (payload == null || payload.dimension() == null) {
            return;
        }
        Optional<StormVariant> variant = StormVariant.byWireId(payload.variant());
        int ramp = Math.max(MIN_RAMP_TICKS, payload.rampTicks());
        if (variant.isEmpty()) {
            Entry entry = BY_DIMENSION.get(payload.dimension());
            if (entry != null) {
                entry.active = false;
                entry.rampTicks = ramp;
            }
            return;
        }
        Entry entry = BY_DIMENSION.computeIfAbsent(payload.dimension(), d -> new Entry());
        if (entry.variant != null && entry.variant != variant.get()) {
            entry.mix = 0.0F; // a different storm replaced the old one: start its fade from clear sky
        }
        entry.variant = variant.get();
        entry.intensity = StormVariant.clampIntensity(payload.intensity());
        entry.windYaw = Math.floorMod(payload.windYaw(), 360);
        entry.rampTicks = ramp;
        entry.active = true;
    }

    /** {@link StormExposurePayload} handler. */
    public static synchronized void onExposure(StormExposurePayload payload) {
        if (payload != null) {
            exposed = payload.exposed();
        }
    }

    /** One client tick: advances every fade and the loop volume. */
    public static synchronized void tick() {
        Iterator<Map.Entry<Identifier, Entry>> it = BY_DIMENSION.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            float step = 1.0F / entry.rampTicks;
            if (entry.active) {
                entry.mix = Math.min(1.0F, entry.mix + step);
            } else {
                entry.mix = Math.max(0.0F, entry.mix - step);
                if (entry.mix <= 1.0E-4F) {
                    it.remove();
                }
            }
        }
        float target = exposed ? 1.0F : SHELTERED_VOLUME;
        float step = (1.0F - SHELTERED_VOLUME) / VOLUME_LERP_TICKS;
        loopVolume = loopVolume < target ? Math.min(target, loopVolume + step) : Math.max(target, loopVolume - step);
    }

    /** The storm to draw in {@code dimension}, if any is visible. */
    public static synchronized Optional<View> view(Identifier dimension) {
        Entry entry = dimension == null ? null : BY_DIMENSION.get(dimension);
        if (entry == null || entry.variant == null || entry.mix <= 0.0F) {
            return Optional.empty();
        }
        float strength = entry.mix * entry.intensity / 3.0F;
        return Optional.of(new View(entry.variant, entry.intensity, strength, entry.windYaw, exposed,
                loopVolume * entry.mix));
    }

    /** Whether the server last said this player is out in the storm. */
    public static synchronized boolean exposed() {
        return exposed;
    }

    /** Forgets everything (new level, disconnect). The server re-syncs within 200 ticks. */
    public static synchronized void clear() {
        BY_DIMENSION.clear();
        exposed = false;
        loopVolume = SHELTERED_VOLUME;
    }
}
