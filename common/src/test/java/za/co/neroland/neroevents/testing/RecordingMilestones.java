package za.co.neroland.neroevents.testing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.progression.Milestones;

/** Records every quest-contract call (gates open once per player, like Core). */
public final class RecordingMilestones implements Milestones {

    public final List<String> calls = new ArrayList<>();
    public final Set<UUID> firstParticipation = new LinkedHashSet<>();
    public final Set<UUID> supplyClaimed = new LinkedHashSet<>();
    public final Set<UUID> raidRepelled = new LinkedHashSet<>();
    public final Set<UUID> bossContributor = new LinkedHashSet<>();
    public final List<Identifier> bossDefeated = new ArrayList<>();
    public final List<long[]> resolvedValues = new ArrayList<>();
    public final List<Identifier> resolvedScopes = new ArrayList<>();

    @Override
    public boolean recordFirstParticipation(UUID player) {
        calls.add("first_participation");
        return firstParticipation.add(player);
    }

    @Override
    public boolean recordSupplyClaimed(UUID player) {
        calls.add("supply_claimed");
        return supplyClaimed.add(player);
    }

    @Override
    public boolean recordRaidRepelled(UUID player) {
        calls.add("raid_repelled");
        return raidRepelled.add(player);
    }

    @Override
    public boolean recordBossContributor(UUID player) {
        calls.add("boss_contributor");
        return bossContributor.add(player);
    }

    @Override
    public boolean fireBossDefeated(Identifier bossDefinitionId) {
        calls.add("boss_defeated");
        return bossDefeated.add(bossDefinitionId);
    }

    @Override
    public boolean fireEventResolved(Identifier definitionId, long worldResolvedCount) {
        calls.add("event_resolved");
        resolvedScopes.add(definitionId);
        resolvedValues.add(new long[] {worldResolvedCount});
        return true;
    }
}
