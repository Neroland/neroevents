package za.co.neroland.neroevents.storm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.storm.DrainPlan.Candidate;
import za.co.neroland.neroevents.storm.DrainPlan.Drain;
import za.co.neroland.neroevents.storm.DrainPlan.Limits;

class DrainPlanTest {

    private static final Limits DEFAULTS = new Limits(0.01D, 5000L, 0.2D, 64);
    private static final BlockPos P = new BlockPos(1, 2, 3);

    private static Candidate c(long amount, long capacity, boolean exposed, boolean allowed) {
        return new Candidate(P, amount, capacity, exposed, allowed);
    }

    @Test
    void fractionScalesByIntensityAndMultiplier() {
        assertEquals(0.01D, DrainPlan.fraction(StormVariant.SOLAR, 3, DEFAULTS), 1e-12);
        assertEquals(0.01D / 3.0D, DrainPlan.fraction(StormVariant.SOLAR, 1, DEFAULTS), 1e-12);
        assertEquals(0.015D, DrainPlan.fraction(StormVariant.ION, 3, DEFAULTS), 1e-12);
        assertEquals(0.0D, DrainPlan.fraction(StormVariant.DUST, 3, DEFAULTS));
        assertEquals(0.0D, DrainPlan.fraction(StormVariant.CRYO, 3, DEFAULTS));
    }

    @Test
    void extractIsFractionOfStored() {
        List<Drain> plan = DrainPlan.of(List.of(c(100_000L, 200_000L, true, true)), StormVariant.SOLAR, 3, DEFAULTS);
        assertEquals(List.of(new Drain(P, 1000L)), plan);
        List<Drain> ion = DrainPlan.of(List.of(c(100_000L, 200_000L, true, true)), StormVariant.ION, 2, DEFAULTS);
        assertEquals(List.of(new Drain(P, 1000L)), ion, "0.01 x 2/3 x 1.5 = 1 %");
    }

    @Test
    void perPassCapHolds() {
        List<Drain> plan = DrainPlan.of(List.of(c(10_000_000L, 10_000_000L, true, true)), StormVariant.ION, 3,
                DEFAULTS);
        assertEquals(5000L, plan.get(0).extract());
    }

    @Test
    void neverBelowTheFloor() {
        // 20 % floor of 10 000 = 2 000; stored 2 050 → at most 50
        Limits big = new Limits(0.2D, 1_000_000L, 0.2D, 64);
        List<Drain> plan = DrainPlan.of(List.of(c(2_050L, 10_000L, true, true)), StormVariant.SOLAR, 3, big);
        assertEquals(50L, plan.get(0).extract());
        assertTrue(DrainPlan.of(List.of(c(2_000L, 10_000L, true, true)), StormVariant.SOLAR, 3, big).isEmpty());
        assertTrue(DrainPlan.of(List.of(c(1_000L, 10_000L, true, true)), StormVariant.SOLAR, 3, big).isEmpty());
        // repeated passes converge on the floor and stop there
        long amount = 10_000L;
        for (int pass = 0; pass < 200; pass++) {
            List<Drain> p = DrainPlan.of(List.of(c(amount, 10_000L, true, true)), StormVariant.SOLAR, 3, big);
            amount -= p.isEmpty() ? 0L : p.get(0).extract();
        }
        assertTrue(amount >= 2_000L, "never below 20 %: " + amount);
    }

    @Test
    void shelteredOrProtectedOrEmptyIsUntouched() {
        assertTrue(DrainPlan.of(List.of(c(100_000L, 200_000L, false, true)), StormVariant.SOLAR, 3, DEFAULTS)
                .isEmpty(), "not exposed");
        assertTrue(DrainPlan.of(List.of(c(100_000L, 200_000L, true, false)), StormVariant.SOLAR, 3, DEFAULTS)
                .isEmpty(), "not allowed");
        assertTrue(DrainPlan.of(List.of(c(0L, 1_000_000L, true, true)), StormVariant.SOLAR, 3, DEFAULTS).isEmpty());
        assertTrue(DrainPlan.of(List.of(c(100_000L, 200_000L, true, true)), StormVariant.DUST, 3, DEFAULTS)
                .isEmpty(), "dust does not drain");
    }

    @Test
    void candidateCapKeepsTheFirst64() {
        List<Candidate> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new Candidate(new BlockPos(i, 0, 0), 100_000L, 200_000L, true, true));
        }
        List<Candidate> capped = DrainPlan.cap(many, 64);
        assertEquals(64, capped.size());
        assertEquals(new BlockPos(63, 0, 0), capped.get(63).pos());
        assertEquals(64, DrainPlan.of(capped, StormVariant.SOLAR, 3, DEFAULTS).size());
        assertEquals(3, DrainPlan.cap(many.subList(0, 3), 64).size());
    }
}
