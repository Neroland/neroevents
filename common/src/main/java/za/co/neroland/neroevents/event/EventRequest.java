package za.co.neroland.neroevents.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * A request to start an event outside the scheduler: an operator's {@code /neroevents start}
 * ({@link EventOrigin#ADMIN}) or a gameplay request such as a player's own request item
 * ({@link EventOrigin#REQUEST}).
 *
 * @param definitionId    the definition to start
 * @param dimension       where
 * @param origin          {@code ADMIN} or {@code REQUEST}
 * @param requestedAnchor an explicit anchor (still protection-checked), or {@code null} for a density pick
 */
public record EventRequest(Identifier definitionId, Identifier dimension, EventOrigin origin,
        @Nullable BlockPos requestedAnchor) {

    public EventRequest {
        if (origin == EventOrigin.SCHEDULER) {
            throw new IllegalArgumentException("The scheduler does not use requests");
        }
    }
}
