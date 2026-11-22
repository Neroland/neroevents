package za.co.neroland.neroevents.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * The quest contract as an interface, so the engine and the reward resolver fire it through one seam that
 * tests can record. Production delegates to {@link EventMilestones} (records need the running server;
 * channels do not).
 */
public interface Milestones {

    boolean recordFirstParticipation(UUID player);

    boolean recordSupplyClaimed(UUID player);

    boolean recordRaidRepelled(UUID player);

    boolean recordBossContributor(UUID player);

    /** A player present in a storm's dimension when it ran its course. Default: records nothing. */
    default boolean recordStormSurvived(UUID player) {
        return false;
    }

    /** A credited participant on the winning side of a faction war. Default: records nothing. */
    default boolean recordWarVictor(UUID player) {
        return false;
    }

    /** Scope = the boss event's definition id; falling. */
    boolean fireBossDefeated(Identifier bossDefinitionId);

    /** Scope = the definition id; value = the world's total of resolved events. */
    boolean fireEventResolved(Identifier definitionId, long worldResolvedCount);

    /** The real contract for {@code server} (record gates are no-ops without a server). */
    static Milestones forServer(@Nullable MinecraftServer server) {
        return new Milestones() {
            @Override
            public boolean recordFirstParticipation(UUID player) {
                return server != null && EventMilestones.recordFirstParticipation(server, player);
            }

            @Override
            public boolean recordSupplyClaimed(UUID player) {
                return server != null && EventMilestones.recordSupplyClaimed(server, player);
            }

            @Override
            public boolean recordRaidRepelled(UUID player) {
                return server != null && EventMilestones.recordRaidRepelled(server, player);
            }

            @Override
            public boolean recordBossContributor(UUID player) {
                return server != null && EventMilestones.recordBossContributor(server, player);
            }

            @Override
            public boolean recordStormSurvived(UUID player) {
                return server != null && EventMilestones.recordStormSurvived(server, player);
            }

            @Override
            public boolean recordWarVictor(UUID player) {
                return server != null && EventMilestones.recordWarVictor(server, player);
            }

            @Override
            public boolean fireBossDefeated(Identifier bossDefinitionId) {
                return EventMilestones.fireBossDefeated(bossDefinitionId);
            }

            @Override
            public boolean fireEventResolved(Identifier definitionId, long worldResolvedCount) {
                return EventMilestones.fireEventResolved(definitionId, worldResolvedCount);
            }
        };
    }
}
