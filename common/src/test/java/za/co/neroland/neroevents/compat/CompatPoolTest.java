package za.co.neroland.neroevents.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.DebugMarkerPayload;
import za.co.neroland.neroevents.event.PayloadTypes;
import za.co.neroland.neroevents.payload.debris.DebrisCrashPayload;
import za.co.neroland.neroevents.payload.meteor.MeteorShowerPayload;
import za.co.neroland.neroevents.payload.supply.SupplyDropPayload;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionResult;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.protect.ProtectionSources;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.compat.FakeAgricultureBridge;
import za.co.neroland.neroevents.testing.compat.FakeColoniesBridge;
import za.co.neroland.neroevents.testing.compat.FakeCreaturesBridge;
import za.co.neroland.neroevents.testing.compat.FakeFactionsBridge;
import za.co.neroland.neroevents.testing.compat.FakeNerospaceBridge;
import za.co.neroland.neroevents.testing.compat.FakeOpacBridge;

/**
 * The event pool is right by construction for every combination of installed siblings: each façade registers its
 * payload types and protection sources only when its sibling is loaded and the jar carries the bridge.
 *
 * <p>Each row measures exactly what the façades add (the registry starts empty), so the Core-only row is "the
 * façades add nothing"; the Core-only types themselves are registered by {@code EventPayloads} and covered by its
 * own tests. The "everything" row also registers the four Core-only types first, pinning the full pool of eleven.
 */
class CompatPoolTest {

    private static final Set<String> CORE_ONLY = Set.of("nerolandcore");
    private static final Set<String> SPACE = Set.of("nerolandcore", "nerospace");
    private static final Set<String> SPACE_CREATURES_COLONIES =
            Set.of("nerolandcore", "nerospace", "nerocreatures", "nerocolonies");
    private static final Set<String> EVERYTHING = Set.of("nerolandcore", "nerospace", "nerocreatures", "nerocolonies",
            "neroeconomy", "neroagriculture", "nerofactions", "openpartiesandclaims");

    /** Payload ids each configuration adds, cumulatively. */
    private static final Set<String> STORMS = Set.of("solar_storm", "planetary_storm");
    private static final Set<String> STORMS_CREATURES =
            Set.of("solar_storm", "planetary_storm", "pirate_raid", "alien_invasion", "boss");
    private static final Set<String> ALL_SIBLING = Set.of("solar_storm", "planetary_storm", "pirate_raid",
            "alien_invasion", "boss", "market_shock", "faction_war");
    /** The full pool: the four Core-only types plus everything the façades add. */
    private static final Set<String> FULL_POOL = Set.of("debug_marker", "supply_drop", "meteor_shower",
            "debris_crash", "solar_storm", "planetary_storm", "pirate_raid", "alien_invasion", "boss",
            "market_shock", "faction_war");

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private static void clean() {
        PayloadTypes.resetForTests();
        ProtectionSources.resetForTests();
        CompatRegistry.resetForTests();
    }

    private static CompatRegistry.Fakes allFakes() {
        return new CompatRegistry.Fakes(new FakeNerospaceBridge(), new FakeAgricultureBridge(), new FakeColoniesBridge(),
                new FakeOpacBridge(), new FakeCreaturesBridge(), new FakeFactionsBridge());
    }

