package za.co.neroland.neroevents.payload.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.neroevents.payload.registry.CorePayloadEntities;

/**
 * Client-only: the one entity → renderer table for the Core-only payloads, adapted onto each loader's
 * registration hook (NeoForge/Forge {@code RegisterRenderers}, Fabric {@code EntityRendererRegistry}). Models
 * are baked straight from their {@code createBodyLayer()}, so no model-layer registry is needed.
 */
public final class PayloadEntityRenderers {

    /** A loader's renderer-registration entry point. */
    public interface Sink {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    private PayloadEntityRenderers() {
    }

    public static void registerAll(Sink sink) {
        sink.register(CorePayloadEntities.SUPPLY_POD.get(), SupplyPodRenderer::new);
        sink.register(CorePayloadEntities.METEOR.get(), MeteorRenderer::new);
        sink.register(CorePayloadEntities.DEBRIS.get(), DebrisRenderer::new);
    }
}
