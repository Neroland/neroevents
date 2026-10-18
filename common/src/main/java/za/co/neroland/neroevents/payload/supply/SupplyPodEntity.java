package za.co.neroland.neroevents.payload.supply;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.menu.MenuOpener;
import za.co.neroland.neroevents.payload.LootRolls;
import za.co.neroland.neroevents.payload.PayloadSounds;
import za.co.neroland.neroevents.payload.registry.CorePayloadEntities;

/**
 * The supply pod ({@code neroevents:supply_pod}): a plain, AI-less, indestructible entity that descends at a
 * constant parachute speed with a slow drift, lands, rolls its loot once into a 27-slot container and opens as a
 * vanilla chest menu. All gameplay is server-side; the client renders from the synced position and the
 * {@code tier}, {@code landed} and {@code open} flags (canopy detach, hatch swing).
 *
 * <p>Carries the event instance id as a saved field ({@code NeroEventsInstance}) — Fabric has no persistent
 * data map, so a field plus NBT is the portable form of the "entities carry the instance id" rule. Never
 * records who opened it.
 */
public class SupplyPodEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(SupplyPodEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LANDED =
            SynchedEntityData.defineId(SupplyPodEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_OPEN =
            SynchedEntityData.defineId(SupplyPodEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int SLOTS = 27;
    private static final Component TITLE = Component.translatable("entity.neroevents.supply_pod");

    private @Nullable UUID instanceId;
    private double descent = 0.35D;
    private double driftX;
    private double driftZ;
    private @Nullable Identifier lootTable;
    private int lootRolls = 1;
    private long lootBudget;
    private long seedBase;
    private boolean lootRolled;
    private int descentTicks;
    private int maxDescentTicks = 600;
    private final PodContainer container = new PodContainer();
    /** Client only: the tick the landed flag was first seen (−1 = not landed), and the hatch's 0..1 swing. */
    private int clientLandedAt = -1;
    private float hatch;
    private float hatchPrev;

    //? if <26.3 {
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    //?}

    @SuppressWarnings("this-escape")
    public SupplyPodEntity(EntityType<? extends SupplyPodEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** A configured, not yet added pod (server). */
    static @Nullable SupplyPodEntity create(ServerLevel level, SupplyDropPayload.PodSpec spec) {
        SupplyPodEntity pod = new SupplyPodEntity(CorePayloadEntities.SUPPLY_POD.get(), level);
        pod.setPos(spec.x(), spec.y(), spec.z());
        pod.instanceId = spec.instanceId();
        pod.entityData.set(DATA_TIER, spec.tier());
        pod.descent = spec.descent();
        pod.driftX = spec.driftX();
        pod.driftZ = spec.driftZ();
        pod.lootTable = spec.lootTable();
        pod.lootRolls = spec.lootRolls();
        pod.lootBudget = spec.lootBudget();
        pod.seedBase = spec.seedBase();
        double height = Math.max(1.0D, spec.y() - level.getMinY());
        pod.maxDescentTicks = (int) Math.ceil((Math.min(height, 400.0D) + 64.0D) / Math.max(0.05D, spec.descent()));
        return pod;
    }

    /**
     * An empty pod belonging to no event (the creative gallery): already landed with its beacon lit, or
     * descending under its canopy from where it is placed. It holds nothing and is removed by the gallery's clear.
     */
    public static SupplyPodEntity display(ServerLevel level, double x, double y, double z, int tier, boolean landed) {
        SupplyPodEntity pod = new SupplyPodEntity(CorePayloadEntities.SUPPLY_POD.get(), level);
        pod.setPos(x, y, z);
        pod.entityData.set(DATA_TIER, Math.max(1, Math.min(3, tier)));
        pod.entityData.set(DATA_LANDED, landed);
        pod.lootRolled = true;
        pod.descent = 0.05D; // slow, so the screenshot catches it under its canopy
        return pod;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 1);
        builder.define(DATA_LANDED, false);
        builder.define(DATA_OPEN, false);
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

    public int tier() {
        return Math.max(1, Math.min(3, this.entityData.get(DATA_TIER)));
    }

    public boolean isLanded() {
        return this.entityData.get(DATA_LANDED);
    }

    public boolean isOpenForRender() {
        return this.entityData.get(DATA_OPEN);
    }

    /** Client: landing time and hatch swing (open over 10 ticks, close over 6). */
    private void clientAnimate() {
        if (isLanded() && clientLandedAt < 0) {
            // Seen landed on its first ticks (joined later): no detach animation.
            clientLandedAt = tickCount < 3 ? tickCount - 1000 : tickCount;
        }
        hatchPrev = hatch;
        hatch = isOpenForRender() ? Math.min(1.0F, hatch + 0.1F) : Math.max(0.0F, hatch - 1.0F / 6.0F);
    }

    /** Client: ticks since landing (partial), or −1 while descending. */
    public float ticksSinceLanded(float partialTick) {
        return clientLandedAt < 0 ? -1.0F : tickCount - clientLandedAt + partialTick;
    }

    /** Client: the hatch swing 0 (shut) .. 1 (open), interpolated. */
    public float hatchOpen(float partialTick) {
        return hatchPrev + (hatch - hatchPrev) * partialTick;
    }

    /** Whether the rolled contents are all taken (false before landing). */
    boolean contentsEmpty() {
        return lootRolled && container.isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            //? if <26.3 {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            }
            //?}
            clientAnimate();
            za.co.neroland.neroevents.fx.EventFx.podTick(this); // landing dust, beacon ring, reward sparks, descent loop
            return;
        }
        if (isLanded()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setDeltaMovement(driftX, -descent, driftZ);
        move(MoverType.SELF, getDeltaMovement());
        descentTicks++;
        if (onGround() || isInWater() || isInLava() || descentTicks > maxDescentTicks
                || getY() < level().getMinY()) {
            land();
        }
    }

    private void land() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_LANDED, true);
        rollLoot(serverLevel);
        PayloadSounds.playLayered(serverLevel, getX(), getY(), getZ(), PayloadSounds.SUPPLY_POD_LAND, 64.0F);
    }

    private void rollLoot(ServerLevel serverLevel) {
        if (lootRolled) {
            return;
        }
        lootRolled = true;
        Identifier table = lootTable;
        if (table == null) {
            return;
        }
        long seed = seedBase ^ getUUID().getLeastSignificantBits();
        List<ItemStack> stacks = SupplyDropPayload.rollPod(
                (t, rolls, s) -> LootRolls.roll(serverLevel, t, rolls, s, blockPosition()),
                LootRolls::valueOf, table, lootRolls, seed, lootBudget);
        for (int i = 0; i < stacks.size() && i < SLOTS; i++) {
            container.setItem(i, stacks.get(i));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (!isLanded()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = MenuOpener.openOrConsume(serverPlayer, new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, container), TITLE));
        UUID instance = instanceId;
        if (result == InteractionResult.SUCCESS && instance != null && level() instanceof ServerLevel serverLevel) {
            SupplyDropPayload.onPodOpened(serverLevel.getServer(), instance, getUUID(), serverPlayer);
        }
        return result;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false; // indestructible
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        String instance = input.getStringOr("NeroEventsInstance", "");
        this.instanceId = instance.isEmpty() ? null : parseUuid(instance);
        this.entityData.set(DATA_TIER, input.getIntOr("Tier", 1));
        this.entityData.set(DATA_LANDED, input.getBooleanOr("Landed", false));
        this.descent = input.getDoubleOr("Descent", 0.35D);
        this.driftX = input.getDoubleOr("DriftX", 0.0D);
        this.driftZ = input.getDoubleOr("DriftZ", 0.0D);
        String table = input.getStringOr("LootTable", "");
        this.lootTable = table.isEmpty() ? null : Identifier.tryParse(table);
        this.lootRolls = input.getIntOr("LootRolls", 1);
        this.lootBudget = input.getLongOr("LootBudget", 0L);
        this.seedBase = input.getLongOr("SeedBase", 0L);
        this.lootRolled = input.getBooleanOr("LootRolled", false);
        this.descentTicks = input.getIntOr("DescentTicks", 0);
        this.maxDescentTicks = input.getIntOr("MaxDescentTicks", 600);
        List<ItemStack> items = input.read("Items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        container.clearContent();
        for (int i = 0; i < items.size() && i < SLOTS; i++) {
            container.setItem(i, items.get(i));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        UUID instance = instanceId;
        if (instance != null) {
            output.putString("NeroEventsInstance", instance.toString());
        }
        output.putInt("Tier", tier());
        output.putBoolean("Landed", isLanded());
        output.putDouble("Descent", descent);
        output.putDouble("DriftX", driftX);
        output.putDouble("DriftZ", driftZ);
        Identifier table = lootTable;
        if (table != null) {
            output.putString("LootTable", table.toString());
        }
        output.putInt("LootRolls", lootRolls);
        output.putLong("LootBudget", lootBudget);
        output.putLong("SeedBase", seedBase);
        output.putBoolean("LootRolled", lootRolled);
        output.putInt("DescentTicks", descentTicks);
        output.putInt("MaxDescentTicks", maxDescentTicks);
        List<ItemStack> items = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            items.add(container.getItem(i));
        }
        output.store("Items", ItemStack.OPTIONAL_CODEC.listOf(), items);
    }

    private static @Nullable UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The pod's 27 slots; counts viewers (never who) for the hatch flag. */
    private final class PodContainer extends SimpleContainer {
        private int viewers;

        PodContainer() {
            super(SLOTS);
        }

        @Override
        public boolean stillValid(Player player) {
            return !SupplyPodEntity.this.isRemoved() && player.distanceToSqr(SupplyPodEntity.this) <= 64.0D;
        }

        @Override
        public void startOpen(ContainerUser user) {
            viewers++;
            if (viewers == 1 && SupplyPodEntity.this.level() instanceof ServerLevel serverLevel) {
                PayloadSounds.playLayered(serverLevel, SupplyPodEntity.this.getX(), SupplyPodEntity.this.getY(),
                        SupplyPodEntity.this.getZ(), PayloadSounds.SUPPLY_POD_OPEN, 16.0F);
            }
            SupplyPodEntity.this.entityData.set(DATA_OPEN, true);
        }

        @Override
        public void stopOpen(ContainerUser user) {
            viewers = Math.max(0, viewers - 1);
            SupplyPodEntity.this.entityData.set(DATA_OPEN, viewers > 0);
        }
    }
}
