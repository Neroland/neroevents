package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.command.GalleryCommands.Mode;
import za.co.neroland.neroevents.command.GalleryLayout.Offset;
import za.co.neroland.neroevents.command.GalleryStations.DecayCell;
import za.co.neroland.neroevents.command.GalleryStations.Station;
import za.co.neroland.neroevents.command.GalleryStations.YardWreck;
import za.co.neroland.neroevents.compat.CompatRegistry;
import za.co.neroland.neroevents.console.ConsoleBlocks;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.ConsoleServer;
import za.co.neroland.neroevents.console.EventConsoleBlock;
import za.co.neroland.neroevents.console.EventConsoleMenu;
import za.co.neroland.neroevents.event.Severity;
import za.co.neroland.neroevents.menu.MenuOpener;
import za.co.neroland.neroevents.payload.PayloadSounds;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;
import za.co.neroland.neroevents.payload.debris.WreckLayout;
import za.co.neroland.neroevents.payload.debris.WreckLayouts;
import za.co.neroland.neroevents.payload.debris.WreckPlacer;
import za.co.neroland.neroevents.payload.meteor.CraterBuilder;
import za.co.neroland.neroevents.payload.meteor.CraterPlan;
import za.co.neroland.neroevents.payload.meteor.CraterPocketBlockEntity;
import za.co.neroland.neroevents.payload.meteor.ImpactFlashPayload;
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.storm.ActiveStorms;
import za.co.neroland.neroevents.storm.StormExposurePayload;
import za.co.neroland.neroevents.storm.StormSyncPayload;
import za.co.neroland.neroevents.storm.StormVariant;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;
import za.co.neroland.neroevents.war.WarBeaconBlockEntity;
import za.co.neroland.neroevents.war.WarBlocks;
import za.co.neroland.neroevents.war.WarColours;

/**
 * The gallery's event stations and its demo session: builds one station per event type (see
 * {@link GalleryStations}) and, while the invoking operator stays in the dimension, runs each station's loop —
 * a pod landing every 20 s, a meteor striking a pre-dug crater every 5 s, debris crashing onto a wreck every
 * 6 s, a war beacon being captured and recaptured — plus a storm preview and a demo HUD/console sent to that
 * operator only.
 *
 * <p>Display only, by construction: pods, meteors and debris carry no event instance (so no impact, landing or
 * opening hook is ever reached), the crater pocket and the salvage crates have no loot table, the creature and
 * boss bays are labels only (no mob is spawned), and nothing here touches the engine's state, rewards, history,
 * budgets, gates or another mod's API. Everything is placed inside the footprint the build already cleared with
 * the protection rules. One session per server, held in memory only: the owner's UUID lives here while the
 * session does and is dropped on {@code gallery clear}, when the owner logs out or changes dimension, and when
 * the server stops. It is never saved or logged. Server thread only.
 */
public final class GalleryShowcase {

    static final int POD_PERIOD = 400;
    static final int POD_DROP_DY = 14;
    static final int POD_CUE_DY = 9;
    static final int METEOR_REST = 70;
    static final int DEBRIS_REST = 90;
    static final int FALL_TIMEOUT = 80;
    static final int STORM_SWITCH_TICKS = 300;
    static final int STORM_RESEND_TICKS = 100;
    static final int STORM_INTENSITY = 3;
    static final int STORM_RAMP = 40;
    static final int STORM_WIND_YAW = 300;
    static final StormVariant[] STORM_CYCLE = {StormVariant.SOLAR, StormVariant.DUST, StormVariant.ION,
            StormVariant.CRYO};

    /** What a station build placed: the station count, the siblings a real event would need, the display mobs. */
    record BuildReport(int stations, List<String> missingMods, List<UUID> mobs) {
    }

    /** The live demo session (one per server). */
    static final class Session {
        final @Nullable MinecraftServer server;
        final UUID owner;
        final Identifier dimension;
        final BlockPos origin;
        final Mode mode;
        long tick;
        @Nullable UUID pod;
        int podTier = 1;
        long podNextAt;
        @Nullable UUID meteor;
        long meteorNextAt;
        long meteorSpawnedAt;
        final BlockPos meteorImpact;
        @Nullable UUID debris;
        long debrisNextAt;
        long debrisSpawnedAt;
        final BlockPos debrisImpact;
        long warT;
        @Nullable StormVariant stormPinned;
        boolean stormCycling = true;
        int stormIndex;
        long stormChangedAt;
        long stormSentAt = Long.MIN_VALUE / 2;
        @Nullable StormVariant stormSent;
        int hudScene;
        long hudSceneStartedAt;
        boolean yieldedNoticeSent;
        boolean failureReported;
        final List<UUID> mobs = new ArrayList<>();

