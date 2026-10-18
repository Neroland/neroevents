package za.co.neroland.neroevents.compat;

/**
 * Whether a mod is loaded. Every compat façade asks this before it looks for its bridge, so tests can
 * inject a fake presence map instead of a real loader ({@code Services.PLATFORM::isModLoaded} in game).
 */
@FunctionalInterface
public interface ModPresence {

    /** Whether the mod with this id is loaded. */
    boolean isLoaded(String modId);
}
