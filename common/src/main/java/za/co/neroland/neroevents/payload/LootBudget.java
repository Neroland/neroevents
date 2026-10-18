package za.co.neroland.neroevents.payload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Pure: clamps a rolled stack list to a value-unit budget, so a generous loot table can never pay more than
 * the definition allows. Stacks are removed highest value first (ties: the later one goes first) until the
 * total is within the budget; the survivors keep their original order. A budget of 0 or less means "no cap".
 */
public final class LootBudget {

    private LootBudget() {
    }

    /** Removes stacks (highest value first) until Σ value ≤ budget; budget ≤ 0 ⇒ unchanged. Pure. */
    public static <T> List<T> clamp(List<T> stacks, long budget, ToLongFunction<T> value) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        if (budget <= 0L) {
            return List.copyOf(stacks);
        }
        long total = 0L;
        long[] values = new long[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            values[i] = Math.max(0L, value.applyAsLong(stacks.get(i)));
            total += values[i];
        }
        if (total <= budget) {
            return List.copyOf(stacks);
        }
        List<Integer> order = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingLong(i -> values[i]).reversed()
                .thenComparing(Comparator.<Integer>reverseOrder()));
        boolean[] removed = new boolean[stacks.size()];
        for (int index : order) {
            if (total <= budget) {
                break;
            }
            removed[index] = true;
            total -= values[index];
        }
        List<T> kept = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            if (!removed[i]) {
                kept.add(stacks.get(i));
            }
        }
        return List.copyOf(kept);
    }

    /** Σ value of {@code stacks}. */
    public static <T> long total(List<T> stacks, ToLongFunction<T> value) {
        long sum = 0L;
        for (T stack : stacks) {
            sum += Math.max(0L, value.applyAsLong(stack));
        }
        return sum;
    }
}
