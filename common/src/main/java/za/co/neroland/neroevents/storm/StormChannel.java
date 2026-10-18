package za.co.neroland.neroevents.storm;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The {@code neroevents:storm} channel on Neroland Core's {@link ThresholdEvents}: a rising crossing with
 * {@code value = intensity} when a storm starts, resumes or changes intensity, and a falling crossing
 * with {@code value = 0} when it ends. The scope is the dimension id: a place, never a player.
 *
 * <p>A listener that missed the end while the server was down sees a fresh start on resume.
 */
public final class StormChannel {

    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "storm");

    private static final Consumer<ThresholdEvents.ThresholdCrossing> CORE =
            crossing -> ThresholdEvents.fire(NeroEventsCommon.MOD_ID, crossing);

    private static volatile Consumer<ThresholdEvents.ThresholdCrossing> sink = CORE;

    private StormChannel() {
    }

    /** The storm in {@code dimension} started (or resumed, or changed intensity). */
    public static void start(Identifier dimension, int intensity) {
        fire(new ThresholdEvents.ThresholdCrossing(CHANNEL, dimension.toString(),
                StormVariant.clampIntensity(intensity), 1L, true));
    }

    /** The storm in {@code dimension} ended. */
    public static void end(Identifier dimension) {
        fire(new ThresholdEvents.ThresholdCrossing(CHANNEL, dimension.toString(), 0L, 1L, false));
    }

    private static void fire(ThresholdEvents.ThresholdCrossing crossing) {
        try {
            sink.accept(crossing);
        } catch (RuntimeException | LinkageError e) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] storm channel listener failed", e);
            NeroEventsTelemetry.captureHandledException(e, "payload:storm", "channel");
        }
    }

    /** Tests: route crossings to a recorder instead of Core ({@code null} restores Core). */
    public static void sinkForTests(Consumer<ThresholdEvents.ThresholdCrossing> recorder) {
        sink = recorder == null ? CORE : Objects.requireNonNull(recorder);
    }
}
