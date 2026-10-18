package za.co.neroland.neroevents.compat;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The health of one sibling integration: whether its bridge has failed this session, with the one WARN line and
 * the one telemetry report that go with the first failure. Shared by every compat façade so they all fail the
 * same way. Logs carry the mod id and the exception class only — never a player, a position or a message.
 */
public final class CompatHealth {

    private final String modId;
    private volatile boolean failing;
    private final AtomicBoolean warned = new AtomicBoolean();

    public CompatHealth(String modId) {
        this.modId = modId;
    }

    /** Whether the bridge has thrown at least once since the last {@link #reset()}. */
    public boolean failing() {
        return failing;
    }

    /** Records a bridge failure: the integration is off for the session; warned and reported once. */
    public void fail(Throwable failure) {
        failing = true;
        if (warned.compareAndSet(false, true)) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] The {} integration failed ({}); it is off for this session.",
                    modId, failure.getClass().getName());
            NeroEventsTelemetry.captureHandledException(failure, "compat:" + modId, "bridge");
        }
    }

    /** Healthy again (tests; a façade re-initialised from scratch). */
    public void reset() {
        failing = false;
        warned.set(false);
    }

    /**
     * Finds the one implementation of {@code type} in this jar through {@link ServiceLoader}, or {@code null} when
     * the build left it out ({@code <id>_compat=false}) or loading it failed (warned and reported once).
     */
    public <B> @Nullable B loadBridge(Class<B> type) {
        try {
            return ServiceLoader.load(type, type.getClassLoader()).findFirst().orElse(null);
        } catch (ServiceConfigurationError | RuntimeException | LinkageError failure) {
            fail(failure);
            return null;
        }
    }

    /** The INFO line for a sibling that is not installed. */
    public void logAbsent(String unavailable) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] {} not present — {} unavailable", modId, unavailable);
    }

    /** The INFO line for a sibling that is installed while this jar carries no bridge for it. */
    public void logNoBridge() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] {} present but no bridge in this jar (built with {}_compat=false)",
                modId, modId);
    }

    /** The INFO line for a sibling that is hooked. */
    public void logHooked(String enabled) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] {} found — {} enabled", modId, enabled);
    }
}
