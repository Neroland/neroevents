package za.co.neroland.neroevents.participation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroevents.protect.Footprint;

/**
 * The running-server {@link PresenceView} over the player list. The only class in the participation
 * package that touches {@link ServerPlayer}; a player's position is read inside {@link #isWithin} and never
 * leaves it. Server thread only.
 */
public final class ServerPresenceView implements PresenceView {

    private final MinecraftServer server;

    public ServerPresenceView(MinecraftServer server) {
        this.server = server;
    }

    private ServerPlayer player(UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    @Override
    public boolean isOnline(UUID player) {
        return player(player) != null;
    }

    @Override
    public boolean inDimension(UUID player, Identifier dimension) {
        ServerPlayer online = player(player);
        return online != null && online.level().dimension().identifier().equals(dimension);
    }

    @Override
    public boolean isWithin(UUID player, Footprint footprint) {
        ServerPlayer online = player(player);
        return online != null && online.level().dimension().identifier().equals(footprint.dimension())
                && footprint.contains(online.getBlockX(), online.getBlockZ());
    }

    @Override
    public Set<UUID> onlineIds() {
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            ids.add(online.getUUID());
        }
        return ids;
    }
}
