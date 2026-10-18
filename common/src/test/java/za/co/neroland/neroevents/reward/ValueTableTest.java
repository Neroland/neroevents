package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

/** Value units per line type. */
class ValueTableTest {

    private static final Identifier INGOT = Identifier.fromNamespaceAndPath("nerolandcore", "nero_alloy_ingot");
    private static final Identifier FACTION = Identifier.fromNamespaceAndPath("nerofactions", "guild");
    private final ValueTable values = ValueTable.DEFAULT;

    @Test
    void eachLineHasItsValue() {
        assertEquals(120L, values.valueOf(new RewardLine.Currency(120L, 0)));
        assertEquals(50L, values.valueOf(new RewardLine.Xp(200L, 0)), "200 XP × 0.25");
        assertEquals(30L, values.valueOf(new RewardLine.Item(INGOT, 3, OptionalLong.empty(), 0)), "3 × 10");
        assertEquals(0L, values.valueOf(new RewardLine.Reputation(FACTION, 10, 0)));
    }

    @Test
    void anItemValueOverridesTheDefault() {
        assertEquals(75L, values.valueOf(new RewardLine.Item(INGOT, 3, OptionalLong.of(25L), 0)));
    }

    @Test
    void totalAddsEveryLine() {
        assertEquals(120L + 50L + 30L, values.total(List.of(new RewardLine.Currency(120L, 0),
                new RewardLine.Xp(200L, 0), new RewardLine.Item(INGOT, 3, OptionalLong.empty(), 1),
                new RewardLine.Reputation(FACTION, 5, 0))));
    }

    @Test
    void theConfigValuationIsUsed() {
        ValueTable custom = new ValueTable(1.0D, 2L);
        assertEquals(200L, custom.valueOf(new RewardLine.Xp(200L, 0)));
        assertEquals(6L, custom.valueOf(new RewardLine.Item(INGOT, 3, OptionalLong.empty(), 0)));
    }
}