        /** A session with no server (tests). */
        Session(UUID owner, Identifier dimension, BlockPos origin) {
            this(null, owner, dimension, origin, Mode.ALL);
        }

        Session(@Nullable MinecraftServer server, UUID owner, Identifier dimension, BlockPos origin, Mode mode) {
            this.server = server;
            this.owner = owner;
            this.dimension = dimension;
            this.origin = origin.immutable();
            this.mode = mode;
            this.meteorImpact = GalleryStations.METEOR_SHOWER.centre().at(this.origin);
            this.debrisImpact = GalleryStations.DEBRIS_IMPACT.at(this.origin);
        }

        /** Whether {@code player} in {@code playerDimension} is this session's owner, there. */
        boolean matches(UUID player, Identifier playerDimension) {
            return owner.equals(player) && dimension.equals(playerDimension);
        }

        /** Whether {@code pos} in {@code levelDimension} lies inside this gallery's clear box. */
        boolean covers(Identifier levelDimension, BlockPos pos) {
            if (!dimension.equals(levelDimension)) {
                return false;
            }
            return GalleryLayout.CLEAR_BOX.contains(new Offset(pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ()));
        }

        long sceneAge() {
            return Math.max(0L, tick - hudSceneStartedAt);
        }
    }

    private static volatile @Nullable Session session;

    private GalleryShowcase() {
    }

    static @Nullable Session session() {
        return session;
    }

    /** Tests only. */
    static void setSessionForTests(@Nullable Session value) {
        session = value;
    }

    // --- build -----------------------------------------------------------------------------------------------

    /** Builds every station (floor surfaces, blocks, display entities, labels). */
    static BuildReport buildStations(ServerLevel level, BlockPos origin, ProtectionEvaluation evaluation) {
        Identifier dimension = level.dimension().identifier();
        paintFloor(level, origin);
        Set<String> missing = new LinkedHashSet<>();
        for (Station station : GalleryStations.ALL) {
            BlockPos labelAt = station.centre().plus(0, 5, 0).at(origin);
            String text = station.label();
            if (station == GalleryStations.DEBRIS_CRASH && !placeWreck(level, origin, GalleryStations.DEBRIS_WRECK,
                    evaluation, dimension)) {
                text = text + " — layout missing";
            }
            GalleryCommands.label(level, labelAt, text);
            String needs = station.needsMod();
            if (needs != null && !Boolean.TRUE.equals(CompatRegistry.availability().get(needs))) {
                String name = GalleryStations.modName(needs);
                missing.add(name);
                GalleryCommands.label(level, station.centre().plus(0, 4, 0).at(origin), "needs " + name);
            }
        }
        buildMeteorCrater(level, origin, evaluation, dimension);
        buildWreckYard(level, origin, evaluation, dimension);
        buildWar(level, origin);
        List<UUID> mobs = buildCreatures(level, origin);
        buildStorms(level, origin);
        buildMarket(level, origin);
        return new BuildReport(GalleryStations.ALL.size(), List.copyOf(missing), List.copyOf(mobs));
    }

    private static void set(ServerLevel level, BlockPos origin, int dx, int dy, int dz, BlockState state) {
        level.setBlockAndUpdate(origin.offset(dx, dy, dz), state);
    }

