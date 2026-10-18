package za.co.neroland.neroevents.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.client.screen.EventConsoleScreen;
import za.co.neroland.neroevents.command.GalleryLayout;

/**
 * CLIENT, creative-debug: a reproducible screenshot pass over the {@code /neroevents gallery} scene. The server
 * command builds the exhibits and the event stations; this harness pins time and weather, drives the camera
 * through the {@link GalleryShots} list with every overlay hidden (the HUD shown only for the HUD and storm
 * shots), cues each station at its photo moment, and writes one PNG per shot to
 * {@code screenshots/neroevents/<shot>.png}, so the gallery can be re-shot identically after any texture or
 * model change instead of being framed by hand.
 *
 * <p><b>Commands</b> (client side; they never reach the server dispatcher). The root is {@code /nsevgallery},
 * NOT {@code /neroevents}: a client-dispatcher root that shares its name with a server command shadows the server
 * subtree (the client parser matches the root, fails on the server-only child and never forwards).
 * <ul>
 *   <li>{@code /nsevgallery capture [time]}: freezes time and weather ({@code advance_time} and
 *       {@code advance_weather} off), rebuilds the gallery at the spot you stand on ({@code gallery clear} then
 *       {@code gallery}, so a rerun never stacks), strips the floating labels and loose items within 64 blocks,
 *       then shoots the list: every exhibit, every station, the storm previews, every demo HUD scene and every
 *       console tab. {@code time} is {@code day}/{@code noon}/{@code night}/{@code midnight} or a tick count;
 *       the default is {@code night}, which shows the glows, beacons and trails best.</li>
 *   <li>{@code /nsevgallery capture stress [time]}: the same with {@code gallery stress} (40 more meteors,
 *       10 pods, an ion storm preview); after the budget frame it writes the {@code /nsevrender} numbers to chat
 *       and to {@code screenshots/neroevents/stress_budget.txt}.</li>
 *   <li>{@code /nsevgallery capture shot <name>}: captures the current view once, overlays hidden, to
 *       {@code neroevents/<name>.png}.</li>
 * </ul>
 *
 * <p>Every pose is relative to the origin the run started at (the player's block position), so the shots match
 * the gallery plan wherever it is built. The HUD, clouds and view-bob are suppressed for the run and restored
 * afterwards. It changes game rules and replaces blocks: use a test world. Each loader wires
 * {@link #registerClientCommands(CommandDispatcher)} from its client-command hook and {@link #tick()} from its
 * client tick. Nothing here records a player, a name or a position; the screenshots and the budget file stay in
 * this client's own {@code screenshots/} folder and nothing is sent to the server except the ordinary commands
 * above. Other players in view are not hidden, so the run warns when anyone else is in the world.
 */
public final class GalleryCaptureHarness {

    /** Ticks for a fresh frame to render at a pinned pose before grabbing. */
    private static final int SETTLE_TICKS = 12;
    /** Ticks after the teleport and environment pins for chunks to load before building. */
    private static final int BUILD_WARMUP_TICKS = 60;
    /** Ticks after the build for the pods to light, trails to form and the label kill to settle. */
    private static final int SCENE_TICKS = 70;
    /** Ticks a posed shot waits for the server to see the new position before its setup commands go out. */
    private static final int POSE_TICKS = 3;

