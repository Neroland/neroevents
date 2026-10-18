package za.co.neroland.neroevents.war;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * A war beacon's block entity. Saved: the instance it belongs to, its objective index and the visible state.
 * Synced to clients (renderer only, never the instance id): {@code owner} (−1 none, 0 side A, 1 side B),
 * {@code progressTicks}, {@code captureTicks}, {@code contested}, {@code colourA}, {@code colourB},
 * {@code captureRadius} (the ground ring, default 6) and {@code progressSide} (−1 none, 0 A, 1 B: the colour of
 * the sweep). The faction war payload drives every field from its tick; a beacon without a live instance is
 * decoration. Nothing here names a player.
 */
public class WarBeaconBlockEntity extends BlockEntity {

    public static final byte NONE = -1;
    public static final byte SIDE_A = 0;
    public static final byte SIDE_B = 1;
    public static final int DEFAULT_CAPTURE_RADIUS = 6;
    public static final int DEFAULT_CAPTURE_TICKS = 600;

    private static final String TAG_INSTANCE = "NeroEventsInstance";
    private static final String TAG_INDEX = "index";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_PROGRESS = "progressTicks";
    private static final String KEY_CAPTURE = "captureTicks";
    private static final String KEY_CONTESTED = "contested";
    private static final String KEY_COLOUR_A = "colourA";
    private static final String KEY_COLOUR_B = "colourB";
    private static final String KEY_RADIUS = "captureRadius";
    private static final String KEY_PROGRESS_SIDE = "progressSide";

    private @Nullable UUID instanceId;
    private int index;
    private byte owner = NONE;
    private int progressTicks;
    private int captureTicks = DEFAULT_CAPTURE_TICKS;
    private boolean contested;
    private int colourA = WarColours.UNOWNED;
    private int colourB = WarColours.UNOWNED;
    private int captureRadius = DEFAULT_CAPTURE_RADIUS;
    private byte progressSide = NONE;

    /** Client only: the emitter ring's accumulated yaw (continuous across rate switches). */
    private float ringYaw;
    private long ringYawAt = Long.MIN_VALUE;

    public WarBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(WarBlocks.WAR_BEACON_BLOCK_ENTITY.get(), pos, state);
    }

    // --- server: binding and sync ---------------------------------------------------------------

    /** Binds this beacon to an instance (server, at placement); {@code null} binds it to none (a display). */
    public void bind(@Nullable UUID instance, int objectiveIndex, int colourOfA, int colourOfB, int ticksToCapture,
            int radius) {
        this.instanceId = instance;
        this.index = objectiveIndex;
        this.colourA = colourOfA;
        this.colourB = colourOfB;
        this.captureTicks = Math.max(1, ticksToCapture);
        this.captureRadius = Math.max(0, Math.min(127, radius));
        this.owner = NONE;
        this.progressTicks = 0;
        this.contested = false;
        this.progressSide = NONE;
        changed();
    }

    /** Pushes the objective's visible state; sends a block update only when something changed. */
    public void sync(int newOwner, int progress, boolean isContested, int newProgressSide) {
        byte o = (byte) Math.max(NONE, Math.min(SIDE_B, newOwner));
        byte ps = (byte) Math.max(NONE, Math.min(SIDE_B, newProgressSide));
        int p = Math.max(0, progress);
        if (o == owner && p == progressTicks && isContested == contested && ps == progressSide) {
            return;
        }
        owner = o;
        progressTicks = p;
        contested = isContested;
        progressSide = ps;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable UUID instanceId() {
        return instanceId;
    }

    public int index() {
        return index;
    }

    // --- renderer surface ----------------------------------------------------------------------

    public byte owner() {
        return owner;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public int captureTicks() {
        return captureTicks;
    }

    public boolean contested() {
        return contested;
    }

    public int colourA() {
        return colourA;
    }

    public int colourB() {
        return colourB;
    }

    public int captureRadius() {
        return captureRadius;
    }

    public byte progressSide() {
        return progressSide;
    }

    /** Client: advances and returns the emitter ring yaw (2°/t idle, 6°/t contested), kept continuous. */
    public float advanceRingYaw(float time) {
        long whole = (long) Math.floor(time);
        if (ringYawAt == Long.MIN_VALUE || whole < ringYawAt || whole - ringYawAt > 200L) {
            ringYawAt = whole;
        }
        float rate = contested ? 6.0F : 2.0F;
        if (whole > ringYawAt) {
            ringYaw = (ringYaw + rate * (whole - ringYawAt)) % 360.0F;
            ringYawAt = whole;
        }
        return ringYaw + rate * (time - whole);
    }

    // --- persistence and sync ------------------------------------------------------------------

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only what the renderer needs: never the instance id. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeVisible(tag);
        return tag;
    }

    private void writeVisible(CompoundTag tag) {
        tag.putByte(KEY_OWNER, owner);
        tag.putInt(KEY_PROGRESS, progressTicks);
        tag.putInt(KEY_CAPTURE, captureTicks);
        tag.putBoolean(KEY_CONTESTED, contested);
        tag.putInt(KEY_COLOUR_A, colourA);
        tag.putInt(KEY_COLOUR_B, colourB);
        tag.putByte(KEY_RADIUS, (byte) captureRadius);
        tag.putByte(KEY_PROGRESS_SIDE, progressSide);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (instanceId != null) {
            output.putString(TAG_INSTANCE, instanceId.toString());
        }
        output.putInt(TAG_INDEX, index);
        output.putByte(KEY_OWNER, owner);
        output.putInt(KEY_PROGRESS, progressTicks);
        output.putInt(KEY_CAPTURE, captureTicks);
        output.putBoolean(KEY_CONTESTED, contested);
        output.putInt(KEY_COLOUR_A, colourA);
        output.putInt(KEY_COLOUR_B, colourB);
        output.putByte(KEY_RADIUS, (byte) captureRadius);
        output.putByte(KEY_PROGRESS_SIDE, progressSide);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr(TAG_INSTANCE, "");
        try {
            this.instanceId = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            this.instanceId = null;
        }
        this.index = input.getIntOr(TAG_INDEX, 0);
        this.owner = clampSide(input.getByteOr(KEY_OWNER, NONE));
        this.progressTicks = Math.max(0, input.getIntOr(KEY_PROGRESS, 0));
        this.captureTicks = Math.max(1, input.getIntOr(KEY_CAPTURE, DEFAULT_CAPTURE_TICKS));
        this.contested = input.getBooleanOr(KEY_CONTESTED, false);
        this.colourA = input.getIntOr(KEY_COLOUR_A, WarColours.UNOWNED);
        this.colourB = input.getIntOr(KEY_COLOUR_B, WarColours.UNOWNED);
        this.captureRadius = Math.max(0, input.getByteOr(KEY_RADIUS, (byte) DEFAULT_CAPTURE_RADIUS));
        this.progressSide = clampSide(input.getByteOr(KEY_PROGRESS_SIDE, NONE));
    }

    private static byte clampSide(byte value) {
        return value < NONE || value > SIDE_B ? NONE : value;
    }
}
