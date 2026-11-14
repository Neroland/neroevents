package za.co.neroland.neroevents.compat.factions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * What NeroEvents needs from NeroFactions. A pure interface: it imports nothing from NeroFactions; the one
 * implementation that does is loaded by {@link java.util.ServiceLoader} only when NeroFactions is present,
 * and callers go through the façade, which catches anything a method throws.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface FactionsBridge {

    /** Every faction defined on this server ({@code NeroFactionsApi.factions(server).keySet()}). May throw. */
    Set<Identifier> factionIds(MinecraftServer server);

    /** The factions a player belongs to ({@code NeroFactionsApi.membershipOf}); empty when unknown. May throw. */
    Set<Identifier> membershipOf(MinecraftServer server, UUID player);

    /** A faction's display name ({@code FactionView.displayName()}); empty if unknown. May throw. */
    Optional<String> displayName(MinecraftServer server, Identifier faction);

    /**
     * Awards EVENT-source reputation ({@code NeroFactionsApi.awardEvent}) on the owning server thread. Returns the
     * applied amount: {@code 0} for an invalid call, an unknown faction, the wrong thread or server, or when the
     * player's daily EVENT cap is reached. <b>Not deduplicated</b>: the caller awards once per outcome. May throw.
     */
    int awardEvent(MinecraftServer server, UUID player, Identifier faction, int amount, Identifier reason);
}
