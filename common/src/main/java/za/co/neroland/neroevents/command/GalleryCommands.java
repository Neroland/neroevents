package za.co.neroland.neroevents.command;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.command.GalleryLayout.Exhibit;
import za.co.neroland.neroevents.command.GalleryLayout.Offset;
import za.co.neroland.neroevents.console.ConsoleBlocks;
import za.co.neroland.neroevents.console.EventConsoleBlock;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;
import za.co.neroland.neroevents.payload.debris.SalvageCrateBlock;
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;
import za.co.neroland.neroevents.war.WarBlocks;

/**
 * The creative showcase: {@code /neroevents gallery} lays out every NeroEvents block, entity and effect north of
 * the invoking operator for screenshots, and {@code /neroevents gallery clear} takes it down again. Operator
 * level 2, creative mode and a player are all required; the floor plan is {@link GalleryLayout}. Meant for a
 * test world: it replaces the blocks in its footprint.
 *
 * <p>What it shows: the crater blocks, both Event Console states, the wreck blocks and a closed and an opened
 * salvage crate on the front row; three landed supply pods (tiers 1 to 3, beacons lit, rings rising) with a
 * fourth descending under its canopy, and a war beacon on the middle row; two meteors and a piece of debris
 * hanging over the back row, tumbling in place and trailing smoke and embers. The pods are empty and belong
 * to no event; nothing here is recorded anywhere. Every spawned entity carries {@value GalleryLayout#ENTITY_TAG}
 * so a clear removes only the gallery's own; building also sweeps loose dropped items within 64 blocks so the
 * frames are clean. A build first clears any earlier gallery at the same spot, so it never stacks.
 */
public final class GalleryCommands {

    private GalleryCommands() {
    }

