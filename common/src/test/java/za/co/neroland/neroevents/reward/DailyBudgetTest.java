package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** The UTC calendar day of the server clock. */
class DailyBudgetTest {

    private static long at(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    void theDayTurnsAtUtcMidnight() {
        long before = DailyBudget.epochDay(at("2026-10-17T23:59:59.999Z"));
        long after = DailyBudget.epochDay(at("2026-10-18T00:00:00.000Z"));
        assertEquals(before + 1L, after);
        assertEquals(java.time.LocalDate.parse("2026-10-18").toEpochDay(), after);
    }

    @Test
    void rolledResetsOnANewDayAndKeepsTheSameDay() {
        DailyBudget today = new DailyBudget(DailyBudget.epochDay(at("2026-10-17T12:00:00Z")), 400L);
        assertSame(today, today.rolled(at("2026-10-17T23:59:59.999Z")));
        DailyBudget tomorrow = today.rolled(at("2026-10-18T00:00:00Z"));
        assertEquals(0L, tomorrow.spent());
        assertEquals(today.epochDay() + 1L, tomorrow.epochDay());
    }

    @Test
    void remainingIsNeverNegative() {
        assertEquals(600L, new DailyBudget(0L, 400L).remaining(1000L));
        assertEquals(0L, new DailyBudget(0L, 1400L).remaining(1000L));
        assertEquals(0L, new DailyBudget(0L, 0L).remaining(0L));
    }
}
