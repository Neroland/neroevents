package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventConditions.ConditionResult;
import za.co.neroland.neroevents.event.EventConditions.TimeWindow;

/** Condition filtering against a hand-built snapshot. */
class EventConditionsTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final Identifier PLANET = Identifier.fromNamespaceAndPath("nerospace", "greenxertz");
    private static final Identifier SPACE_TAG = Identifier.fromNamespaceAndPath("neroland", "space/dimensions");
    private static final Identifier GATE = Identifier.fromNamespaceAndPath("nerospace", "hook/reveal_star_guide_page");

    private static SchedulerSnapshot snap(int online, Map<Identifier, Integer> perDim, Map<Identifier, Long> dayTimes,
            Map<Identifier, Integer> gates, Set<String> mods) {
        return new SchedulerSnapshot(0L, online, BandTable.DEFAULT.bandFor(online), false, ConsentMode.OFF, true,
                perDim, List.of(OVERWORLD, NETHER, PLANET), dayTimes, gates, Map.of(SPACE_TAG, Set.of(PLANET)), mods,
                Map.of(), Map.of(), 0, 0L, Map.of(), Map.of(), Long.MAX_VALUE);
    }

    private static SchedulerSnapshot everywhere(int online) {
        return snap(online, Map.of(OVERWORLD, online, NETHER, 1, PLANET, 1), Map.of(OVERWORLD, 6000L, NETHER, 18000L,
                PLANET, 14000L), Map.of(GATE, 2), Set.of("nerospace"));
    }

    private static EventConditions conditions(List<Identifier> dims, Optional<Identifier> tag, int min, int max,
            Optional<TimeWindow> window, Optional<List<String>> bands, Optional<Identifier> gate, int holders,
            List<String> mods, boolean allowEmpty) {
        return new EventConditions(dims, tag, min, max, window, bands, gate, holders, mods, allowEmpty);
    }

    @Test
    void dimensionListAndTagFilterCandidates() {
        ConditionResult list = conditions(List.of(NETHER), Optional.empty(), 1, -1, Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of(), false).evaluate(everywhere(3));
        assertEquals(List.of(NETHER), list.candidateDimensions());
        ConditionResult tag = conditions(List.of(), Optional.of(SPACE_TAG), 1, -1, Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of(), false).evaluate(everywhere(3));
        assertEquals(List.of(PLANET), tag.candidateDimensions());
        ConditionResult all = EventConditions.DEFAULT.evaluate(everywhere(3));
        assertEquals(3, all.candidateDimensions().size());
    }

    @Test
    void onlineBoundsAreEnforced() {
        EventConditions c = conditions(List.of(), Optional.empty(), 2, 4, Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of(), false);
        assertEquals("min_online", c.evaluate(everywhere(1)).firstFailure());
        assertTrue(c.evaluate(everywhere(2)).eligible());
        assertTrue(c.evaluate(everywhere(4)).eligible());
        assertEquals("min_online", c.evaluate(everywhere(5)).firstFailure());
    }

    @Test
    void bandNamesAreMatched() {
        EventConditions c = conditions(List.of(), Optional.empty(), 1, -1, Optional.empty(), Optional.of(List.of("5-9")),
                Optional.empty(), 1, List.of(), false);
        assertEquals("band", c.evaluate(everywhere(3)).firstFailure());
        assertTrue(c.evaluate(everywhere(6)).eligible());
    }

    @Test
    void gateHoldersMustReachTheRequiredCount() {
        EventConditions two = conditions(List.of(), Optional.empty(), 1, -1, Optional.empty(), Optional.empty(),
                Optional.of(GATE), 2, List.of(), false);
        assertTrue(two.evaluate(everywhere(3)).eligible());
        EventConditions three = conditions(List.of(), Optional.empty(), 1, -1, Optional.empty(), Optional.empty(),
                Optional.of(GATE), 3, List.of(), false);
        assertEquals("gate_holders", three.evaluate(everywhere(3)).firstFailure());
    }

    @Test
    void timeOfDayIsEvaluatedPerDimensionIncludingWrappingWindows() {
        EventConditions day = conditions(List.of(), Optional.empty(), 1, -1, Optional.of(new TimeWindow(0, 12000)),
                Optional.empty(), Optional.empty(), 1, List.of(), false);
        assertEquals(List.of(OVERWORLD), day.evaluate(everywhere(3)).candidateDimensions());
        EventConditions night = conditions(List.of(), Optional.empty(), 1, -1, Optional.of(new TimeWindow(13000, 1000)),
                Optional.empty(), Optional.empty(), 1, List.of(), false);
        assertEquals(Set.of(NETHER, PLANET), Set.copyOf(night.evaluate(everywhere(3)).candidateDimensions()));
        EventConditions never = conditions(List.of(OVERWORLD), Optional.empty(), 1, -1,
                Optional.of(new TimeWindow(13000, 1000)), Optional.empty(), Optional.empty(), 1, List.of(), false);
        assertEquals("time_of_day", never.evaluate(everywhere(3)).firstFailure());
        assertTrue(new TimeWindow(13000, 1000).matches(500L));
        assertFalse(new TimeWindow(13000, 1000).matches(1000L));
    }

    @Test
    void emptyDimensionsAreRemovedUnlessAllowed() {
        SchedulerSnapshot onlyOverworld = snap(2, Map.of(OVERWORLD, 2), Map.of(), Map.of(), Set.of());
        assertEquals(List.of(OVERWORLD), EventConditions.DEFAULT.evaluate(onlyOverworld).candidateDimensions());
        EventConditions allow = conditions(List.of(), Optional.empty(), 1, -1, Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of(), true);
        assertEquals(3, allow.evaluate(onlyOverworld).candidateDimensions().size());
        EventConditions planetOnly = conditions(List.of(PLANET), Optional.empty(), 1, -1, Optional.empty(),
                Optional.empty(), Optional.empty(), 1, List.of(), false);
        assertEquals("dimension", planetOnly.evaluate(onlyOverworld).firstFailure());
    }

    @Test
    void firstFailureCodesAndModChecks() {
        EventConditions mods = conditions(List.of(), Optional.empty(), 1, -1, Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of("nerocreatures"), false);
        assertEquals("mods", mods.evaluate(everywhere(3)).firstFailure());
        ConditionResult ok = EventConditions.DEFAULT.evaluate(everywhere(3));
        assertTrue(ok.eligible());
        assertNull(ok.firstFailure());
    }
}
