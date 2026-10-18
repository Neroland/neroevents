package za.co.neroland.neroevents.payload.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** The pure crater plan: pinned cell counts per radius, one pocket, ore count, the soft plan, determinism. */
class CraterPlanTest {

    private static final BlockPos IMPACT = new BlockPos(10, 70, -5);

    private static CraterPlan plan(int r, int ore, long seed) {
        return CraterPlan.of(IMPACT, r, false, ore, 1, new Random(seed));
    }

    @Test
    void hemisphereCountsArePinned() {
        int[][] expected = { // r, air, crust+ore, slag
            {2, 6, 12, 16},
            {3, 27, 28, 20},
            {4, 72, 48, 32},
        };
        for (int[] row : expected) {
            CraterPlan p = plan(row[0], 0, 1L);
            assertEquals(row[1], p.count(CraterPlan.Kind.AIR), "air r" + row[0]);
            assertEquals(row[2], p.count(CraterPlan.Kind.CRUST) + p.count(CraterPlan.Kind.ORE), "crust r" + row[0]);
            assertEquals(row[3], p.count(CraterPlan.Kind.SLAG), "slag r" + row[0]);
            assertEquals(1, p.count(CraterPlan.Kind.POCKET), "pocket r" + row[0]);
        }
    }

    @Test
    void exactlyOnePocketUnderTheImpactColumnAndItIsLast() {
        CraterPlan p = plan(3, 2, 7L);
        CraterPlan.Cell pocket = p.pocket();
        assertNotNull(pocket);
        assertEquals(new BlockPos(IMPACT.getX(), IMPACT.getY() - 3, IMPACT.getZ()), pocket.pos());
        assertEquals(CraterPlan.Kind.POCKET, p.cells().get(p.cells().size() - 1).kind());
    }

    @Test
    void oreCountIsHonouredAndDistinct() {
        for (int ore = 0; ore <= 6; ore++) {
            CraterPlan p = plan(2, ore, 3L);
            assertEquals(ore, p.count(CraterPlan.Kind.ORE));
        }
        Set<BlockPos> cells = new HashSet<>();
        for (CraterPlan.Cell c : plan(4, 6, 5L).cells()) {
            assertTrue(cells.add(c.pos()), "every planned cell is distinct: " + c.pos());
        }
    }

    @Test
    void softPlanIsExactlyOnePocketAboveTheImpact() {
        CraterPlan p = CraterPlan.of(IMPACT, 4, true, 3, 1, new Random(1L));
        assertEquals(List.of(new CraterPlan.Cell(IMPACT.above(), CraterPlan.Kind.POCKET, -1)), p.cells());
    }

    @Test
    void deterministicBySeed() {
        assertEquals(plan(3, 3, 99L), plan(3, 3, 99L));
        assertEquals(plan(4, 2, 1234L).cells(), plan(4, 2, 1234L).cells());
    }
}
