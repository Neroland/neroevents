package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** The weighted roll is deterministic for a seed and independent of insertion order. */
class WeightedSelectionTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neroevents_test", path);
    }

    private static List<Identifier> picks(Map<Identifier, Integer> weights, long seed, int n) {
        Random rng = new Random(seed);
        List<Identifier> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(EventScheduler.pick(weights, rng).orElseThrow());
        }
        return out;
    }

    @Test
    void sameSeedGivesTheSameSequence() {
        Map<Identifier, Integer> weights = new LinkedHashMap<>();
        weights.put(id("a"), 10);
        weights.put(id("b"), 30);
        weights.put(id("c"), 60);
        weights.put(id("zero"), 0);
        List<Identifier> first = picks(weights, 42L, 200);
        List<Identifier> second = picks(weights, 42L, 200);
        assertEquals(first, second);
        assertFalse(first.contains(id("zero")));
        assertTrue(first.contains(id("a")) && first.contains(id("b")) && first.contains(id("c")));
    }

    @Test
    void insertionOrderDoesNotMatter() {
        Map<Identifier, Integer> forward = new LinkedHashMap<>();
        forward.put(id("a"), 1);
        forward.put(id("b"), 2);
        forward.put(id("c"), 3);
        Map<Identifier, Integer> reverse = new LinkedHashMap<>();
        reverse.put(id("c"), 3);
        reverse.put(id("b"), 2);
        reverse.put(id("a"), 1);
        assertEquals(picks(forward, 42L, 200), picks(reverse, 42L, 200));
    }

    @Test
    void emptyOrAllZeroPicksNothing() {
        assertTrue(EventScheduler.pick(Map.of(), new Random(1L)).isEmpty());
        assertTrue(EventScheduler.pick(Map.of(id("z"), 0), new Random(1L)).isEmpty());
    }

    @Test
    void reweightOverridesTheDefinitionWeight() {
        try (TestEngine t = new TestEngine()) {
            EventDefinition def = t.define("reweighted", new RecordingPayloadType("reweighted"), "{\"weight\": 10}");
            t.state.setReweight(def.id(), 0);
            EventScheduler.Eligibility e = EventScheduler.eligibility(def, t.engine.snapshot(), t.settings);
            assertEquals(0, e.effectiveWeight());
            t.state.setReweight(def.id(), 500);
            assertEquals(500, EventScheduler.eligibility(def, t.engine.snapshot(), t.settings).effectiveWeight());
        }
    }
}
