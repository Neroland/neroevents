package za.co.neroland.neroevents.console;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.neroevents.command.GalleryDemo;

/**
 * The console's screen state: the severity of the most urgent announced or running event ({@link #NONE}
 * when nothing is), so the block's tint source and renderer can colour the screen. World data only — it
 * never holds a player, a vote or an event id. Updated once a second by the server ticker.
 */
public class EventConsoleBlockEntity extends BlockEntity {

    /** No event: the idle screen. */
    public static final byte NONE = -1;

    private static final String KEY_SEVERITY = "severity";
    /** Ticks between two screen refreshes. */
    static final int REFRESH_TICKS = 20;

    private byte severity = NONE;

    public EventConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ConsoleBlocks.EVENT_CONSOLE_BLOCK_ENTITY.get(), pos, state);
    }

    /** {@code Severity} ordinal of the most urgent event, or {@link #NONE}. */
    public byte severity() {
        return severity;
    }

    /** Once a second: mirror the engine's most urgent event onto the block state and the synced severity. */
    static void serverTick(Level level, BlockPos pos, BlockState state, EventConsoleBlockEntity console) {
        if (level.getGameTime() % REFRESH_TICKS != 0L) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        byte wanted = ConsoleServer.screenSeverity(server);
        if (wanted == NONE) {
            // Only the creative gallery's own consoles, while its demo runs; every other console stays idle.
            wanted = GalleryDemo.severityAt(level, pos);
        }
        boolean active = wanted != NONE;
        BlockState current = state;
        if (state.hasProperty(EventConsoleBlock.ACTIVE) && state.getValue(EventConsoleBlock.ACTIVE) != active) {
            current = state.setValue(EventConsoleBlock.ACTIVE, active);
            level.setBlock(pos, current, Block.UPDATE_ALL);
        }
        if (console.severity != wanted) {
            console.severity = wanted;
            console.setChanged();
            level.sendBlockUpdated(pos, current, current, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte(KEY_SEVERITY, severity);
        return tag;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putByte(KEY_SEVERITY, severity);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        byte loaded = input.getByteOr(KEY_SEVERITY, NONE);
        byte previous = this.severity;
        this.severity = loaded < NONE || loaded > 4 ? NONE : loaded;
        // The screen tint is baked into the chunk mesh: re-mesh this block when the severity changes client-side.
        Level level = this.level;
        if (previous != this.severity && level != null && level.isClientSide()) {
            BlockState state = getBlockState();
            level.setBlocksDirty(worldPosition, state, state);
        }
    }
}
