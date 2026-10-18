package za.co.neroland.neroevents.testing;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.participation.PresenceView;
import za.co.neroland.neroevents.protect.Footprint;

/** A scriptable presence: uuid → (online, dimension, inside the bounds). */
public final class FakePresenceView implements PresenceView {

    private record State(boolean online, Identifier dimension, boolean inside) {
    }

    private final Map<UUID, State> players = new HashMap<>();

    public FakePresenceView inside(UUID player) {
        players.put(player, new State(true, FakeWorldView.OVERWORLD, true));
        return this;
    }

    public FakePresenceView outside(UUID player) {
        players.put(player, new State(true, FakeWorldView.OVERWORLD, false));
        return this;
    }

    public FakePresenceView offline(UUID player) {
        players.put(player, new State(false, FakeWorldView.OVERWORLD, false));
        return this;
    }

    @Override
    public boolean isOnline(UUID player) {
        State s = players.get(player);
        return s != null && s.online();
    }

    @Override
    public boolean inDimension(UUID player, Identifier dimension) {
        State s = players.get(player);
        return s != null && s.online() && s.dimension().equals(dimension);
    }

    @Override
    public boolean isWithin(UUID player, Footprint footprint) {
        State s = players.get(player);
        return s != null && s.online() && s.inside();
    }

    @Override
    public Set<UUID> onlineIds() {
        Set<UUID> out = new LinkedHashSet<>();
        players.forEach((id, s) -> {
            if (s.online()) {
                out.add(id);
            }
        });
        return out;
    }
}
