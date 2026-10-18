package za.co.neroland.neroevents.event;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.ProtectionQuery;

/**
 * The running-server {@link PayloadContext}: the pure context plus the level and server. Payloads that
 * spawn entities or place blocks use {@code if (ctx instanceof ServerPayloadContext s)}; every such effect
 * still goes through {@link #protection()} first.
 */
public final class ServerPayloadContext extends EnginePayloadContext {

    private final MinecraftServer server;

    public ServerPayloadContext(MinecraftServer server, EventInstance instance, WorldView world,
            ProtectionQuery protection, EngineClock clock) {
        super(instance, world, protection, clock);
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    /** The instance's level, or {@code null} if that dimension is not loaded. */
    public @Nullable ServerLevel level() {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension()));
    }
}
