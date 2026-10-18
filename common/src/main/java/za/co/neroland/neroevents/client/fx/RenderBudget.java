package za.co.neroland.neroevents.client.fx;

import java.util.Locale;

/**
 * Per-frame counters for the payload renderers' budget (40 meteors + 10 pods + a storm in view): how many
 * meteors, pods, debris and war beacons were drawn and how many were culled, the time spent in those renderers,
 * the whole frame's time, and the payload particles spawned. Read with the client command {@code /nsevrender}.
 *
 * <p>Renderer contract (a few integer adds, render thread only): first line of {@code extractRenderState} calls
 * {@link #frame(long, float)} (a new game-time / partial-tick pair closes the previous frame), then
 * {@link #drawn(Kind)}; a {@code shouldRender} that answers false calls {@link #culled()}; {@code long t =
 * start()} ... {@code stop(t)} around extract and submit. The last {@value #WINDOW} frames are kept in a ring.
 * Pure Java, unit-testable; holds counts and timings only, never a player, name or position.
 */
public final class RenderBudget {

    /** What was drawn. */
    public enum Kind {
        METEOR, POD, DEBRIS, WAR_BEACON
    }

    /** Frames averaged. */
    public static final int WINDOW = 200;

    private static final int KINDS = Kind.values().length;
    private static final long[][] DRAWN = new long[KINDS][WINDOW];
    private static final long[] CULLED = new long[WINDOW];
    private static final long[] NANOS = new long[WINDOW];
    private static final long[] FRAME_NANOS = new long[WINDOW];
    private static final long[] PARTICLES = new long[WINDOW];
    private static int head;
    private static int filled;

    private static final long[] curDrawn = new long[KINDS];
    private static long curCulled;
    private static long curNanos;
    private static long curParticles;
    private static long frameGameTime = Long.MIN_VALUE;
    private static float framePartial = Float.NaN;
    private static boolean frameOpen;
    private static long frameStartedAt;
    private static int lastMeteors;

    /** The window average. */
    public record Snapshot(int frames, double meteors, double pods, double debris, double beacons, double culled,
            double rendererMillis, double frameMillis, double peakFrameMillis, double particlesPerFrame) {
    }

    private RenderBudget() {
    }

    /** Marks the frame the following counts belong to. */
    public static synchronized void frame(long gameTime, float partialTick) {
        if (frameOpen && gameTime == frameGameTime && Float.compare(partialTick, framePartial) == 0) {
            return;
        }
        long now = System.nanoTime();
        if (frameOpen) {
            closeFrame(now - frameStartedAt);
        }
        frameGameTime = gameTime;
        framePartial = partialTick;
        frameOpen = true;
        frameStartedAt = now;
    }

    /** Closes the open frame with an explicit frame time (tests; {@link #frame} measures it itself). */
    public static synchronized void closeFrame(long frameNanos) {
        if (!frameOpen) {
            return;
        }
        for (int k = 0; k < KINDS; k++) {
            DRAWN[k][head] = curDrawn[k];
            curDrawn[k] = 0;
        }
        lastMeteors = (int) DRAWN[Kind.METEOR.ordinal()][head];
        CULLED[head] = curCulled;
        NANOS[head] = curNanos;
        FRAME_NANOS[head] = Math.max(0L, frameNanos);
        PARTICLES[head] = curParticles;
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) {
            filled++;
        }
        curCulled = 0;
        curNanos = 0;
        curParticles = 0;
        frameOpen = false;
    }

    /** Opens a frame without a game time (tests). */
    public static synchronized void openFrame() {
        frameOpen = true;
        frameGameTime = Long.MIN_VALUE;
        framePartial = Float.NaN;
        frameStartedAt = System.nanoTime();
    }

    public static synchronized void drawn(Kind kind) {
        curDrawn[kind.ordinal()]++;
    }

    public static synchronized void culled() {
        curCulled++;
    }

    /** Payload particles spawned (counted into the frame that is open). */
    public static synchronized void particles(int count) {
        if (count > 0) {
            curParticles += count;
        }
    }

    public static long start() {
        return System.nanoTime();
    }

    public static synchronized void stop(long startNanos) {
        long d = System.nanoTime() - startNanos;
        if (d > 0) {
            curNanos += d;
        }
    }

    /** Adds explicit renderer nanoseconds (tests). */
    public static synchronized void nanos(long nanos) {
        if (nanos > 0) {
            curNanos += nanos;
        }
    }

    /** Meteors drawn in the last closed frame (the trail cap reads this). */
    public static synchronized int meteorsLastFrame() {
        return lastMeteors;
    }

    public static synchronized Snapshot average() {
        int n = filled;
        if (n == 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double[] drawn = new double[KINDS];
        long culled = 0;
        long nanos = 0;
        long frame = 0;
        long peak = 0;
        long particles = 0;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < KINDS; k++) {
                drawn[k] += DRAWN[k][i];
            }
            culled += CULLED[i];
            nanos += NANOS[i];
            frame += FRAME_NANOS[i];
            peak = Math.max(peak, FRAME_NANOS[i]);
            particles += PARTICLES[i];
        }
        return new Snapshot(n, drawn[0] / n, drawn[1] / n, drawn[2] / n, drawn[3] / n, (double) culled / n,
                nanos / 1.0e6 / n, frame / 1.0e6 / n, peak / 1.0e6, (double) particles / n);
    }

    /** The multi-line readout. */
    public static String record() {
        Snapshot a = average();
        return String.format(Locale.ROOT,
                "NeroEvents renderer stats over %d frames%n"
                        + "  drawn/frame: meteors %.1f, pods %.1f, debris %.1f, war beacons %.1f (culled %.1f)%n"
                        + "  payload renderer ms/frame: %.3f%n"
                        + "  frame ms: %.2f avg, %.2f peak (%.0f fps)%n"
                        + "  payload particles spawned/frame: %.1f",
                a.frames(), a.meteors(), a.pods(), a.debris(), a.beacons(), a.culled(), a.rendererMillis(),
                a.frameMillis(), a.peakFrameMillis(), a.frameMillis() <= 0 ? 0.0D : 1000.0D / a.frameMillis(),
                a.particlesPerFrame());
    }

    /** Clears everything. */
    public static synchronized void reset() {
        for (int i = 0; i < WINDOW; i++) {
            for (int k = 0; k < KINDS; k++) {
                DRAWN[k][i] = 0;
            }
            CULLED[i] = 0;
            NANOS[i] = 0;
            FRAME_NANOS[i] = 0;
            PARTICLES[i] = 0;
        }
        for (int k = 0; k < KINDS; k++) {
            curDrawn[k] = 0;
        }
        head = 0;
        filled = 0;
        curCulled = 0;
        curNanos = 0;
        curParticles = 0;
        frameGameTime = Long.MIN_VALUE;
        framePartial = Float.NaN;
        frameOpen = false;
        lastMeteors = 0;
    }
}
