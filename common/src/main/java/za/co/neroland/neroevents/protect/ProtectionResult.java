package za.co.neroland.neroevents.protect;

import org.jetbrains.annotations.Nullable;

/**
 * The answer to "may this event affect that footprint?".
 *
 * @param allowed  whether every source abstained
 * @param deniedBy the id of the first source that refused ({@code null} when allowed)
 * @param detail   a short, non-identifying reason ({@code unloaded chunk}, a namespace, a zone name)
 */
public record ProtectionResult(boolean allowed, @Nullable String deniedBy, @Nullable String detail) {

    public static final ProtectionResult ALLOWED = new ProtectionResult(true, null, null);

    public static ProtectionResult denied(String source, @Nullable String detail) {
        return new ProtectionResult(false, source, detail);
    }
}
