package za.co.neroland.neroevents.compat.opac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionResult;
import za.co.neroland.neroevents.protect.ProtectionSources;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.compat.FakeOpacBridge;

/** Open Parties and Claims: every chunk the effect touches (plus the margin), memoised, failing closed. */
class OpacProtectionSourceTest {

    private static final Identifier OW = FakeWorldView.OVERWORLD;

    private FakeWorldView world;
    private FakeOpacBridge opac;

    @BeforeEach
    void setUp() {
        world = new FakeWorldView().allChunksLoaded(OW);
        ProtectionSources.resetHealth();
        opac = new FakeOpacBridge();
    }

    private OpacProtectionSource source(int margin) {
        return new OpacProtectionSource((d, x, z) -> opac.isChunkClaimed(null, d, x, z), () -> margin);
    }

    private ProtectionEvaluation evaluation(OpacProtectionSource source) {
        return new ProtectionQuery(List.of(source)).evaluation(world);
    }

    @Test
    void abstainsOnTheWholeDimensionWithoutAsking() {
        opac.claim(0, 0);
        assertTrue(evaluation(source(0)).mayAffectAll(Footprint.dimension(OW)).allowed());
        assertEquals(0, opac.calls);
    }

    @Test
    void pointInAClaimedChunkDenies() {
        opac.claim(0, 0);
        ProtectionResult result = evaluation(source(0)).mayAffect(OW, new BlockPos(8, 64, 8));
        assertFalse(result.allowed());
        assertEquals("opac", result.deniedBy());
        assertEquals("claimed chunk", result.detail());
        assertTrue(evaluation(source(0)).mayAffect(OW, new BlockPos(24, 64, 8)).allowed());
    }

    @Test
    void everyChunkOfTheBoxIsCheckedAndAnyClaimDenies() {
        opac.claim(2, 0);
        Footprint box = new Footprint(OW, 0, 60, 0, 47, 70, 15); // chunks 0..2 x 0
        assertFalse(evaluation(source(0)).mayAffectAll(box).allowed());
        assertEquals(3, opac.calls);
    }

    @Test
    void marginReachesTheNeighbouringClaim() {
        opac.claim(1, 0);
        Footprint box = new Footprint(OW, 0, 60, 0, 12, 70, 12); // chunk 0 only
        assertTrue(evaluation(source(0)).mayAffectAll(box).allowed());
        assertFalse(evaluation(source(8)).mayAffectAll(box).allowed(), "12 + 8 reaches chunk 1");
    }

    @Test
    void perChunkAnswersAreMemoisedPerEvaluation() {
        OpacProtectionSource source = source(0);
        ProtectionEvaluation evaluation = evaluation(source);
        Footprint box = new Footprint(OW, 0, 60, 0, 31, 70, 31); // 4 chunks
        assertTrue(evaluation.mayAffectAll(box).allowed());
        assertTrue(evaluation.mayAffectAll(box).allowed());
        assertEquals(4, opac.calls);
        assertTrue(evaluation(source).mayAffectAll(box).allowed());
        assertEquals(8, opac.calls, "a fresh evaluation asks again");
    }

    @Test
    void throwingBridgeDeniesAndIsMarkedFailing() {
        opac.throwing = true;
        ProtectionResult result = evaluation(source(0)).mayAffect(OW, BlockPos.ZERO);
        assertFalse(result.allowed());
        assertEquals("opac", result.deniedBy());
        assertTrue(ProtectionSources.health().get("opac").startsWith("failing"));
    }
}
