package za.co.neroland.neroevents.testing.storm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.storm.StormOps;
import za.co.neroland.neroevents.storm.StormVariant;

/** A recording {@link StormOps}: scripted players, exposure and planets; records packets, effects, drains. */
public final class FakeStormOps implements StormOps {

    public record Sent(UUID player, CustomPacketPayload payload) {
    }

    public record Applied(UUID player, StormVariant variant, int intensity) {
    }

    public final Map<Identifier, Identifier> planets = new HashMap<>();
    public final Set<UUID> players = new LinkedHashSet<>();
    public final Set<UUID> exposedPlayers = new HashSet<>();
    public final List<Sent> sent = new ArrayList<>();
    public final List<Applied> applied = new ArrayList<>();
    public final EngineSettings settings = EngineSettings.defaults();
    public int drains;
    public boolean throwOnDrain;

    @Override
    public Optional<Identifier> planetOf(Identifier dimension) {
        return Optional.ofNullable(planets.get(dimension));
    }

    @Override
    public EngineSettings settings() {
        return settings;
    }

    @Override
    public List<UUID> players(PayloadContext ctx) {
        return List.copyOf(players);
    }

    @Override
    public boolean exposed(PayloadContext ctx, UUID player) {
        return exposedPlayers.contains(player);
    }

    @Override
    public void send(PayloadContext ctx, UUID player, CustomPacketPayload payload) {
        sent.add(new Sent(player, payload));
    }

    @Override
    public void applyEffects(PayloadContext ctx, UUID player, StormVariant variant, int intensity) {
        applied.add(new Applied(player, variant, intensity));
    }

    @Override
    public int drain(PayloadContext ctx, StormVariant variant, int intensity) {
        if (throwOnDrain) {
            throw new IllegalStateException("drain failed");
        }
        drains++;
        return 1;
    }

    /** Packets of one type sent to one player, in order. */
    public <T extends CustomPacketPayload> List<T> sentTo(UUID player, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Sent s : sent) {
            if (s.player().equals(player) && type.isInstance(s.payload())) {
                out.add(type.cast(s.payload()));
            }
        }
        return out;
    }

    public void clearRecords() {
        sent.clear();
        applied.clear();
        drains = 0;
    }
}
