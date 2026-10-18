package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.client.HistorySentences.Place;
import za.co.neroland.neroevents.client.HistorySentences.Result;
import za.co.neroland.neroevents.client.HistorySentences.Sentence;
import za.co.neroland.neroevents.client.HistorySentences.Title;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;

/**
 * The Past and Mine tabs speak in sentences: one template per type and result, a generic template otherwise,
 * counts instead of names, and never a bare id. Every key a sentence can use exists in the shipped lang file.
 */
class HistorySentencesTest {

    private static final Identifier CINDARA = Identifier.fromNamespaceAndPath("nerospace", "cindara");

    private static HistoryRow row(String type, EventPhase terminal, EventOutcome outcome, long age, int participants) {
        return new HistoryRow(type, "event.neroevents." + type + ".title", CINDARA, (byte) terminal.ordinal(),
                (byte) (outcome == null ? -1 : outcome.ordinal()), age, participants);
    }

    @Test
    void aRepelledRaidReadsAsASentenceWithACount() {
        Sentence s = HistorySentences.sentence(row("pirate_raid", EventPhase.DONE, EventOutcome.SUCCESS, 7200L, 6));
        assertEquals("history.neroevents.with_count", s.key());
        Sentence core = (Sentence) s.args().get(0);
        assertEquals("history.neroevents.pirate_raid.success", core.key());
        assertEquals(new Place(CINDARA), core.args().get(0));
        assertEquals(Sentence.of("time.neroevents.ago.hours", 2L), core.args().get(1));
        assertEquals(Sentence.of("history.neroevents.took_part.many", 6), s.args().get(1));
    }

    @Test
    void resultsMapFromTerminalAndOutcome() {
        assertEquals(Result.SUCCESS, HistorySentences.result(EventPhase.DONE.ordinal(), EventOutcome.SUCCESS.ordinal()));
        assertEquals(Result.FAILURE, HistorySentences.result(EventPhase.DONE.ordinal(), EventOutcome.FAILURE.ordinal()));
        assertEquals(Result.FAILURE, HistorySentences.result(EventPhase.DONE.ordinal(), EventOutcome.ESCAPED.ordinal()));
        assertEquals(Result.OVER, HistorySentences.result(EventPhase.DONE.ordinal(), EventOutcome.EXPIRED.ordinal()));
        assertEquals(Result.OVER, HistorySentences.result(EventPhase.DONE.ordinal(), -1));
        assertEquals(Result.SKIPPED, HistorySentences.result(EventPhase.SKIPPED.ordinal(), -1));
        assertEquals(Result.CANCELLED, HistorySentences.result(EventPhase.CANCELLED.ordinal(), -1));
        assertEquals(Result.OVER, HistorySentences.result(99, 99));
    }

    @Test
    void unknownTypesUseTheGenericTemplateWithTheTitle() {
        Sentence s = HistorySentences.sentence(row("datapack_thing", EventPhase.CANCELLED, null, 30L, 0));
        assertEquals("history.neroevents.plain", s.key(), "a called-off event with nobody in it has no count");
        Sentence core = (Sentence) s.args().get(0);
        assertEquals("history.neroevents.generic.cancelled", core.key());
        assertEquals(new Title("event.neroevents.datapack_thing.title"), core.args().get(2));
        assertEquals(Sentence.of("time.neroevents.ago.now"), core.args().get(1));
    }

    @Test
    void agesReadNaturally() {
        assertEquals(Sentence.of("time.neroevents.ago.now"), HistorySentences.ago(5));
        assertEquals(Sentence.of("time.neroevents.ago.minute"), HistorySentences.ago(70));
        assertEquals(Sentence.of("time.neroevents.ago.minutes", 5L), HistorySentences.ago(300));
        assertEquals(Sentence.of("time.neroevents.ago.hour"), HistorySentences.ago(3600));
        assertEquals(Sentence.of("time.neroevents.ago.hours", 5L), HistorySentences.ago(5 * 3600));
        assertEquals(Sentence.of("time.neroevents.ago.yesterday"), HistorySentences.ago(30 * 3600));
        assertEquals(Sentence.of("time.neroevents.ago.days", 3L), HistorySentences.ago(3 * 86400));
        assertEquals(Sentence.of("time.neroevents.ago.now"), HistorySentences.ago(-10));
    }

