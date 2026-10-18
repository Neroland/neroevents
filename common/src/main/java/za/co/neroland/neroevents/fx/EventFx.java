package za.co.neroland.neroevents.fx;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;
import za.co.neroland.neroevents.payload.meteor.ImpactFlashPayload;
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;

/**
 * The seam between common payload code and the client-only effects (particles, the pod's descent loop, the
 * impact flash). The payload entities call {@link #sink()} from their client-side tick and the impact packet
 * handler calls {@link #impact}; each loader's client setup installs the real sink, so a dedicated server never
 * loads a client class and simply keeps the no-op. Every call is guarded: a failing effect never breaks a tick.
 */
public final class EventFx {

    /** What the client does for each payload moment. */
    public interface Sink {
        void meteorTick(MeteorEntity meteor);

        void debrisTick(DebrisEntity debris);

        void podTick(SupplyPodEntity pod);

        void impact(ImpactFlashPayload payload);
    }

    private static final Sink NOOP = new Sink() {
        @Override
        public void meteorTick(MeteorEntity meteor) {
        }

        @Override
        public void debrisTick(DebrisEntity debris) {
        }

        @Override
        public void podTick(SupplyPodEntity pod) {
        }

        @Override
        public void impact(ImpactFlashPayload payload) {
        }
    };

    private static volatile Sink sink = NOOP;
    private static boolean failureLogged;

    private EventFx() {
    }

    /** Client setup: installs the client effects. */
    public static void install(Sink clientSink) {
        sink = clientSink == null ? NOOP : clientSink;
    }

    public static void meteorTick(MeteorEntity meteor) {
        try {
            sink.meteorTick(meteor);
        } catch (RuntimeException | LinkageError e) {
            logOnce(e);
        }
    }

    public static void debrisTick(DebrisEntity debris) {
        try {
            sink.debrisTick(debris);
        } catch (RuntimeException | LinkageError e) {
            logOnce(e);
        }
    }

    public static void podTick(SupplyPodEntity pod) {
        try {
            sink.podTick(pod);
        } catch (RuntimeException | LinkageError e) {
            logOnce(e);
        }
    }

    /** {@link ImpactFlashPayload} handler (runs on the client thread). */
    public static void impact(ImpactFlashPayload payload) {
        try {
            sink.impact(payload);
        } catch (RuntimeException | LinkageError e) {
            logOnce(e);
        }
    }

    private static void logOnce(Throwable e) {
        if (!failureLogged) {
            failureLogged = true;
            NeroEventsCommon.LOGGER.debug("[NeroEvents] payload effect failed ({})", e.getClass().getName());
        }
    }
}
