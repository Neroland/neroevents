package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Population band parsing, lookup and the participant estimate budgets use. */
class BandTableTest {

    @Test
    void defaultTableParsesCleanly() {
        BandTable table = BandTable.parse(BandTable.DEFAULT_SPEC, 4);
        assertTrue(table.issues().isEmpty());
        assertEquals(List.of("0-1", "2-4", "5-9", "10+"), table.bands().stream().map(PopulationBand::name).toList());
        assertEquals(2.0D, table.bands().get(0).intervalMultiplier());
        assertEquals(3, table.bands().get(3).concurrentCap());
    }

    @Test
    void bandForMapsEveryBoundary() {
        BandTable table = BandTable.DEFAULT;
        assertEquals("0-1", table.bandFor(0).name());
        assertEquals("0-1", table.bandFor(1).name());
        assertEquals("2-4", table.bandFor(2).name());
        assertEquals("2-4", table.bandFor(4).name());
        assertEquals("5-9", table.bandFor(5).name());
        assertEquals("5-9", table.bandFor(9).name());
        assertEquals("10+", table.bandFor(10).name());
        assertEquals("10+", table.bandFor(50).name());
    }

    @Test
    void invalidTablesFallBackToTheDefaultWithOneIssue() {
        for (String bad : List.of("0-1:2.0:1;3-4:1.0:1;5+:1:1", "0-2:2.0:1;2-4:1.0:1;5+:1:1", "0-1:x:1;2+:1:1",
                "0-1:20:1;2+:1:1", "0-1:1:9;2+:1:1", "0-1:1:1;2-4:1:1", "", "nonsense")) {
            BandTable table = BandTable.parse(bad, 4);
            assertEquals(1, table.issues().size(), bad);
            assertEquals(BandTable.DEFAULT.bands(), table.bands(), bad);
        }
    }

    @Test
    void expectedParticipantsUsesTheUpperBoundOrTheOnlineCount() {
        assertEquals(4, snapshot(BandTable.DEFAULT.bandFor(3), 3).expectedParticipants());
        assertEquals(10, snapshot(BandTable.DEFAULT.bandFor(10), 10).expectedParticipants());
        assertEquals(17, snapshot(BandTable.DEFAULT.bandFor(17), 17).expectedParticipants());
    }

    private static SchedulerSnapshot snapshot(PopulationBand band, int online) {
        return new SchedulerSnapshot(0L, online, band, false, ConsentMode.OFF, true, Map.of(), List.of(), Map.of(),
                Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), 0, 0L, Map.of(), Map.of(), Long.MAX_VALUE);
    }
}
