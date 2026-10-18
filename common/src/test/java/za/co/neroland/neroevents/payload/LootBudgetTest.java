package za.co.neroland.neroevents.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.Test;

/** {@link LootBudget}: highest value removed first, the total fits, budget 0 is "no cap", empty stays empty. */
class LootBudgetTest {

    /** A stack is "name:value". */
    private static final ToLongFunction<String> VALUE = s -> Long.parseLong(s.substring(s.indexOf(':') + 1));

    @Test
    void removesHighestValueFirstAndKeepsOrder() {
        List<String> kept = LootBudget.clamp(List.of("a:10", "b:50", "c:20", "d:30"), 60, VALUE);
        assertEquals(List.of("a:10", "c:20", "d:30"), kept);
        kept = LootBudget.clamp(List.of("a:10", "b:50", "c:20", "d:30"), 35, VALUE);
        assertEquals(List.of("a:10", "c:20"), kept);
    }

    @Test
    void totalNeverExceedsTheBudget() {
        List<String> many = java.util.stream.IntStream.range(0, 40).mapToObj(i -> "s" + i + ":" + (i % 7 + 1) * 5)
                .toList();
        for (long budget : new long[] {1, 7, 50, 120, 300, 1000}) {
            List<String> kept = LootBudget.clamp(many, budget, VALUE);
            assertTrue(LootBudget.total(kept, VALUE) <= budget, "budget " + budget);
        }
    }

    @Test
    void zeroBudgetIsUnchanged() {
        List<String> stacks = List.of("a:100", "b:200");
        assertEquals(stacks, LootBudget.clamp(stacks, 0, VALUE));
        assertEquals(stacks, LootBudget.clamp(stacks, -5, VALUE));
    }

    @Test
    void emptyListStaysEmpty() {
        assertTrue(LootBudget.clamp(List.<String>of(), 10, VALUE).isEmpty());
    }

    @Test
    void withinBudgetIsUnchanged() {
        List<String> stacks = List.of("a:10", "b:20");
        assertEquals(stacks, LootBudget.clamp(stacks, 30, VALUE));
    }
}
