package za.co.neroland.neroevents.event;

/**
 * Engine timing, exposed in microseconds by {@code /neroevents status}. Measured, never throttled:
 * throttling would hide the problem.
 */
public final class EngineMetrics {

    private static final double ALPHA = 0.05D;

    private long tickStart;
    private long passStart;
    private long lastTickNanos;
    private long maxTickNanos;
    private double ewmaTickNanos;
    private long lastPassNanos;
    private long maxPassNanos;
    private long passes;
    private long lastAnchorNanos;

    void beginTick() {
        tickStart = System.nanoTime();
    }

    void endTick() {
        lastTickNanos = System.nanoTime() - tickStart;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
        ewmaTickNanos = ewmaTickNanos == 0.0D ? lastTickNanos : ewmaTickNanos + ALPHA * (lastTickNanos - ewmaTickNanos);
    }

    void beginPass() {
        passStart = System.nanoTime();
    }

    /** @return the pass duration in nanoseconds */
    long endPass() {
        lastPassNanos = System.nanoTime() - passStart;
        maxPassNanos = Math.max(maxPassNanos, lastPassNanos);
        passes++;
        return lastPassNanos;
    }

    void anchorNanos(long nanos) {
        lastAnchorNanos = nanos;
    }

    public long lastTickMicros() {
        return lastTickNanos / 1000L;
    }

    public long maxTickMicros() {
        return maxTickNanos / 1000L;
    }

    public long ewmaTickMicros() {
        return Math.round(ewmaTickNanos / 1000.0D);
    }

    public long lastPassMicros() {
        return lastPassNanos / 1000L;
    }

    public long maxPassMicros() {
        return maxPassNanos / 1000L;
    }

    public long passes() {
        return passes;
    }

    public long lastAnchorMicros() {
        return lastAnchorNanos / 1000L;
    }
}