    private static Set<String> payloadPaths() {
        return PayloadTypes.ids().stream().map(Identifier::getPath).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> sourceIds() {
        return ProtectionSources.ordered().stream().map(ProtectionSource::id)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void coreOnlyAddsNoPayloadsAndNoSources() {
        CompatRegistry.initForTests(CORE_ONLY::contains, allFakes());
        assertEquals(Set.of(), payloadPaths());
        assertEquals(Set.of(), sourceIds());
        assertTrue(CompatRegistry.availability().values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void nerospaceAddsTheStorms() {
        CompatRegistry.initForTests(SPACE::contains, allFakes());
        assertEquals(Set.of(), sourceIds());
        assertEquals(Map.of("nerospace", true), live());
        assertEquals(STORMS, payloadPaths());
    }

    @Test
    void creaturesAndColoniesAddWavesTheBossAndColonyProtection() {
        CompatRegistry.initForTests(SPACE_CREATURES_COLONIES::contains, allFakes());
        assertEquals(Set.of("colony"), sourceIds());
        assertEquals(Map.of("nerospace", true, "nerocolonies", true, "nerocreatures", true), live());
        assertEquals(STORMS_CREATURES, payloadPaths());
    }

    @Test
    void everythingAddsMarketShockFactionWarAndClaimProtection() {
        CompatRegistry.initForTests(EVERYTHING::contains, allFakes());
        assertEquals(Set.of("colony", "opac"), sourceIds());
        assertTrue(CompatRegistry.availability().values().stream().allMatch(Boolean::booleanValue),
                () -> "every integration live: " + CompatRegistry.availability());
        assertEquals(ALL_SIBLING, payloadPaths());
    }

    @Test
    void coreTypesPlusEverythingIsTheFullPoolOfEleven() {
        // The four types EventPayloads.init() registers (its block registries need a running game).
        PayloadTypes.register(new DebugMarkerPayload());
        PayloadTypes.register(new SupplyDropPayload());
        PayloadTypes.register(new MeteorShowerPayload());
        PayloadTypes.register(new DebrisCrashPayload());
        CompatRegistry.initForTests(EVERYTHING::contains, allFakes());
        assertEquals(FULL_POOL, payloadPaths());
        assertEquals(11, payloadPaths().size());
    }

    @Test
    void agricultureRegistersExactlyOneCycleProvider() {
        FakeAgricultureBridge agriculture = new FakeAgricultureBridge();
        CompatRegistry.initForTests(EVERYTHING::contains,
                new CompatRegistry.Fakes(null, agriculture, null, null, null, null));
        assertEquals(1, agriculture.providers.size());
        // No storm is active anywhere: identity (null), whatever the dimension.
        assertEquals(null, agriculture.providers.get(0).apply(FakeWorldView.OVERWORLD, 0L));
    }

    @Test
    void siblingPresentWithoutBridgeRegistersNothing() {
        Set<String> present = Set.of("nerolandcore", "nerospace", "nerocreatures", "nerocolonies", "neroagriculture",
                "nerofactions", "openpartiesandclaims");
        CompatRegistry.initForTests(present::contains, CompatRegistry.Fakes.none());
        assertEquals(Set.of(), payloadPaths());
        assertEquals(Set.of(), sourceIds());
        assertTrue(CompatRegistry.availability().values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void throwingBridgeTurnsTheIntegrationOffAndTheSourceDenies() {
        FakeOpacBridge opac = new FakeOpacBridge();
        opac.throwing = true;
        CompatRegistry.initForTests(EVERYTHING::contains,
                new CompatRegistry.Fakes(null, null, null, opac, null, null));
        assertTrue(CompatRegistry.availability().get("openpartiesandclaims"), "available before the first call");

        Footprint box = new Footprint(FakeWorldView.OVERWORLD, 0, 60, 0, 20, 70, 20);
        ProtectionResult result = ProtectionQuery.live().evaluation(new FakeWorldView().allChunksLoaded(
                FakeWorldView.OVERWORLD)).mayAffectAll(box);

        assertFalse(result.allowed());
        assertEquals("opac", result.deniedBy());
        assertEquals(1, opac.calls);
        assertFalse(CompatRegistry.availability().get("openpartiesandclaims"), "off after a LinkageError");
        assertTrue(ProtectionSources.health().get("opac").startsWith("failing"));
    }

    @Test
    void claimedChunkDeniesThroughTheRegisteredSource() {
        FakeOpacBridge opac = new FakeOpacBridge().claim(0, 0);
        CompatRegistry.initForTests(EVERYTHING::contains,
                new CompatRegistry.Fakes(null, null, null, opac, null, null));
        ProtectionResult result = ProtectionQuery.live().evaluation(new FakeWorldView().allChunksLoaded(
                FakeWorldView.OVERWORLD)).mayAffect(FakeWorldView.OVERWORLD, new BlockPos(3, 64, 3));
        assertFalse(result.allowed());
        assertEquals("opac", result.deniedBy());
        assertTrue(CompatRegistry.availability().get("openpartiesandclaims"));
    }

    /** The integrations that are live, by id. */
    private static Map<String, Boolean> live() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        CompatRegistry.availability().forEach((id, on) -> {
            if (on) {
                out.put(id, true);
            }
        });
        return out;
    }
}