    @Test
    void countsAreNumbersNeverNames() {
        assertEquals(Sentence.of("history.neroevents.took_part.none"), HistorySentences.tookPart(0));
        assertEquals(Sentence.of("history.neroevents.took_part.one"), HistorySentences.tookPart(1));
        assertEquals(Sentence.of("history.neroevents.took_part.many", 12), HistorySentences.tookPart(12));
    }

    @Test
    void ledgerRowsListOnlyWhatWasPaid() {
        Sentence s = HistorySentences.ledger(new LedgerRow("event.neroevents.core.meteor_shower.title", 2, 40L, 0L, 90000L));
        assertEquals("ledger.neroevents.row", s.key());
        Sentence what = (Sentence) s.args().get(1);
        assertEquals("ledger.neroevents.list2", what.key());
        assertEquals(Sentence.of("ledger.neroevents.items", 2), what.args().get(0));
        assertEquals(Sentence.of("ledger.neroevents.credits", 40L), what.args().get(1));
        assertEquals(Sentence.of("time.neroevents.ago.yesterday"), s.args().get(2));
        Sentence none = (Sentence) HistorySentences.ledger(new LedgerRow("", 0, 0L, 0L, 10L)).args().get(1);
        assertEquals("ledger.neroevents.nothing", none.key());
    }

    @Test
    void noSentenceArgumentIsABareId() {
        for (String type : HistorySentences.TYPES) {
            for (EventPhase terminal : new EventPhase[] {EventPhase.DONE, EventPhase.SKIPPED, EventPhase.CANCELLED}) {
                walk(HistorySentences.sentence(row(type, terminal, EventOutcome.SUCCESS, 100L, 3)), new ArrayList<>());
            }
        }
    }

    private static void walk(Sentence s, List<String> keys) {
        keys.add(s.key());
        for (Object arg : s.args()) {
            if (arg instanceof Sentence nested) {
                walk(nested, keys);
            } else if (arg instanceof String text) {
                assertFalse(text.contains(":"), "raw id in a sentence: " + text);
            } else {
                assertTrue(arg instanceof Place || arg instanceof Title || arg instanceof Number,
                        "unexpected argument " + arg);
            }
        }
    }

    @Test
    void everyTemplateExistsInTheShippedLangFile() throws IOException {
        Path lang = repoRoot().resolve("common/src/main/resources/assets/neroevents/lang/en_us.json");
        JsonObject json = JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> types = new ArrayList<>(HistorySentences.TYPES);
        types.add("generic");
        for (String type : types) {
            for (Result result : Result.values()) {
                String key = HistorySentences.templateKey("generic".equals(type) ? "nope" : type, result);
                assertTrue(json.has(key), "missing " + key);
            }
        }
        for (String key : new String[] {"history.neroevents.with_count", "history.neroevents.plain",
                "history.neroevents.took_part.none", "history.neroevents.took_part.one",
                "history.neroevents.took_part.many", "time.neroevents.ago.now", "time.neroevents.ago.minute",
                "time.neroevents.ago.minutes", "time.neroevents.ago.hour", "time.neroevents.ago.hours",
                "time.neroevents.ago.yesterday", "time.neroevents.ago.days", "ledger.neroevents.row",
                "ledger.neroevents.item", "ledger.neroevents.items", "ledger.neroevents.credits", "ledger.neroevents.xp",
                "ledger.neroevents.list1", "ledger.neroevents.list2", "ledger.neroevents.list3",
                "ledger.neroevents.nothing"}) {
            assertTrue(json.has(key), "missing " + key);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("tools").resolve("lang"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repository root not found");
        }
        return dir;
    }
}
