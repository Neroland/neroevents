package za.co.neroland.neroevents.event.announce;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelData;

import za.co.neroland.neroevents.event.EventDefinitions;
import za.co.neroland.neroevents.event.EventView;

/**
 * Broadcasts lifecycle lines to every online player as translatable chat. The location part is a
 * compass bearing and a rough distance from world spawn when the definition makes its location public,
 * and "somewhere in &lt;dimension&gt;" otherwise — never coordinates, never a player.
 */
public final class ChatAnnouncer implements Announcer {

    private static final String[] BEARINGS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /**
     * {@code <outcome>.<payload type path>} pairs that ship their own resolution line
     * ({@code announce.neroevents.resolved.<outcome>.<type>}); every other pair uses the generic
     * {@code announce.neroevents.resolved.<outcome>}. Listed here because the server cannot ask the client's
     * language whether a key exists.
     */
    private static final Set<String> TYPE_SPECIFIC_RESOLUTIONS = Set.of("failure.market_shock");

    private final MinecraftServer server;

    public ChatAnnouncer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void announced(EventView view) {
        long minutes = Math.max(0L, (view.startsAt() - view.now() + 1199L) / 1200L);
        Component dimension = Component.literal(view.dimension().toString());
        Component line = Component.translatable(definitionAnnounceKey(view), dimension, minutes, where(view));
        broadcast(Component.translatable("announce.neroevents.generic", Component.translatable(view.titleKey()), line));
    }

    @Override
    public void started(EventView view) {
        broadcast(Component.translatable("announce.neroevents.started", Component.translatable(view.titleKey()),
                Component.translatable(view.whatToDoKey())));
    }

    @Override
    public void resolved(EventView view) {
        broadcast(Component.translatable(resolvedKey(view), Component.translatable(view.titleKey())));
    }

    /** The type-specific resolution key when one ships, else the generic one for the outcome. */
    static String resolvedKey(EventView view) {
        String outcome = view.outcome() == null ? "expired" : view.outcome().key();
        if (view.type() != null && "neroevents".equals(view.type().getNamespace())) {
            String specific = outcome + "." + view.type().getPath();
            if (TYPE_SPECIFIC_RESOLUTIONS.contains(specific)) {
                return "announce.neroevents.resolved." + specific;
            }
        }
        return "announce.neroevents.resolved." + outcome;
    }

    @Override
    public void cancelled(EventView view, String reasonKey) {
        broadcast(Component.translatable("announce.neroevents.cancelled", Component.translatable(view.titleKey())));
    }

    @Override
    public void skipped(EventView view, String reasonKey) {
        broadcast(Component.translatable("announce.neroevents.skipped." + reasonKey,
                Component.translatable(view.titleKey())));
    }

    @Override
    public void note(EventView view, String key, Object... args) {
        broadcast(Component.translatable(key, args));
    }

    /** The definition's own announce key is the line's body; the title is the generic prefix. */
    private static String definitionAnnounceKey(EventView view) {
        return EventDefinitions.definition(view.definitionId())
                .map(def -> def.display().announce())
                .orElse("announce.neroevents.fallback");
    }

    private Component where(EventView view) {
        BlockPos anchor = view.publicAnchor();
        if (anchor == null) {
            return Component.translatable("announce.neroevents.where.somewhere",
                    Component.literal(view.dimension().toString()));
        }
        LevelData.RespawnData spawn = server.overworld().getRespawnData();
        BlockPos origin = spawn.dimension().identifier().equals(view.dimension()) ? spawn.pos() : BlockPos.ZERO;
        int dx = anchor.getX() - origin.getX();
        int dz = anchor.getZ() - origin.getZ();
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        String band = distance < 500.0D ? "near" : distance < 2000.0D ? "far" : "very_far";
        return Component.translatable("announce.neroevents.where.bearing",
                Component.translatable("distance.neroevents." + band),
                Component.translatable("bearing.neroevents." + bearing(dx, dz)));
    }

    /** One of eight compass points; north is negative Z. */
    static String bearing(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0D), 8L);
        return BEARINGS[sector];
    }

    private void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
