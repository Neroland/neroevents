package za.co.neroland.neroevents.compat.colonies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

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

/** NeroColonies claims: points, boxes, corner-only claims, the per-chunk memo and failing closed. */
class ColonyProtectionSourceTest {

    private static final Identifier OW = FakeWorldView.OVERWORLD;

    private FakeWorldView world;
    private final List<BlockPos> asked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = new FakeWorldView().allChunksLoaded(OW);
        ProtectionSources.resetHealth();
        asked.clear();
    }

    private ColonyProtectionSource source(BiPredicate<Identifier, BlockPos> claimed) {
        return new ColonyProtectionSource((dimension, pos) -> {
            asked.add(pos);
            return claimed.test(dimension, pos);
        });
    }

    private ProtectionEvaluation evaluation(ColonyProtectionSource source) {
        return new ProtectionQuery(List.of(source)).evaluation(world);
    }

    @Test
    void abstainsOnTheWholeDimensionWithoutAsking() {
        ColonyProtectionSource source = source((d, p) -> true);
        assertTrue(evaluation(source).mayAffectAll(Footprint.dimension(OW)).allowed());
        assertEquals(0, asked.size());
    }

    @Test
    void pointAsksOnceAtThatPosition() {
        BlockPos claimed = new BlockPos(5, 64, 5);
        ColonyProtectionSource source = source((d, p) -> p.equals(claimed));
        ProtectionResult denied = evaluation(source).mayAffect(OW, claimed);
        assertFalse(denied.allowed());
        assertEquals("colony", denied.deniedBy());
        assertEquals("colony claim", denied.detail());
        assertEquals(List.of(claimed), asked);
        assertTrue(evaluation(source).mayAffect(OW, new BlockPos(6, 64, 5)).allowed());
    }

    @Test
    void boxAsksEachChunkOnceAndTheFourCorners() {
        ColonyProtectionSource source = source((d, p) -> false);
        ProtectionEvaluation evaluation = evaluation(source);
        Footprint box = new Footprint(OW, 0, 60, 0, 31, 70, 15); // two chunks
        assertTrue(evaluation.mayAffectAll(box).allowed());
        assertEquals(2 + 4, asked.size());
        assertTrue(evaluation.mayAffectAll(box).allowed());
        assertEquals(2 + 4 + 4, asked.size(), "chunk answers are memoised per evaluation; corners are re-asked");
    }

    @Test
    void cornerOnlyClaimDenies() {
        // A radius claim that clips only the box's far corner, nowhere near a chunk centre.
        ColonyProtectionSource source = source((d, p) -> p.getX() == 31 && p.getZ() == 15);
        ProtectionResult result = evaluation(source).mayAffectAll(new Footprint(OW, 0, 60, 0, 31, 70, 15));
        assertFalse(result.allowed());
        assertEquals("colony", result.deniedBy());
    }

    @Test
    void unloadedDimensionDeniesWithoutFailing() {
        ColonyProtectionSource source = new ColonyProtectionSource((d, p) -> null);
        ProtectionResult point = evaluation(source).mayAffect(OW, BlockPos.ZERO);
        assertFalse(point.allowed());
        assertEquals("colony", point.deniedBy());
        assertEquals("dimension not loaded", point.detail());
        ProtectionResult box = evaluation(source).mayAffectAll(new Footprint(OW, 0, 60, 0, 31, 70, 15));
        assertFalse(box.allowed());
        assertEquals("colony", box.deniedBy());
        assertFalse(String.valueOf(ProtectionSources.health().get("colony")).startsWith("failing"));
    }

    @Test
    void throwingLookupDeniesAndIsMarkedFailing() {
        ColonyProtectionSource source = new ColonyProtectionSource((d, p) -> {
            throw new NoClassDefFoundError("za/co/neroland/nerocolonies/colony/ColonyApi");
        });
        ProtectionResult result = evaluation(source).mayAffect(OW, BlockPos.ZERO);
        assertFalse(result.allowed());
        assertEquals("colony", result.deniedBy());
        assertTrue(ProtectionSources.health().get("colony").startsWith("failing"));
    }
}