    /** {@code /neroevents gallery}. */
    static int build(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        if (hasGalleryAt(level, origin)) {
            clearAt(level, origin);
        }
        buildFloor(level, origin);
        int placed = 0;
        for (Exhibit exhibit : GalleryLayout.BLOCKS) {
            BlockState state = blockFor(exhibit.id());
            if (state != null) {
                level.setBlockAndUpdate(at(origin, exhibit.at()), state);
                label(level, at(origin, exhibit.at()).above(2), exhibit.label());
                placed++;
            }
        }
        for (Exhibit exhibit : GalleryLayout.POD_ROW) {
            BlockPos pos = at(origin, exhibit.at());
            if ("war_beacon".equals(exhibit.id())) {
                level.setBlockAndUpdate(pos, WarBlocks.WAR_BEACON.get().defaultBlockState());
                label(level, pos.above(3), exhibit.label());
                placed++;
                continue;
            }
            int tier = exhibit.id().endsWith("t3") ? 3 : exhibit.id().endsWith("t2") ? 2 : 1;
            boolean landed = !exhibit.id().endsWith("descending");
            spawn(level, SupplyPodEntity.display(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, tier, landed));
            if (landed) {
                label(level, pos.above(3), exhibit.label());
            }
            placed++;
        }
        for (Exhibit exhibit : GalleryLayout.HANGING) {
            BlockPos pos = at(origin, exhibit.at());
            Entity entity = "debris".equals(exhibit.id())
                    ? DebrisEntity.display(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)
                    : MeteorEntity.display(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            spawn(level, entity);
            label(level, pos.above(3), exhibit.label());
            placed++;
        }
        sweepItems(level, origin);
        int count = placed;
        source.sendSuccess(() -> Component.translatable("command.neroevents.gallery.built", count), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /neroevents gallery clear}. */
    static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        if (!hasGalleryAt(level, origin)) {
            source.sendFailure(Component.translatable("command.neroevents.gallery.nothing_here"));
            return 0;
        }
        int[] result = clearAt(level, origin);
        source.sendSuccess(() -> Component.translatable("command.neroevents.gallery.cleared", result[0], result[1]),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static @Nullable ServerPlayer requireCreativePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neroevents.players_only"));
            return null;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.neroevents.gallery.creative_only"));
            return null;
        }
        return player;
    }

    private static BlockPos at(BlockPos origin, Offset offset) {
        return origin.offset(offset.dx(), offset.dy(), offset.dz());
    }

    private static boolean hasGalleryAt(ServerLevel level, BlockPos origin) {
        return level.getBlockState(at(origin, GalleryLayout.MARKER)).is(Blocks.CHISELED_DEEPSLATE);
    }

    private static @Nullable BlockState blockFor(String id) {
        BlockState state = switch (id) {
            case "meteor_slag" -> CorePayloadBlocks.METEOR_SLAG.get().defaultBlockState();
            case "cooled_crust" -> CorePayloadBlocks.COOLED_CRUST.get().defaultBlockState();
            case "embedded_meteor_ore" -> CorePayloadBlocks.EMBEDDED_METEOR_ORE.get().defaultBlockState();
            case "crater_pocket" -> CorePayloadBlocks.CRATER_POCKET.get().defaultBlockState();
            case "hull_plating" -> CorePayloadBlocks.HULL_PLATING.get().defaultBlockState();
            case "scorched_panel" -> CorePayloadBlocks.SCORCHED_PANEL.get().defaultBlockState();
            case "bent_strut" -> CorePayloadBlocks.BENT_STRUT.get().defaultBlockState();
            case "salvage_crate" -> CorePayloadBlocks.SALVAGE_CRATE.get().defaultBlockState();
            case "salvage_crate_open" -> CorePayloadBlocks.SALVAGE_CRATE.get().defaultBlockState()
                    .setValue(SalvageCrateBlock.OPEN, true);
            case "event_console" -> ConsoleBlocks.EVENT_CONSOLE.get().defaultBlockState();
            case "event_console_active" -> ConsoleBlocks.EVENT_CONSOLE.get().defaultBlockState()
                    .setValue(EventConsoleBlock.ACTIVE, true);
            default -> null;
        };
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH); // facing the camera
        }
        return state;
    }

    private static void buildFloor(ServerLevel level, BlockPos origin) {
        BlockState body = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState border = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
            for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                boolean edge = dx == GalleryLayout.MIN_DX || dx == GalleryLayout.MAX_DX || dz == GalleryLayout.MIN_DZ
                        || dz == GalleryLayout.MAX_DZ;
                BlockState state = edge ? border : GalleryLayout.lamp(dx, dz) ? lamp : body;
                level.setBlockAndUpdate(origin.offset(dx, 0, dz), state);
                for (int dy = 1; dy <= GalleryLayout.CLEAR_DY; dy++) {
                    BlockPos above = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(above).isAir()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        level.setBlockAndUpdate(at(origin, GalleryLayout.MARKER), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
    }

    /** Wipes the footprint to air (top down) and removes the gallery's entities: {blocks, entities}. */
    private static int[] clearAt(ServerLevel level, BlockPos origin) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (int dy = GalleryLayout.CLEAR_DY; dy >= 0; dy--) {
            for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
                for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS); // notify clients, no neighbour cascade
                        cleared++;
                    }
                }
            }
        }
        AABB bounds = new AABB(origin.getX() + GalleryLayout.MIN_DX, origin.getY() - 2,
                origin.getZ() + GalleryLayout.MIN_DZ, origin.getX() + GalleryLayout.MAX_DX + 1,
                origin.getY() + GalleryLayout.CLEAR_DY + 4, origin.getZ() + GalleryLayout.MAX_DZ + 1);
        int removed = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds,
                e -> e.entityTags().contains(GalleryLayout.ENTITY_TAG))) {
            entity.discard();
            removed++;
        }
        return new int[] {cleared, removed};
    }

    private static void sweepItems(ServerLevel level, BlockPos origin) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(64.0D))) {
            item.discard();
        }
    }

    private static void spawn(ServerLevel level, Entity entity) {
        entity.addTag(GalleryLayout.ENTITY_TAG);
        level.addFreshEntity(entity);
    }

    private static void label(ServerLevel level, BlockPos pos, String text) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Marker = zero-size bounding box, so the stand never hijacks a click on the exhibit. ArmorStand#setMarker
        // is private in 26.x, so the flag goes in the only public way: Entity#load.
        CompoundTag markerTag = new CompoundTag();
        markerTag.putBoolean("Marker", true);
        stand.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), markerTag));
        stand.refreshDimensions();
        stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); // load() zeroed the position
        stand.setCustomName(Component.literal(text));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        spawn(level, stand);
    }
}