    private static void paintFloor(ServerLevel level, BlockPos origin) {
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        Station debris = GalleryStations.DEBRIS_CRASH;
        Offset wreck = GalleryStations.DEBRIS_WRECK.origin();
        for (int dx = debris.bay().minDx(); dx <= debris.bay().maxDx(); dx++) {
            for (int dz = debris.bay().minDz(); dz <= debris.bay().maxDz(); dz++) {
                boolean underWreck = dx >= wreck.dx() && dx < wreck.dx() + 5 && dz >= wreck.dz() && dz < wreck.dz() + 5;
                if (!underWreck) {
                    set(level, origin, dx, 0, dz, tuff);
                }
            }
        }
        Offset pad = GalleryStations.SUPPLY_DROP.centre();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, origin, pad.dx() + dx, 0, pad.dz() + dz, dx == 0 && dz == 0
                        ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        fillFloor(level, origin, GalleryStations.FACTION_WAR, Blocks.DEEPSLATE_TILES.defaultBlockState());
        fillFloor(level, origin, GalleryStations.CREATURES, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        Station boss = GalleryStations.BOSS;
        Offset c = boss.centre();
        for (int dx = boss.bay().minDx(); dx <= boss.bay().maxDx(); dx++) {
            for (int dz = boss.bay().minDz(); dz <= boss.bay().maxDz(); dz++) {
                int ring = Math.max(Math.abs(dx - c.dx()), Math.abs(dz - c.dz()));
                set(level, origin, dx, 0, dz, ring == 4 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        for (int dx : GalleryStations.STORM_POST_DX) {
            set(level, origin, dx, 0, GalleryStations.STORM_POST_DZ, Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int dx = GalleryStations.MARKET_WALL_MIN_DX; dx <= GalleryStations.MARKET_WALL_MAX_DX; dx++) {
            set(level, origin, dx, 0, GalleryStations.MARKET_WALL_DZ, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        }
    }

    private static void fillFloor(ServerLevel level, BlockPos origin, Station station, BlockState state) {
        for (int dx = station.bay().minDx(); dx <= station.bay().maxDx(); dx++) {
            for (int dz = station.bay().minDz(); dz <= station.bay().maxDz(); dz++) {
                set(level, origin, dx, 0, dz, state);
            }
        }
    }

    /** The meteor station: a tuff plinth with a real (loot-free) crater dug into it. */
    private static void buildMeteorCrater(ServerLevel level, BlockPos origin, ProtectionEvaluation evaluation,
            Identifier dimension) {
        Station station = GalleryStations.METEOR_SHOWER;
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        for (int dx = station.bay().minDx(); dx <= station.bay().maxDx(); dx++) {
            for (int dz = station.bay().minDz(); dz <= station.bay().maxDz(); dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    set(level, origin, dx, dy, dz, tuff);
                }
            }
        }
        BlockPos impact = station.centre().plus(0, 3, 0).at(origin);
        CraterPlan plan = CraterPlan.of(impact, 3, false, 3, 1, new Random(0x6E45L));
        CraterBuilder.apply(new GalleryCraterWorld(level, origin), plan, evaluation, dimension);
    }

    /** Places one wreck with the empty-crate world; returns false when its layout has not loaded. */
    private static boolean placeWreck(ServerLevel level, BlockPos origin, YardWreck wreck,
            ProtectionEvaluation evaluation, Identifier dimension) {
        MinecraftServer server = level.getServer();
        Optional<WreckLayout> layout = WreckLayouts.get(server, WreckLayouts.idOf(wreck.variant()));
        if (layout.isEmpty()) {
            return false;
        }
        BlockPos at = wreck.origin().at(origin);
        WreckPlacer.place(new WreckPlacer.ServerWreckWorld(level, null, null, 0L, 0L), layout.get(), at, evaluation,
                dimension);
        for (DecayCell cell : GalleryStations.decay(layout.get(), wreck.stage())) {
            BlockState state = switch (cell.kind()) {
                case SCORCHED_PANEL -> CorePayloadBlocks.SCORCHED_PANEL.get().defaultBlockState();
                case RUBBLE -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case AIR -> Blocks.AIR.defaultBlockState();
            };
            level.setBlockAndUpdate(at.offset(cell.x(), cell.y(), cell.z()), state);
        }
        return true;
    }

    private static void buildWreckYard(ServerLevel level, BlockPos origin, ProtectionEvaluation evaluation,
            Identifier dimension) {
        for (YardWreck wreck : GalleryStations.YARD) {
            String text = wreck.label();
            if (!placeWreck(level, origin, wreck, evaluation, dimension)) {
                text = text + " — layout missing";
            }
            GalleryCommands.label(level, wreck.centre().plus(0, 5, 0).at(origin), text);
        }
    }

    private static int colourA() {
        return WarColours.of(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "gallery/side_a"));
    }

    private static int colourB() {
        return WarColours.of(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "gallery/side_b"));
    }

    /** Three beacons bound to no event: west held by A, east by B, the centre driven by the war script. */
    private static void buildWar(ServerLevel level, BlockPos origin) {
        int a = colourA();
        int b = colourB();
        List<Offset> beacons = GalleryStations.WAR_BEACONS;
        for (int i = 0; i < beacons.size(); i++) {
            BlockPos pos = beacons.get(i).at(origin);
            level.setBlockAndUpdate(pos, WarBlocks.WAR_BEACON.get().defaultBlockState());
            if (level.getBlockEntity(pos) instanceof WarBeaconBlockEntity be) {
                be.bind(null, i, a, b, GalleryWarScript.CAPTURE_TICKS, i == 1 ? 4 : 3);
                if (i == 0) {
                    be.sync(WarBeaconBlockEntity.SIDE_A, 0, false, WarBeaconBlockEntity.NONE);
                } else if (i == 2) {
                    be.sync(WarBeaconBlockEntity.SIDE_B, 0, false, WarBeaconBlockEntity.NONE);
                }
            }
        }
    }

    /**
     * The raid/invasion and boss bays: labels only, no creatures. Display mobs were dropped on purpose: a creative
     * player bypasses invulnerability, a killed NeroCreatures mob drops its loot and XP, and a killed boss runs
     * {@code BossController.onDeath} (a real defeat on Core's threshold bus, a companion-app broadcast and the
     * boss cooldown) even though no fight started. A display boss also counts as a live boss and the mobs count
     * toward the creature caps. None of that is visible to the safety scan (it is a sibling's death path), so
     * the bays stay label-only until NeroCreatures guards a never-started fight.
     */
    private static List<UUID> buildCreatures(ServerLevel level, BlockPos origin) {
        GalleryCommands.label(level, origin.offset(-3, 4, -60),
                "Pirate Raid — raiders arrive in waves through NeroCreatures");
        GalleryCommands.label(level, origin.offset(3, 4, -60),
                "Alien Invasion — aliens arrive in waves through NeroCreatures");
        GalleryCommands.label(level, origin.offset(14, 4, -58),
                "Boss arena — a real boss is summoned through NeroCreatures");
        return List.of();
    }

    private static void invulnerable(Entity entity) {
        //? if >=26.3 {
        /*entity.setPermanentlyInvulnerable(true);
        *///?} else {
        entity.setInvulnerable(true);
        //?}
    }

    private static void buildStorms(ServerLevel level, BlockPos origin) {
        // By registry id: 26.2 moved the stained glass fields into a colour collection.
        BlockState[] glass = {vanilla("orange_stained_glass"), vanilla("brown_stained_glass"),
                vanilla("light_blue_stained_glass"), vanilla("white_stained_glass")};
        for (int i = 0; i < GalleryStations.STORM_POST_DX.length; i++) {
            int dx = GalleryStations.STORM_POST_DX[i];
            for (int dy = 1; dy <= 4; dy++) {
                set(level, origin, dx, dy, GalleryStations.STORM_POST_DZ, glass[i]);
            }
            GalleryCommands.label(level, origin.offset(dx, 6, GalleryStations.STORM_POST_DZ),
                    GalleryStations.STORM_POST_LABELS.get(i));
        }
    }

    private static BlockState vanilla(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path)).defaultBlockState();
    }

    private static void buildMarket(ServerLevel level, BlockPos origin) {
        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int dx = GalleryStations.MARKET_WALL_MIN_DX; dx <= GalleryStations.MARKET_WALL_MAX_DX; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                set(level, origin, dx, dy, GalleryStations.MARKET_WALL_DZ, wall);
            }
        }
        for (int i = 0; i < GalleryStations.MARKET_ITEMS.size(); i++) {
            int dx = GalleryStations.MARKET_WALL_MIN_DX + i;
            BlockPos at = origin.offset(dx, 2, GalleryStations.MARKET_FRAME_DZ);
            Identifier itemId = Identifier.tryParse(GalleryStations.MARKET_ITEMS.get(i));
            Item item = itemId == null ? Items.BARRIER
                    : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.BARRIER);
            frame(level, at, new ItemStack(item));
            GalleryCommands.label(level, origin.offset(dx, 4, GalleryStations.MARKET_FRAME_DZ),
                    GalleryStations.MARKET_LABELS.get(i));
        }
        BlockState console = ConsoleBlocks.EVENT_CONSOLE.get().defaultBlockState();
        if (console.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            console = console.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        level.setBlockAndUpdate(GalleryStations.MARKET_CONSOLE.at(origin), console);
    }

    /** A fixed, invulnerable glow frame hanging on the wall north of {@code at}, facing south. */
    private static void frame(ServerLevel level, BlockPos at, ItemStack stack) {
        GlowItemFrame frame = new GlowItemFrame(level, at, Direction.SOUTH);
        // "Fixed" has no public setter: set it the way the label stands get "Marker", through Entity#load, with
        // the position and facing it already has so the load keeps them.
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(frame.getX()));
        pos.add(DoubleTag.valueOf(frame.getY()));
        pos.add(DoubleTag.valueOf(frame.getZ()));
        tag.put("Pos", pos);
        tag.putIntArray("block_pos", new int[] {at.getX(), at.getY(), at.getZ()});
        tag.putByte("Facing", (byte) Direction.SOUTH.get3DDataValue());
        tag.putBoolean("Fixed", true);
        tag.putBoolean("Invisible", false);
        tag.putBoolean("Invulnerable", true);
        frame.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        frame.setItem(stack, false);
        invulnerable(frame);
        GalleryCommands.spawn(level, frame);
    }

    /** The stress scene: 40 hanging meteors and 6 more landed pods. Returns the meteor count. */
    static int buildStress(ServerLevel level, BlockPos origin) {
        int meteors = 0;
        for (Offset o : GalleryLayout.stressMeteors()) {
            BlockPos pos = o.at(origin);
            GalleryCommands.spawn(level, MeteorEntity.display(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
            meteors++;
        }
        List<Offset> pods = GalleryLayout.stressPods();
        for (int i = 0; i < pods.size(); i++) {
            BlockPos pos = pods.get(i).at(origin);
            GalleryCommands.spawn(level, SupplyPodEntity.display(level, pos.getX() + 0.5D, pos.getY(),
                    pos.getZ() + 0.5D, 1 + i % 3, true));
        }
        return meteors;
    }

    // --- session ---------------------------------------------------------------------------------------------

    /** Starts the demo for {@code owner} (replacing any other session on this server). */
    static void startSession(MinecraftServer server, ServerPlayer owner, BlockPos origin, Mode mode,
            List<UUID> mobs) {
        Session previous = session;
        if (previous != null) {
            end(previous, true);
        }
        Session next = new Session(server, owner.getUUID(), owner.level().dimension().identifier(), origin, mode);
        next.mobs.addAll(mobs);
        session = next;
        Services.NETWORK.sendToPlayer(owner, GalleryDemoData.hud(next.hudScene, 0L, next.dimension, next.origin));
    }

    /** Whether {@code player} owns the live session, in its dimension. */
    static boolean ownsSession(ServerPlayer player) {
        Session s = session;
        return s != null && s.server == player.level().getServer()
                && s.matches(player.getUUID(), player.level().dimension().identifier());
    }

    /** Ends the session if {@code player} started it, wherever it is. */
    static void endOwnedBy(ServerPlayer player) {
        Session s = session;
        if (s != null && s.owner.equals(player.getUUID())) {
            end(s, true);
        }
    }

    /** Ends the session if it belongs to the gallery at {@code origin} in {@code level}. */
    static void endIfAt(ServerLevel level, BlockPos origin) {
        Session s = session;
        if (s != null && s.dimension.equals(level.dimension().identifier()) && s.origin.equals(origin)) {
            end(s, true);
        }
    }

    /** Server stopping: drops the session (and the owner's UUID with it) without sending anything. */
    public static void onServerStopping(MinecraftServer server) {
        Session s = session;
        if (s != null && (s.server == server || s.server == null)) {
            session = null;
        }
    }

    /** Stops the loops, removes the loop entities and, when the owner is online, clears their demo screen. */
    static void end(Session s, boolean notifyOwner) {
        if (session == s) {
            session = null;
        }
        MinecraftServer server = s.server;
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, s.dimension));
        if (level != null) {
            discard(level, s.pod);
            discard(level, s.meteor);
            discard(level, s.debris);
        }
        s.pod = null;
        s.meteor = null;
        s.debris = null;
        if (!notifyOwner) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
        if (owner == null) {
            return;
        }
        if (!realStormIn(s.dimension)) {
            Services.NETWORK.sendToPlayer(owner, StormSyncPayload.none(s.dimension));
            Services.NETWORK.sendToPlayer(owner, new StormExposurePayload(false));
        }
        Services.NETWORK.sendToPlayer(owner, HudState.EMPTY);
    }

    private static void discard(ServerLevel level, @Nullable UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    /** Whether a real storm runs in {@code dimension} (the preview then stays off). */
    static boolean realStormIn(Identifier dimension) {
        return ActiveStorms.active(dimension).isPresent();
    }

    /** Pins the storm preview to {@code variant}. */
    static void pinStorm(StormVariant variant) {
        Session s = session;
        if (s != null) {
            s.stormPinned = variant;
            s.stormCycling = false;
            s.stormSentAt = Long.MIN_VALUE / 2; // send on the next tick
        }
    }

    /** Turns the storm preview off. */
    static void stormOff() {
        Session s = session;
        if (s == null) {
            return;
        }
        s.stormPinned = null;
        s.stormCycling = false;
        s.stormSent = null;
        MinecraftServer server = s.server;
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(s.owner);
        if (owner != null && !realStormIn(s.dimension)) {
            Services.NETWORK.sendToPlayer(owner, StormSyncPayload.none(s.dimension));
            Services.NETWORK.sendToPlayer(owner, new StormExposurePayload(false));
        }
    }

    /** Each loader calls this from its server tick, beside {@code ConsoleServer.tick}. Cheap with no session. */
    public static void tick(MinecraftServer server) {
        Session s = session;
        if (s == null) {
            return;
        }
        if (s.server != server) {
            session = null; // left over from a server that has stopped
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
        if (owner == null || !owner.level().dimension().identifier().equals(s.dimension)) {
            end(s, true);
            return;
        }
        try {
            s.tick++;
            ServerLevel level = owner.level();
            supplyLoop(s, level, false);
            meteorLoop(s, level, owner, false);
            debrisLoop(s, level, owner, false);
            warLoop(s, level);
            stormPreview(s, owner);
            if (s.sceneAge() >= GalleryDemoData.SCENE_TICKS) {
                s.hudScene = GalleryDemoData.next(s.hudScene);
                s.hudSceneStartedAt = s.tick;
            }
        } catch (RuntimeException | LinkageError e) {
            if (!s.failureReported) {
                s.failureReported = true;
                NeroEventsTelemetry.captureHandledException(e, "command", "gallery");
            }
            end(s, true);
        }
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static @Nullable Entity live(ServerLevel level, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        Entity entity = level.getEntity(id);
        return entity == null || entity.isRemoved() ? null : entity;
    }

    private static void supplyLoop(Session s, ServerLevel level, boolean cue) {
        BlockPos pad = GalleryStations.SUPPLY_DROP.centre().at(s.origin);
        if (!loaded(level, pad) || (!cue && s.tick < s.podNextAt)) {
            return;
        }
        discard(level, s.pod);
        int dy = cue ? POD_CUE_DY : POD_DROP_DY;
        SupplyPodEntity pod = SupplyPodEntity.display(level, pad.getX() + 0.5D, s.origin.getY() + dy,
                pad.getZ() + 0.5D, s.podTier, false);
        GalleryCommands.spawn(level, pod);
        s.pod = pod.getUUID();
        s.podNextAt = s.tick + (cue ? 300 : POD_PERIOD);
        s.podTier = s.podTier % 3 + 1;
    }

    private static void meteorLoop(Session s, ServerLevel level, ServerPlayer owner, boolean cue) {
        BlockPos impact = s.meteorImpact;
        if (!loaded(level, impact)) {
            return;
        }
        if (cue) {
            discard(level, s.meteor);
            s.meteor = null;
            s.meteorNextAt = s.tick;
        }
        if (s.meteor != null) {
            Entity meteor = live(level, s.meteor);
            if (meteor == null) {
                PayloadSounds.playLayered(level, impact.getX() + 0.5D, impact.getY() + 1.0D, impact.getZ() + 0.5D,
                        PayloadSounds.METEOR_IMPACT, 48.0F);
                Services.NETWORK.sendToPlayer(owner, new ImpactFlashPayload(impact.above(),
                        (byte) Severity.HAZARD.ordinal(), false));
                s.meteor = null;
                s.meteorNextAt = s.tick + METEOR_REST;
            } else if (s.tick - s.meteorSpawnedAt > FALL_TIMEOUT) {
                meteor.discard();
                s.meteor = null;
                s.meteorNextAt = s.tick + METEOR_REST;
            }
            return;
        }
        if (s.tick < s.meteorNextAt) {
            return;
        }
        MeteorEntity meteor = MeteorEntity.displayFalling(level, impact.getX() + 0.5D, s.origin.getY() + 2 + 10.5D,
                impact.getZ() + 0.5D, -0.35D);
        GalleryCommands.spawn(level, meteor);
        s.meteor = meteor.getUUID();
        s.meteorSpawnedAt = s.tick;
    }

    private static void debrisLoop(Session s, ServerLevel level, ServerPlayer owner, boolean cue) {
        BlockPos impact = s.debrisImpact;
        if (!loaded(level, impact)) {
            return;
        }
        if (cue) {
            discard(level, s.debris);
            s.debris = null;
            s.debrisNextAt = s.tick;
        }
        if (s.debris != null) {
            Entity debris = live(level, s.debris);
            if (debris == null) {
                PayloadSounds.playLayered(level, impact.getX() + 0.5D, impact.getY() + 1.0D, impact.getZ() + 0.5D,
                        PayloadSounds.DEBRIS_IMPACT, 48.0F);
                Services.NETWORK.sendToPlayer(owner, new ImpactFlashPayload(impact.above(),
                        (byte) Severity.HAZARD.ordinal(), true));
                s.debris = null;
                s.debrisNextAt = s.tick + DEBRIS_REST;
            } else if (s.tick - s.debrisSpawnedAt > FALL_TIMEOUT) {
                debris.discard();
                s.debris = null;
                s.debrisNextAt = s.tick + DEBRIS_REST;
            }
            return;
        }
        if (s.tick < s.debrisNextAt) {
            return;
        }
        DebrisEntity debris = DebrisEntity.displayFalling(level, s.origin.getX() + 14.5D, s.origin.getY() + 2 + 9.0D,
                s.origin.getZ() - 31.5D, -0.30D);
        GalleryCommands.spawn(level, debris);
        s.debris = debris.getUUID();
        s.debrisSpawnedAt = s.tick;
    }

    private static void warLoop(Session s, ServerLevel level) {
        BlockPos pos = GalleryStations.WAR_BEACONS.get(1).at(s.origin);
        if (!loaded(level, pos)) {
            return;
        }
        GalleryWarScript.Frame f = GalleryWarScript.at(s.warT);
        s.warT = (s.warT + 1) % GalleryWarScript.PERIOD;
        if (!(level.getBlockEntity(pos) instanceof WarBeaconBlockEntity be)) {
            return;
        }
        be.sync(f.owner(), f.progress(), f.contested(), f.progressSide());
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;
        if (f.tickSound()) {
            PayloadSounds.play(level, x, y, z, PayloadSounds.WAR_BEACON_CAPTURE_TICK, 24.0F,
                    PayloadSounds.captureTickPitch(f.progress(), GalleryWarScript.CAPTURE_TICKS));
        }
        if (f.capturedNow()) {
            PayloadSounds.playLayered(level, x, y, z, PayloadSounds.WAR_BEACON_CAPTURED, 48.0F);
        }
    }

    private static void stormPreview(Session s, ServerPlayer owner) {
        if (s.stormPinned == null && !s.stormCycling) {
            return;
        }
        if (realStormIn(s.dimension)) {
            s.stormSent = null; // a real storm here: its own syncs win
            return;
        }
        if (s.stormCycling && s.tick - s.stormChangedAt >= STORM_SWITCH_TICKS) {
            s.stormIndex = (s.stormIndex + 1) % STORM_CYCLE.length;
            s.stormChangedAt = s.tick;
        }
        StormVariant variant = s.stormPinned != null ? s.stormPinned : STORM_CYCLE[s.stormIndex];
        if (variant == s.stormSent && s.tick - s.stormSentAt < STORM_RESEND_TICKS) {
            return;
        }
        Services.NETWORK.sendToPlayer(owner, StormSyncPayload.active(s.dimension, variant, STORM_INTENSITY,
                STORM_RAMP, STORM_WIND_YAW));
        Services.NETWORK.sendToPlayer(owner, new StormExposurePayload(true));
        s.stormSent = variant;
        s.stormSentAt = s.tick;
    }

    // --- cues ------------------------------------------------------------------------------------------------

    /** Runs a cue for the session owner; false only when {@code console} finds no gallery console nearby. */
    static boolean cue(ServerPlayer owner, String id) {
        Session s = session;
        if (s == null) {
            return false;
        }
        ServerLevel level = owner.level();
        switch (id) {
            case "supply_drop" -> supplyLoop(s, level, true);
            case "meteor_shower" -> meteorLoop(s, level, owner, true);
            case "debris_crash" -> debrisLoop(s, level, owner, true);
            case "faction_war" -> s.warT = GalleryWarScript.CUE_T;
            case "console" -> {
                return openConsole(s, owner);
            }
            default -> {
                int scene = GalleryStations.CUE_IDS.indexOf(id) - GalleryStations.CUE_IDS.indexOf("hud_info");
                if (scene >= 0 && scene < GalleryDemoData.SCENES) {
                    s.hudScene = scene;
                    s.hudSceneStartedAt = s.tick;
                    Services.NETWORK.sendToPlayer(owner, GalleryDemoData.hud(s.hudScene, 0L, s.dimension, s.origin));
                }
            }
        }
        return true;
    }

    private static boolean openConsole(Session s, ServerPlayer owner) {
        ServerLevel level = owner.level();
        BlockPos at = owner.blockPosition();
        Identifier dimension = level.dimension().identifier();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-6, -6, -6), at.offset(6, 6, 6))) {
            if (!s.covers(dimension, pos) || !(level.getBlockState(pos).getBlock() instanceof EventConsoleBlock)) {
                continue;
            }
            double distance = pos.distSqr(at);
            if (distance <= 36.0D && distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        if (best == null) {
            return false;
        }
        BlockPos console = best;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inventory, who) -> new EventConsoleMenu(id, inventory, ContainerLevelAccess.create(level, console)),
                Component.translatable("block.neroevents.event_console"));
        MenuOpener.openOrConsume(owner, provider);
        if (owner.containerMenu instanceof EventConsoleMenu menu) {
            ConsoleServer.sendSnapshot(owner, menu, true, "", "");
        }
        return true;
    }

    // --- the crater world: the real crater shape, no loot ------------------------------------------------

    /** The crater seam for the meteor station: only inside the clear box, and the pocket holds nothing. */
    private static final class GalleryCraterWorld implements CraterBuilder.CraterWorld {
        private final ServerLevel level;
        private final BlockPos origin;

        GalleryCraterWorld(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        @Override
        public boolean replaceable(BlockPos pos) {
            Offset o = new Offset(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            return GalleryLayout.CLEAR_BOX.contains(o) && !level.getBlockState(pos).hasBlockEntity();
        }

        @Override
        public boolean solid(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && state.isSolidRender();
        }

        @Override
        public void set(CraterPlan.Cell cell) {
            BlockState next = switch (cell.kind()) {
                case AIR -> Blocks.AIR.defaultBlockState();
                case CRUST -> CorePayloadBlocks.COOLED_CRUST.get().defaultBlockState();
                case SLAG -> CorePayloadBlocks.METEOR_SLAG.get().defaultBlockState();
                case ORE -> CorePayloadBlocks.EMBEDDED_METEOR_ORE.get().defaultBlockState();
                case POCKET -> CorePayloadBlocks.CRATER_POCKET.get().defaultBlockState();
            };
            level.setBlock(cell.pos(), next, Block.UPDATE_ALL);
            if (cell.kind() == CraterPlan.Kind.POCKET
                    && level.getBlockEntity(cell.pos()) instanceof CraterPocketBlockEntity pocket) {
                pocket.configure(null, null, 0L, 0L);
            }
        }
    }
}