    private static final String SUBFOLDER = "neroevents";
    private static final String BUDGET_SHOT = "stress_budget";

    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup, int warmup,
            boolean hud, int tab, Vec3 camera, Vec3 target) {
    }

    private enum Phase { MOVE, POSE, WARMUP, SCENE, SETTLE, SHOOT }

    private static final Deque<Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean bobViewWas;
    private static boolean flyingWas;
    private static CloudStatus cloudsWere;
    private static Phase phase = Phase.MOVE;
    private static int warmup;
    private static int sceneWait;
    private static int settle;
    private static int poseWait;
    private static String lastConsoleShot = "";
    private static Shot current;

    private GalleryCaptureHarness() {
    }

    /**
     * Registers the client-side {@code /nsevgallery capture ...} tree. Generic in {@code S} so one call serves
     * NeoForge and Forge ({@code CommandSourceStack}) and Fabric ({@code FabricClientCommandSource}).
     */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("nsevgallery")
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("night", false))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                                .executes(ctx -> shotHere(StringArgumentType.getString(ctx, "name")))))
                                .then(LiteralArgumentBuilder.<S>literal("stress")
                                        .executes(ctx -> startCapture("night", true))
                                        .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                                .executes(ctx -> startCapture(StringArgumentType.getString(ctx, "time"),
                                                        true))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                        .executes(ctx -> startCapture(StringArgumentType.getString(ctx, "time"),
                                                false)))));
    }

    private static int startCapture(String time, boolean stress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.literal("NeroEvents gallery capture already running."));
            return 0;
        }
        BlockPos origin = mc.player.blockPosition();
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(origin, time, stress));
        lastConsoleShot = GalleryShots.lastConsoleShot(stress ? GalleryShots.STRESS : GalleryShots.SHOWCASE);
        begin(mc);
        warnIfOthersNearby(mc);
        mc.player.sendSystemMessage(Component.literal("NeroEvents gallery capture: rebuilding the gallery"
                + (stress ? " (stress scene)" : "") + " here, time=" + time + ", " + QUEUE.size()
                + " shots -> screenshots/" + SUBFOLDER + "/ (overlays hidden)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, false, -1, null, null));
        lastConsoleShot = "";
        begin(mc);
        warnIfOthersNearby(mc);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Screenshots are local files, but they may end up in a public gallery: when anyone else is in this world,
     * say so, because their name tag and skin can appear in a frame. Counts players only; names nobody.
     */
    private static void warnIfOthersNearby(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.level.players().size() <= 1) {
            return;
        }
        mc.player.sendSystemMessage(Component.literal("Other players are in this world: their names and skins may "
                + "appear in these screenshots. Check every capture before you publish it."));
    }

    /** The shot list, with the one-time rebuild and environment pins attached to the first shot. */
    private static List<Shot> galleryQueue(BlockPos origin, String time, boolean stress) {
        List<Shot> shots = new ArrayList<>();
        for (GalleryShots.Shot s : stress ? GalleryShots.STRESS : GalleryShots.SHOWCASE) {
            shots.add(new Shot(s.name(), s.setup(), List.of(), List.of(), s.warmup(), s.hud(), s.tab(),
                    at(origin, s.camera()), at(origin, s.target())));
        }
        List<String> setup = List.of(
                "tp @s " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5),
                "gamerule advance_time false",    // 26.1 renamed doDaylightCycle -> advance_time
                "gamerule advance_weather false", // ...and doWeatherCycle -> advance_weather
                "time set " + time,
                "weather clear");
        // Built after the warmup, once chunks are loaded. The server command clears any earlier gallery at this
        // origin itself; the explicit clear first keeps a rerun honest if a previous run was interrupted.
        List<String> build = List.of(
                "neroevents gallery clear",
                stress ? "neroevents gallery stress" : "neroevents gallery");
        // After the scene settles: strip the floating labels (gallery-tagged stands only) and sweep any litter.
        List<String> cleanup = List.of(
                "kill @e[type=armor_stand,tag=" + GalleryLayout.ENTITY_TAG + ",distance=..96]",
                "kill @e[type=item,distance=..64]",
                "kill @e[type=experience_orb,distance=..64]");
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, cleanup, BUILD_WARMUP_TICKS, first.hud(), first.tab(),
                first.camera(), first.target()));
        return shots;
    }

    private static Vec3 at(BlockPos o, GalleryShots.Pose p) {
        return new Vec3(o.getX() + 0.5 + p.x(), o.getY() + p.y(), o.getZ() + 0.5 + p.z());
    }

    private static void begin(Minecraft mc) {
        new File(mc.gameDirectory, "screenshots/" + SUBFOLDER).mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF);
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false);
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE;
        current = null;
    }

    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    /** Called once per client tick from each loader's client-tick hook. No-op unless a run is live. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            finish(mc);
            return;
        }
        switch (phase) {
            case MOVE -> {
                Shot shot = QUEUE.poll();
                current = shot;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                if (BUDGET_SHOT.equals(shot.name())) {
                    RenderBudget.reset(); // the budget window is the frames of this shot's warmup
                }
                if (shot.build().isEmpty() && shot.camera() != null) {
                    // Cues act around the camera (the console cue opens the console nearest it): pose first, and
                    // let the position reach the server before the setup commands do.
                    applyPose(player, shot);
                    poseWait = POSE_TICKS;
                    phase = Phase.POSE;
                    return;
                }
                sendSetup(player, shot);
            }
            case POSE -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot);
                if (--poseWait <= 0) {
                    sendSetup(player, shot);
                }
            }
            case WARMUP -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                if (shot.build().isEmpty()) {
                    applyPose(player, shot);
                } else {
                    holdStill(mc, player);
                }
                if (--warmup <= 0) {
                    for (String cmd : shot.build()) {
                        player.connection.sendCommand(cmd);
                    }
                    sceneWait = shot.build().isEmpty() ? 0 : SCENE_TICKS;
                    phase = Phase.SCENE;
                }
            }
            case SCENE -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player);
                if (--sceneWait <= 0) {
                    for (String cmd : shot.cleanup()) {
                        player.connection.sendCommand(cmd);
                    }
                    applyPose(player, shot);
                    settle = SETTLE_TICKS;
                    phase = Phase.SETTLE;
                }
            }
            case SETTLE -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot); // re-pinned every tick so nothing drifts the camera
                if (shot.hud()) {
                    setHudHidden(mc, false);
                }
                if (shot.tab() >= 0) {
                    EventConsoleScreen.selectTabForCapture(shot.tab());
                }
                if (--settle <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                if (shot.hud()) {
                    setHudHidden(mc, true);
                }
                if (shot.name().equals(lastConsoleShot)) {
                    player.closeContainer();
                }
                if (BUDGET_SHOT.equals(shot.name())) {
                    writeBudget(mc);
                }
                phase = Phase.MOVE;
            }
        }
    }

    private static void sendSetup(LocalPlayer player, Shot shot) {
        for (String cmd : shot.setup()) {
            player.connection.sendCommand(cmd);
        }
        warmup = shot.warmup();
        phase = Phase.WARMUP;
    }

    /** The renderer numbers for the stress frame: to chat and to a text file beside the screenshots. */
    private static void writeBudget(Minecraft mc) {
        String record = RenderBudget.record();
        if (mc.player != null) {
            for (String line : record.split("\\R")) {
                mc.player.sendSystemMessage(Component.literal(line));
            }
        }
        File out = new File(mc.gameDirectory, "screenshots/" + SUBFOLDER + "/" + BUDGET_SHOT + ".txt");
        try {
            Files.writeString(out.toPath(), record + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("Could not write screenshots/" + SUBFOLDER + "/"
                        + BUDGET_SHOT + ".txt (" + e.getClass().getSimpleName() + ")."));
            }
        }
    }

    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true);
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Snaps the player (the render camera) to the shot's pose. */
    private static void applyPose(LocalPlayer player, Shot shot) {
        Vec3 cam = shot.camera();
        Vec3 tgt = shot.target();
        if (cam == null || tgt == null) {
            return;
        }
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        player.snapTo(cam.x, cam.y, cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, SUBFOLDER + "/" + name + ".png", mainRenderTarget(mc), 1, msg -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(msg);
            }
        });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (cloudsWere != null) {
            mc.options.cloudStatus().set(cloudsWere);
        }
        mc.options.bobView().set(bobViewWas);
        setFlying(mc, flyingWas);
        running = false;
        current = null;
        QUEUE.clear();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("NeroEvents gallery capture done: see screenshots/"
                    + SUBFOLDER + "/."));
        }
    }

    // --- HUD flag and main framebuffer: the API moved in 26.2 --------------------------------------------------

    private static boolean isHudHidden(Minecraft mc) {
        //? if <26.2 {
        /*return mc.options.hideGui;
        *///?} else {
        return mc.gui.hud.isHidden();
        //?}
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        //? if <26.2 {
        /*mc.options.hideGui = hidden;
        *///?} else {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
        //?}
    }

    private static RenderTarget mainRenderTarget(Minecraft mc) {
        //? if <26.2 {
        /*return mc.getMainRenderTarget();
        *///?} else {
        return mc.gameRenderer.mainRenderTarget();
        //?}
    }
}
