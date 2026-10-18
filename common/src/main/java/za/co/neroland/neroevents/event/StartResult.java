package za.co.neroland.neroevents.event;

import org.jetbrains.annotations.Nullable;

/**
 * What a payload's {@code onStart} decided: start, or skip with a short reason code
 * ({@code no_room}, {@code protected}, {@code no_sky}, ...).
 */
public record StartResult(boolean started, @Nullable String skipReason) {

    private static final StartResult OK = new StartResult(true, null);

    public static StartResult ok() {
        return OK;
    }

    public static StartResult skip(String reason) {
        return new StartResult(false, reason == null || reason.isBlank() ? "payload" : reason);
    }
}
