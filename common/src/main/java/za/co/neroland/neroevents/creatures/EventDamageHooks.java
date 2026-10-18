package za.co.neroland.neroevents.creatures;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The common entry for every loader's living-damage event. Each loader entry point forwards its
 * after-damage event here with the damage actually taken (Fabric {@code ServerLivingEntityEvents.AFTER_DAMAGE}
 * when not blocked, NeoForge/Forge {@code LivingDamageEvent.Post} with {@code getNewDamage()}).
 *
 * <p>Cheap when the victim is not an event entity: one map lookup in {@link EventEntityIndex}. When it is,
 * the hit is dispatched to the wave and boss payloads, each of which looks the instance up in its own
 * {@code LIVE} map (a no-op when the instance is not one of its own) and queues a {@code PendingCredits.damage}.
 * A payload failure is contained here: a damage event must never crash because an event payload threw.
 */
public final class EventDamageHooks {

    private EventDamageHooks() {
    }

    /** Called after damage was applied. Server thread. Cheap when the victim is not an event entity (one map lookup). */
    public static void afterDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim == null || source == null || !(amount > 0.0F) || victim.level().isClientSide()) {
            return;
        }
        Optional<UUID> instance = EventEntityIndex.instanceOf(victim.getUUID());
        if (instance.isEmpty()) {
            return;
        }
        UUID instanceId = instance.get();
        try {
            WavePayloadBase.onDamage(instanceId, victim, source, amount);
        } catch (RuntimeException failure) {
            NeroEventsTelemetry.captureHandledException(failure, "payload:waves", "tick");
        }
        try {
            BossPayload.onDamage(instanceId, victim, source, amount);
        } catch (RuntimeException failure) {
            NeroEventsTelemetry.captureHandledException(failure, "payload:boss", "tick");
        }
    }
}
