package za.co.neroland.neroevents.payload.debris;

import java.util.UUID;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.payload.registry.CorePayloadEntities;

/**
 * A falling hull fragment ({@code neroevents:debris}): flies at constant velocity like the meteor and, on its
 * first collision, hands the landing spot to {@link DebrisCrashPayload#onLanded} (which lays the wreck through
 * protection) and is discarded. Lost below the world or after twice its fall time: reported as lost.
 */
public class DebrisEntity extends Entity {

    private @Nullable UUID instanceId;
    private int maxAge = 400;
    private double vx;
    private double vy = -1.2D;
    private double vz;

    //? if <26.3 {
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    //?}

    @SuppressWarnings("this-escape")
    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    static DebrisEntity create(ServerLevel level, UUID instance, double x, double y, double z, double vx, double vy,
            double vz, int fallTicks) {
        DebrisEntity debris = new DebrisEntity(CorePayloadEntities.DEBRIS.get(), level);
        debris.setPos(x, y, z);
        debris.instanceId = instance;
        debris.vx = vx;
        debris.vy = vy;
        debris.vz = vz;
        debris.maxAge = Math.max(40, fallTicks * 2);
        debris.setDeltaMovement(vx, vy, vz);
        return debris;
    }

    /** Debris that hangs still and tumbles in place, belonging to no event (the creative gallery). */
    public static DebrisEntity display(ServerLevel level, double x, double y, double z) {
        DebrisEntity debris = new DebrisEntity(CorePayloadEntities.DEBRIS.get(), level);
        debris.setPos(x, y, z);
        debris.vx = 0.0D;
        debris.vy = 0.0D;
        debris.vz = 0.0D;
        debris.maxAge = Integer.MAX_VALUE;
        debris.setDeltaMovement(0.0D, 0.0D, 0.0D);
        return debris;
    }

    /**
     * Debris that falls straight down at {@code vy} blocks per tick, belonging to no event (the creative gallery's
     * crash loop): discarded on its first collision without reaching any landing hook, or after 400 ticks.
     */
    public static DebrisEntity displayFalling(ServerLevel level, double x, double y, double z, double vy) {
        DebrisEntity debris = new DebrisEntity(CorePayloadEntities.DEBRIS.get(), level);
        debris.setPos(x, y, z);
        debris.vx = 0.0D;
        debris.vy = vy;
        debris.vz = 0.0D;
        debris.maxAge = 400;
        debris.setDeltaMovement(0.0D, vy, 0.0D);
        return debris;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data: the client tumbles it on age.
    }

    //? if >=26.3 {
    /*@Override
    protected InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this);
    }
    *///?} else {
    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }
    //?}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            //? if <26.3 {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            }
            //?}
            za.co.neroland.neroevents.fx.EventFx.debrisTick(this); // smoke trail
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());
        UUID instance = instanceId;
        if (getY() < serverLevel.getMinY() || tickCount > maxAge) {
            if (instance != null) {
                DebrisCrashPayload.onLost(instance);
            }
            discard();
            return;
        }
        if (horizontalCollision || verticalCollision) {
            if (instance != null) {
                DebrisCrashPayload.onLanded(serverLevel.getServer(), instance, blockPosition());
            }
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        String instance = input.getStringOr("NeroEventsInstance", "");
        UUID parsed = null;
        if (!instance.isEmpty()) {
            try {
                parsed = UUID.fromString(instance);
            } catch (IllegalArgumentException e) {
                parsed = null;
            }
        }
        this.instanceId = parsed;
        this.maxAge = input.getIntOr("MaxAge", 400);
        this.vx = input.getDoubleOr("VX", 0.0D);
        this.vy = input.getDoubleOr("VY", -1.2D);
        this.vz = input.getDoubleOr("VZ", 0.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        UUID instance = instanceId;
        if (instance != null) {
            output.putString("NeroEventsInstance", instance.toString());
        }
        output.putInt("MaxAge", maxAge);
        output.putDouble("VX", vx);
        output.putDouble("VY", vy);
        output.putDouble("VZ", vz);
    }
}
