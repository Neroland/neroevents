package za.co.neroland.neroevents.payload.meteor;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.fx.EventFx;
import za.co.neroland.neroevents.payload.PayloadSounds;
import za.co.neroland.neroevents.payload.registry.CorePayloadEntities;

/**
 * A meteor of a shower ({@code neroevents:meteor}): an AI-less entity flying at constant velocity (a streak,
 * not a drop). On its first collision — or below the world — it reports the impact to
 * {@link MeteorShowerPayload#onImpact} (which decides crater or soft landing through protection) and is
 * discarded. One that flies twice its expected fall time without touching anything is discarded and reported
 * as lost. The client renders tumble and trail from age and velocity.
 */
public class MeteorEntity extends Entity {

    private @Nullable UUID instanceId;
    private int index;
    private int maxAge = 400;
    private double vx;
    private double vy = -1.6D;
    private double vz;

    //? if <26.3 {
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    //?}

    @SuppressWarnings("this-escape")
    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** A configured, not yet added meteor (server). */
    static MeteorEntity create(ServerLevel level, UUID instance, int index, double x, double y, double z,
            double vx, double vy, double vz, int fallTicks) {
        MeteorEntity meteor = new MeteorEntity(CorePayloadEntities.METEOR.get(), level);
        meteor.setPos(x, y, z);
        meteor.instanceId = instance;
        meteor.index = index;
        meteor.vx = vx;
        meteor.vy = vy;
        meteor.vz = vz;
        meteor.maxAge = Math.max(40, fallTicks * 2);
        meteor.setDeltaMovement(vx, vy, vz);
        return meteor;
    }

    /**
     * A meteor that hangs still and tumbles in place, belonging to no event (the creative gallery). It never
     * hits anything, never expires and makes no whoosh; the gallery's clear removes it.
     */
    public static MeteorEntity display(ServerLevel level, double x, double y, double z) {
        MeteorEntity meteor = new MeteorEntity(CorePayloadEntities.METEOR.get(), level);
        meteor.setPos(x, y, z);
        meteor.vx = 0.0D;
        meteor.vy = 0.0D;
        meteor.vz = 0.0D;
        meteor.maxAge = Integer.MAX_VALUE;
        meteor.setDeltaMovement(0.0D, 0.0D, 0.0D);
        return meteor;
    }

    /**
     * A meteor that falls straight down at {@code vy} blocks per tick, belonging to no event (the creative
     * gallery's strike loop). It whooshes in flight and is discarded on its first collision without reaching any
     * impact hook; one that touches nothing is discarded after 400 ticks.
     */
    public static MeteorEntity displayFalling(ServerLevel level, double x, double y, double z, double vy) {
        MeteorEntity meteor = new MeteorEntity(CorePayloadEntities.METEOR.get(), level);
        meteor.setPos(x, y, z);
        meteor.vx = 0.0D;
        meteor.vy = vy;
        meteor.vz = 0.0D;
        meteor.maxAge = 400;
        meteor.setDeltaMovement(0.0D, vy, 0.0D);
        return meteor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data: the client renders from the tracked position, velocity and age.
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
            EventFx.meteorTick(this); // smoke and ember trail
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());
        if (tickCount % 20 == 1 && (vx != 0.0D || vy != 0.0D || vz != 0.0D)) {
            // The streak's whoosh, re-fired every second in flight.
            PayloadSounds.play(serverLevel, getX(), getY(), getZ(), PayloadSounds.METEOR_WHOOSH, 96.0F);
        }
        UUID instance = instanceId;
        if (horizontalCollision || verticalCollision || getY() < serverLevel.getMinY()) {
            if (instance != null) {
                MeteorShowerPayload.onImpact(serverLevel.getServer(), instance, index, impactBlock(serverLevel));
            }
            discard();
            return;
        }
        if (tickCount > maxAge) {
            if (instance != null) {
                MeteorShowerPayload.onLost(instance, index, getUUID());
            }
            discard();
        }
    }

    /** The ground block that was hit: the first non-air block at or just below the meteor. */
    private BlockPos impactBlock(ServerLevel level) {
        BlockPos pos = blockPosition();
        for (int i = 0; i < 4 && level.getBlockState(pos).isAir() && pos.getY() > level.getMinY(); i++) {
            pos = pos.below();
        }
        return pos;
    }

    /** The shower index (seeds the client tumble so a shower is not synchronous). */
    public int index() {
        return index;
    }

    /** The constant flight velocity (client: heat-pass scale and trail direction). */
    public Vec3 flightVelocity() {
        return new Vec3(vx, vy, vz);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false; // indestructible in flight
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        String instance = input.getStringOr("NeroEventsInstance", "");
        this.instanceId = instance.isEmpty() ? null : CraterPocketBlockEntity.parseUuid(instance);
        this.index = input.getIntOr("Index", 0);
        this.maxAge = input.getIntOr("MaxAge", 400);
        this.vx = input.getDoubleOr("VX", 0.0D);
        this.vy = input.getDoubleOr("VY", -1.6D);
        this.vz = input.getDoubleOr("VZ", 0.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        UUID instance = instanceId;
        if (instance != null) {
            output.putString("NeroEventsInstance", instance.toString());
        }
        output.putInt("Index", index);
        output.putInt("MaxAge", maxAge);
        output.putDouble("VX", vx);
        output.putDouble("VY", vy);
        output.putDouble("VZ", vz);
    }
}
