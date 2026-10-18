package za.co.neroland.neroevents.protect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.ExclusionZones.ExclusionZone;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.TestSources.CountingSource;
import za.co.neroland.neroevents.testing.TestSources.DenyAllSource;
import za.co.neroland.neroevents.testing.TestSources.ThrowingSource;

/** The protection contract and every built-in source. */
class ProtectionQueryTest {

    private static final Identifier OW = FakeWorldView.OVERWORLD;

    private FakeWorldView world;

    @BeforeEach
    void setUp() {
        world = new FakeWorldView().allChunksLoaded(OW);
        ProtectionSources.resetHealth();
    }

    private static Footprint box(int x0, int z0, int x1, int z1) {
        return new Footprint(OW, x0, 0, z0, x1, 10, z1);
    }

    @Test
    void firstDenyWinsInOrderAndAllAbstainAllows() {
        CountingSource before = new CountingSource("before");
        DenyAllSource deny = new DenyAllSource("deny");
        CountingSource after = new CountingSource("after");
        ProtectionResult result = new ProtectionQuery(List.of(before, deny, after)).evaluation(world)
                .mayAffectAll(box(0, 0, 5, 5));
        assertFalse(result.allowed());
        assertEquals("deny", result.deniedBy());
        assertEquals(1, before.calls);
        assertEquals(0, after.calls);
        assertTrue(new ProtectionQuery(List.of(before, after)).evaluation(world).mayAffect(OW, BlockPos.ZERO).allowed());
    }

    @Test
    void throwingSourceDeniesIsMarkedFailingAndWarnsOnce() {
        ThrowingSource thrower = new ThrowingSource("thrower_test");
        ProtectionQuery query = new ProtectionQuery(List.of(thrower));
        int warnedBefore = ProtectionSources.warnedCount();
        ProtectionResult first = query.evaluation(world).mayAffectAll(box(0, 0, 1, 1));
        query.evaluation(world).mayAffectAll(box(0, 0, 1, 1));
        assertFalse(first.allowed());
        assertEquals("thrower_test", first.deniedBy());
        assertTrue(ProtectionSources.health().get("thrower_test").startsWith("failing: IllegalStateException"));
        assertEquals(warnedBefore + 1, ProtectionSources.warnedCount());
    }

    @Test
    void footprintIteratesEveryChunkAndTheMemoIsHit() {
        NamespaceProtectionSource namespace = new NamespaceProtectionSource(() -> Set.of("nerosecurity"), () -> 0);
        ProtectionEvaluation evaluation = new ProtectionQuery(List.of(namespace)).evaluation(world);
        Footprint fp = box(0, 0, 47, 31); // 3 × 2 chunks
        assertTrue(evaluation.mayAffectAll(fp).allowed());
        assertEquals(6, world.isChunkLoadedCalls);
        assertEquals(6, world.blockEntityCalls);
        assertTrue(evaluation.mayAffectAll(fp).allowed());
        assertEquals(6, world.isChunkLoadedCalls, "second check is served from the memo");
        assertEquals(6, fp.chunkCoords().size());
    }

    @Test
    void exclusionZonesBoundariesAndConfigParsing() {
        List<String> issues = new ArrayList<>();
        List<ExclusionZone> zones = ExclusionZones.parseConfig(
                "base@minecraft:overworld:100,100,-100,-100; bad entry ;x@minecraft:the_nether:0,0,10,10;"
                        + "y@minecraft:overworld:a,b,c,d", issues);
        assertEquals(2, zones.size());
        assertEquals(2, issues.size());
        assertEquals(-100, zones.get(0).minX());
        ExclusionZoneSource source = new ExclusionZoneSource(() -> zones);
        ProtectionQuery query = new ProtectionQuery(List.of(source));
        assertFalse(query.evaluation(world).mayAffectAll(box(0, 0, 1, 1)).allowed(), "inside");
        assertFalse(query.evaluation(world).mayAffectAll(box(100, 100, 120, 120)).allowed(), "edge");
        assertTrue(query.evaluation(world).mayAffectAll(box(101, 101, 120, 120)).allowed(), "outside");
        assertTrue(query.evaluation(world).mayAffectAll(new Footprint(FakeWorldView.NETHER, 50, 0, 50, 60, 1, 60))
                .allowed(), "other dimension");
        assertEquals("base", query.evaluation(world).mayAffectAll(box(0, 0, 1, 1)).detail());
    }

