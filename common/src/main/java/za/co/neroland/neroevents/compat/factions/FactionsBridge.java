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

    /** Every faction defined on this server ({@code FactionDefinitions.factionsForServer}). May throw. */
    Set<Identifier> factionIds(MinecraftServer server);

    /** The factions a player belongs to ({@code FactionMembership.membershipOf}). May throw. */
    Set<Identifier> membershipOf(MinecraftServer server, UUID player);

    /** A faction's display name ({@code FactionDefinition.displayName()}); empty if unknown. May throw. */
    Optional<String> displayName(MinecraftServer server, Identifier faction);
}
