package za.co.neroland.neroevents.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.console.ConsoleRows;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.war.WarSides;
import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapDimensionBadge;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * NeroEvents' map layers, offered through Neroland Core's map registry; NeroEvents depends on no map mod.
 *
 * <p>Two {@link MapScope#PUBLIC} layers, the same for every viewer, under the event console's own rule
 * ({@link ConsoleRows#locationAllowed}: the definition makes the location public, there is a public anchor, and the
 * event is not a raid or invasion target), re-checked on every query:
 * <ul>
 *   <li>{@code events}: a pin at the event's <b>public anchor</b> and, for a radius event, its declared radius.</li>
 *   <li>{@code war_zones}: a faction war's capture beacons, the holding side shown by a glyph key and a colour.</li>
 * </ul>
 * Storms have no position, so they are a dimension badge, not a layer. Nothing here names a player.
 */
public final class EventsMapLayers implements MapLayerProvider {

    private static final Logger LOG = LoggerFactory.getLogger("NeroEvents/Map");

    public static final Identifier EVENTS = id("events");
    public static final Identifier WAR_ZONES = id("war_zones");

    static final Identifier ICON_EVENT = id("event");
    static final Identifier ICON_WAR = id("war_zone");
    static final Identifier ICON_STORM = id("storm");

    private static final String KEY_PATTERN = "[a-z0-9_.-]{1,128}";
    private static final int VERMILLION = 0xFFD55E00;
    private static final int VERMILLION_FILL = 0x22D55E00;
    private static final int SIDE_A = 0xFF56B4E9;
    private static final int SIDE_B = 0xFFE69F00;
    private static final int UNCLAIMED = 0xFFF2F5F8;

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.of(EVENTS, "map.layer.neroevents.events", ICON_EVENT, MapScope.PUBLIC),
            MapLayerInfo.of(WAR_ZONES, "map.layer.neroevents.war_zones", ICON_WAR, MapScope.PUBLIC));

    private final Function<MinecraftServer, EventsMapSource> sources;

    EventsMapLayers(Function<MinecraftServer, EventsMapSource> sources) {
        this.sources = sources;
    }

    /** Registers the provider with Core's map registry. Never throws. */
    public static void register() {
        try {
            MapLayerRegistry.register(new EventsMapLayers(server -> EventsMapSource.LIVE));
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("NeroEvents map layers not registered ({})", e.getClass().getSimpleName());
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path);
    }

    @Override
    public String modId() {
        return NeroEventsCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (viewer == null || dimension == null || sink == null) {
            return;
        }
        EventsMapSource source = sources.apply(viewer.server());
        if (source == null) {
            return;
        }
        Identifier dim = dimension.identifier();
        for (EventsMapSource.Live live : source.live()) {
            EventView view = live.view();
            // The console's rule, every query: public location, a public anchor, not a raid or invasion.
            if (view == null || !ConsoleRows.locationAllowed(view) || !dim.equals(view.dimension())
                    || !view.phase().live()) {
                continue;
            }
            BlockPos anchor = view.publicAnchor();
            String key = Integer.toHexString(ConsoleRows.keyOf(view.instanceId()));
            MapLabel title = MapLabel.translatable(key(view.titleKey(), "map.neroevents.event"));
            if (!sink.isFull(EVENTS)) {
                sink.marker(MapMarker.builder("event/" + key, EVENTS, dimension, anchor).label(title)
                        .detail(MapLabel.translatable(view.phase().upcoming() ? "map.neroevents.phase.upcoming"
                                : "map.neroevents.phase.active"))
                        .build());
                if (live.radius() > 0) {
                    sink.area(new MapArea.Circle("event_area/" + key, EVENTS, dimension,
                            MapAreaStyle.of(VERMILLION_FILL, VERMILLION), anchor.getX(), anchor.getZ(),
                            live.radius()));
                }
            }
            for (EventsMapSource.Beacon beacon : live.beacons()) {
                if (sink.isFull(WAR_ZONES)) {
                    break;
                }
                WarSides.Side owner = beacon.owner();
                String side = owner == WarSides.Side.A ? "a" : owner == WarSides.Side.B ? "b" : "none";
                int argb = owner == WarSides.Side.A ? SIDE_A : owner == WarSides.Side.B ? SIDE_B : UNCLAIMED;
                sink.marker(MapMarker.builder("war/" + key + "/" + beacon.index(), WAR_ZONES, dimension, beacon.pos())
                        .label(title).argb(argb)
                        .detail(MapLabel.translatable("map.neroevents.war.side_" + side)).build());
            }
        }
    }

    /** A storm on a dimension, as a badge beside the dimension's name: storms have no position to draw. */
    @Override
    public List<MapDimensionBadge> dimensionBadges(MapViewer viewer) {
        EventsMapSource source = viewer == null ? null : sources.apply(viewer.server());
        if (source == null) {
            return List.of();
        }
        List<MapDimensionBadge> out = new ArrayList<>();
        for (Identifier dim : source.stormDimensions()) {
            if (out.size() >= MapDimensionBadge.MAX_PER_PROVIDER) {
                break;
            }
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            Identifier badge = id("storm/" + dim.getNamespace() + "/" + dim.getPath());
            out.add(new MapDimensionBadge(badge, key, ICON_STORM, MapLabel.translatable("map.neroevents.storm")));
        }
        return out;
    }

    private static String key(String key, String fallback) {
        return key != null && key.matches(KEY_PATTERN) ? key : fallback;
    }
}