    @Test
    void spawnProtectionUsesTheLargerRadiusChebyshev() {
        world.spawn(OW, new BlockPos(0, 64, 0));
        SpawnProtectionSource source = new SpawnProtectionSource(() -> 16);
        ProtectionQuery query = new ProtectionQuery(List.of(source));
        assertFalse(query.evaluation(world).mayAffect(OW, new BlockPos(16, 64, -16)).allowed());
        assertTrue(query.evaluation(world).mayAffect(OW, new BlockPos(17, 64, 0)).allowed());
        world.spawnProtectionRadius = 40;
        assertFalse(query.evaluation(world).mayAffect(OW, new BlockPos(30, 64, 30)).allowed());
        assertTrue(query.evaluation(world).mayAffect(FakeWorldView.NETHER, new BlockPos(0, 64, 0)).allowed(),
                "the nether has no world spawn");
    }

    @Test
    void namespaceGuardListedUnlistedUnloadedAndMargin() {
        FakeWorldView partial = new FakeWorldView().loadedChunks(OW, -2, -2, 2, 2)
                .blockEntityNamespace(OW, 1, 0, "nerosecurity").blockEntityNamespace(OW, -1, 0, "minecraft");
        NamespaceProtectionSource source = new NamespaceProtectionSource(() -> Set.of("nerosecurity", "nerocolonies"),
                () -> 8);
        ProtectionQuery query = new ProtectionQuery(List.of(source));
        ProtectionResult listed = query.evaluation(partial).mayAffect(OW, new BlockPos(20, 64, 5));
        assertFalse(listed.allowed());
        assertEquals("nerosecurity", listed.detail());
        assertTrue(query.evaluation(partial).mayAffect(OW, new BlockPos(-10, 64, 5)).allowed(), "unlisted");
        // 9 blocks west of chunk 1 with an 8-block margin: the margin reaches x=15, still chunk 0.
        assertTrue(query.evaluation(partial).mayAffect(OW, new BlockPos(7, 64, 5)).allowed());
        // 8 blocks west: the margin reaches x=16, chunk 1.
        assertFalse(query.evaluation(partial).mayAffect(OW, new BlockPos(8, 64, 5)).allowed(), "margin");
        ProtectionResult unloaded = query.evaluation(partial).mayAffect(OW, new BlockPos(40, 64, 5));
        assertFalse(unloaded.allowed());
        assertEquals("unloaded chunk", unloaded.detail());
        assertEquals(0, partial.surfaceYCalls, "no other chunk accessor is used");
    }

    @Test
    void stationDimensionIsDenied() {
        ProtectionQuery query = new ProtectionQuery(List.of(new StationProtectionSource()));
        assertFalse(query.evaluation(world).mayAffect(StationProtectionSource.STATION_DIMENSION, BlockPos.ZERO).allowed());
        assertTrue(query.evaluation(world).mayAffect(OW, BlockPos.ZERO).allowed());
    }

    @Test
    void securitySeamAbstains() {
        assertTrue(new ProtectionQuery(List.of(new SecurityProtectionSource())).evaluation(world)
                .mayAffectAll(box(0, 0, 100, 100)).allowed());
    }

    @Test
    void wholeDimensionFootprintMakesEveryBuiltInAbstain() {
        world.spawn(OW, BlockPos.ZERO);
        List<ProtectionSource> builtIns = List.of(
                new ExclusionZoneSource(() -> List.of(ExclusionZone.of("z", OW, -5, -5, 5, 5))),
                new SpawnProtectionSource(() -> 64), new StationProtectionSource(),
                new NamespaceProtectionSource(() -> Set.of("minecraft"), () -> 8), new SecurityProtectionSource());
        ProtectionQuery query = new ProtectionQuery(builtIns);
        assertTrue(query.evaluation(world).mayAffectAll(Footprint.dimension(OW)).allowed());
        assertTrue(query.evaluation(world).mayAffectAll(Footprint.dimension(StationProtectionSource.STATION_DIMENSION))
                .allowed());
    }
}
