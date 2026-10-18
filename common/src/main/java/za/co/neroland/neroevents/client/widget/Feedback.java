package za.co.neroland.neroevents.client.widget;

import java.util.Objects;

import net.minecraft.network.chat.Component;

/**
 * One footer line: what the last action did. {@link Kind#OK} (filled square), {@link Kind#DENY} (cross) or
 * {@link Kind#HINT} (plain, {@code SUBTLE}) — the shape is always there, so colour is never the only signal.
 */
public record Feedback(Kind kind, Component text) {

    public enum Kind { OK, DENY, HINT }

    public Feedback {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    public static Feedback ok(Component text) {
        return new Feedback(Kind.OK, text);
    }

    public static Feedback deny(Component text) {
        return new Feedback(Kind.DENY, text);
    }

    public static Feedback hint(Component text) {
        return new Feedback(Kind.HINT, text);
    }
}
