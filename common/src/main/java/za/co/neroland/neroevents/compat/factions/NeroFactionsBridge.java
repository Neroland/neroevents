package za.co.neroland.neroevents.compat.factions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.api.FactionView;
import za.co.neroland.nerofactions.api.NeroFactionsApi;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.nerofactions}, and it uses only NeroFactions'
 * public API ({@code za.co.neroland.nerofactions.api}, NeroFactions 0.7.0-beta.1 or later). NeroFactions is compiled
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
        // The API's map is immutable, and so is its key set.
        return NeroFactionsApi.factions(server).keySet();
    }

    @Override
    public Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
        // An immutable empty set when NeroFactions cannot answer.
        return NeroFactionsApi.membershipOf(server, player);
    }

    @Override
    public Optional<String> displayName(MinecraftServer server, Identifier faction) {
        if (faction == null) {
            return Optional.empty();
        }
        FactionView view = NeroFactionsApi.factions(server).get(faction);
        return view == null ? Optional.empty() : Optional.ofNullable(view.displayName());
    }

    @Override
    public int awardEvent(MinecraftServer server, UUID player, Identifier faction, int amount, Identifier reason) {
        return NeroFactionsApi.awardEvent(server, player, faction, amount, reason);
    }
}
