package za.co.neroland.neroevents.event;

import org.jetbrains.annotations.Nullable;

/** What a payload's {@code onResume} decided after a restart: carry on, or cancel with a reason. */
public record ResumeResult(boolean resumed, @Nullable String cancelReason) {

    private static final ResumeResult OK = new ResumeResult(true, null);

    public static ResumeResult ok() {
        return OK;
    }

    public static ResumeResult refuse(String reason) {
        return new ResumeResult(false, reason == null || reason.isBlank() ? "resume_failed" : reason);
    }
}
