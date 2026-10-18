package za.co.neroland.neroevents.testing.compat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroevents.compat.factions.FactionsBridge;

/** A NeroFactions stand-in with no factions; or one that fails like a sibling API change. */
public final class FakeFactionsBridge implements FactionsBridge {

    public boolean throwing;
    public int calls;

    private void call() {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("za/co/neroland/nerofactions/membership/FactionMembership");
        }
    }

    @Override
    public Set<Identifier> factionIds(MinecraftServer server) {
        call();
        return Set.of();
    }

    @Override
    public Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
        call();
        return Set.of();
    }

    @Override
    public Optional<String> displayName(MinecraftServer server, Identifier faction) {
        call();
        return Optional.empty();
    }
}
