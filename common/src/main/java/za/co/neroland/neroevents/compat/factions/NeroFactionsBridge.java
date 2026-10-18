package za.co.neroland.neroevents.compat.factions;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.membership.FactionMembership;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.nerofactions}. NeroFactions is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link FactionsCompat} only after {@code isModLoaded("nerofactions")}, and left out of a build with
 * {@code nerofactions_compat=false} (the default until NeroFactions is published). Takes the player id the caller
 * already holds and never logs it. Anything NeroFactions throws propagates; the façade's guard records it.
 */
public final class NeroFactionsBridge implements FactionsBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroFactionsBridge() {
    }

    @Override
    public Set<Identifier> factionIds(MinecraftServer server) {
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        return factions == null ? Set.of() : Set.copyOf(factions.keySet());
    }

    @Override
    public Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
        Set<Identifier> memberships = FactionMembership.membershipOf(server, player);
        return memberships == null ? Set.of() : Set.copyOf(memberships);
    }

    @Override
    public Optional<String> displayName(MinecraftServer server, Identifier faction) {
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        FactionDefinition definition = factions == null ? null : factions.get(faction);
        return definition == null ? Optional.empty() : Optional.ofNullable(definition.displayName());
    }
}
