package za.co.neroland.neroevents.event;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * The public, player-safe projection of an instance — the only view of an event anything outside the
 * engine gets (signals, HUD packets, the companion app). There is no participant list, no voter list and
 * no contribution share; {@code publicAnchor} is {@code null} unless the definition says
 * {@code location_public}.
 */
public record EventView(UUID instanceId, String shortId, Identifier definitionId, Identifier type,
        EventCategory category, Severity severity, String titleKey, String whatToDoKey, Identifier dimension,
        EventPhase phase, EventOrigin origin, long startsAt, long endsAt, long now, int participantCount,
        boolean votingOpen, int yes, int no, boolean locationPublic, @Nullable BlockPos publicAnchor,
        int progressDone, int progressTotal, @Nullable EventOutcome outcome, @Nullable String reason) {
}
