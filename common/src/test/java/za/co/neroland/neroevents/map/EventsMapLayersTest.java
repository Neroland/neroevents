package za.co.neroland.neroevents.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.console.ConsoleRows;
import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.Severity;
import za.co.neroland.neroevents.war.WarSides;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapDimensionBadge;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * The event layers against Core's {@link MapLayerConformance} (PUBLIC: every viewer, online or not, sees the same
 * set), and the console rule re-checked on every query: raids, invasions, private and finished events never appear,
 * pins sit on the public anchor, a war's beacons carry the holding side, and storms are a badge, not a layer.
 */
//? if <1.20.3 {
/*// Minecraft 1.20.2 builds any registry key through BuiltInRegistries, which needs a game bootstrap this
// plain-JVM test does not have. The layers are the same code on every cell and are checked on all the others.
@org.junit.jupiter.api.Disabled("1.20.2: registry keys need a bootstrapped game")
*///?}
class EventsMapLayersTest {

    static final Identifier HOME_ID = Identifier.fromNamespaceAndPath("neroevents_test", "home_test");
    static final ResourceKey<Level> HOME = ResourceKey.create(Registries.DIMENSION, HOME_ID);
    static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("neroevents_test", "moon_test"));
    static final UUID VIEWER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    static final UUID STORM = id(1);
    static final UUID RAID = id(2);
    static final UUID PRIVATE = id(3);
    static final UUID WAR = id(4);
    static final UUID SOON = id(5);
    static final UUID DONE = id(6);
    static final UUID ELSEWHERE = id(7);

    static UUID id(int n) {
        return new UUID(0x1000L + n, 0x2000L + n);
    }

    static EventView view(UUID id, EventCategory category, EventPhase phase, Identifier dim, boolean pub,
            BlockPos anchor) {
        return new EventView(id, "e" + id.getLeastSignificantBits(),
                Identifier.fromNamespaceAndPath("neroevents", "test_event"),
                Identifier.fromNamespaceAndPath("neroevents", "test_type"), category, Severity.HAZARD,
                "event.neroevents.test_event.title", "event.neroevents.test_event.todo", dim, phase,
                EventOrigin.SCHEDULER, 0L, 1_000L, 10L, 0, false, 0, 0, pub, pub ? anchor : null, 0, 0, null, null);
    }

    static final class Fake implements EventsMapSource {
        @Override
        public List<Live> live() {
            List<Live> out = new ArrayList<>();
            out.add(new Live(view(STORM, EventCategory.HAZARD, EventPhase.ACTIVE, HOME_ID, true,
                    new BlockPos(10, 64, 10)), 32, List.of()));
            out.add(new Live(view(RAID, EventCategory.RAID, EventPhase.ACTIVE, HOME_ID, true,
                    new BlockPos(20, 64, 0)), 16, List.of()));
            out.add(new Live(view(PRIVATE, EventCategory.COMBAT, EventPhase.ACTIVE, HOME_ID, false,
                    new BlockPos(-20, 64, 0)), 16, List.of()));
            out.add(new Live(view(WAR, EventCategory.WAR, EventPhase.ACTIVE, HOME_ID, true, new BlockPos(0, 64, 40)),
                    0, List.of(new Beacon(0, new BlockPos(5, 64, 45), WarSides.Side.A),
                            new Beacon(1, new BlockPos(-5, 64, 45), WarSides.Side.B),
                            new Beacon(2, new BlockPos(0, 64, 30), null))));
            out.add(new Live(view(SOON, EventCategory.REWARD, EventPhase.ANNOUNCED, HOME_ID, true,
                    new BlockPos(-40, 64, -40)), 0, List.of()));
            out.add(new Live(view(DONE, EventCategory.REWARD, EventPhase.DONE, HOME_ID, true,
                    new BlockPos(40, 64, -40)), 0, List.of()));
            out.add(new Live(view(ELSEWHERE, EventCategory.HAZARD, EventPhase.ACTIVE,
                    Identifier.fromNamespaceAndPath("neroevents_test", "other_test"), true, new BlockPos(0, 64, 0)),
                    0, List.of()));
            return out;
        }

        @Override
        public Set<Identifier> stormDimensions() {
            return Set.of(HOME_ID);
        }
    }

    static EventsMapLayers provider() {
        Fake fake = new Fake();
        return new EventsMapLayers(server -> fake);
    }

    static MapViewer at(UUID id, int x, int z) {
        return MapViewer.forTest(id, null, HOME, new BlockPos(x, 64, z));
    }

    static String key(UUID id) {
        return Integer.toHexString(ConsoleRows.keyOf(id));
    }

    @Test
    void conformance() {
        MapLayerConformance.create(provider())
                .viewers(at(VIEWER, 0, 0), at(STRANGER, 5, 0))
                .dimensions(HOME, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenText("Trudy", "Sam")
                .expectEntitledSees(EventsMapLayers.EVENTS, 3)
                .expectEntitledSees(EventsMapLayers.WAR_ZONES, 3)
                .verify();
    }

    @Test
    void bothLayersArePublic() {
        for (MapLayerInfo info : provider().layers()) {
            assertEquals(MapScope.PUBLIC, info.scope());
        }
    }

    @Test
    void onlyWhatTheConsoleMayLocateIsDrawnAtItsPublicAnchor() {
        MapLayerRegistry.register(provider());
        MapQueryResult r = MapLayerRegistry.query("neroevents", MapViewer.forTest(VIEWER, null, null, null), HOME,
                MapBounds.around(0, 0, 1024));
        assertFalse(r.failed());
        Set<String> ids = new HashSet<>();
        r.markers().forEach(m -> ids.add(m.id()));
        r.areas().forEach(a -> ids.add(a.id()));
        assertEquals(Set.of("event/" + key(STORM), "event_area/" + key(STORM), "event/" + key(WAR),
                "event/" + key(SOON), "war/" + key(WAR) + "/0", "war/" + key(WAR) + "/1", "war/" + key(WAR) + "/2"),
                ids, "raids, private, finished and other-world events stay off the map");
        MapMarker storm = r.markers().stream().filter(m -> m.id().equals("event/" + key(STORM))).findFirst()
                .orElseThrow();
        assertEquals(10, storm.x());
        assertEquals(10, storm.z());
        MapMarker heldByA = r.markers().stream().filter(m -> m.id().equals("war/" + key(WAR) + "/0")).findFirst()
                .orElseThrow();
        assertEquals("map.neroevents.war.side_a", heldByA.details().get(0).text());
        assertTrue(heldByA.argb() != 0);
    }

    @Test
    void stormIsADimensionBadge() {
        List<MapDimensionBadge> badges = provider().dimensionBadges(at(VIEWER, 0, 0));
        assertEquals(1, badges.size());
        assertEquals(HOME, badges.get(0).dimension());
        assertEquals("neroevents", badges.get(0).id().getNamespace());
    }
}
