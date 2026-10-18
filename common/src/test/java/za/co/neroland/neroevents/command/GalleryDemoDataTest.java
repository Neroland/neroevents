package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.console.ConsolePayloads;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.console.EventConsoleBlockEntity;

/**
 * The demo HUD and console stay within the wire limits, cover every severity, carry nothing identifying and
 * use only shipped lang keys; the war script sweeps A to B and back; the demo is the owner's alone.
 */
class GalleryDemoDataTest {

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Identifier DIM = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final BlockPos ORIGIN = new BlockPos(100, 64, -200);
    private static final long[] AGES = {0L, 150L, 399L};

    private static Set<String> langKeys() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("common").resolve("src").resolve("main"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repo root not found");
        }
        Path lang = dir.resolve("common/src/main/resources/assets/neroevents/lang/en_us.json");
        JsonObject json = JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject();
        return new HashSet<>(json.keySet());
    }

    /** Every String component of a record, recursively through lists of records. */
    private static void strings(Object value, List<String> out) throws ReflectiveOperationException {
        if (value instanceof String s) {
            out.add(s);
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                strings(item, out);
            }
        } else if (value instanceof Identifier id) {
            out.add(id.toString());
        } else if (value != null && value.getClass().isRecord()) {
            for (RecordComponent c : value.getClass().getRecordComponents()) {
                strings(c.getAccessor().invoke(value), out);
            }
        }
    }

    @Test
    void scenesStayWithinTheWireLimitsAndCoverEverySeverity() {
        Set<Integer> severities = new HashSet<>();
        for (int scene = 0; scene < GalleryDemoData.SCENES; scene++) {
            for (long age : AGES) {
                HudState hud = GalleryDemoData.hud(scene, age, DIM, ORIGIN);
                ConsoleSnapshot console = GalleryDemoData.console(scene, age, DIM, ORIGIN);
                assertTrue(hud.entries().size() <= ConsolePayloads.MAX_HUD, "scene " + scene);
                assertFalse(hud.entries().isEmpty(), "scene " + scene);
                assertTrue(console.live().size() <= ConsolePayloads.MAX_ROWS);
                assertTrue(console.soon().size() <= ConsolePayloads.MAX_ROWS);
                assertTrue(console.past().size() <= ConsolePayloads.MAX_HISTORY);
                assertTrue(console.ledger().size() <= ConsolePayloads.MAX_LEDGER);
                hud.entries().forEach(e -> severities.add((int) e.severity()));
                for (HudEntry e : hud.entries()) {
                    assertEquals(DIM, e.dimension());
                    assertTrue(e.ticksLeft() >= 0);
                }
            }
            assertEquals(GalleryDemoData.topSeverity(scene),
                    (byte) GalleryDemoData.hud(scene, 0L, DIM, ORIGIN).entries().stream()
                            .mapToInt(HudEntry::severity).max().orElse(-1));
        }
        assertEquals(Set.of(0, 1, 2, 3, 4), severities);
        assertTrue(GalleryDemoData.hud(GalleryDemoData.SCENE_INFO, 0L, DIM, ORIGIN).entries().stream()
                .anyMatch(HudEntry::votingOpen), "scene 0 has the vote");
        assertTrue(GalleryDemoData.console(GalleryDemoData.SCENE_INFO, 0L, DIM, ORIGIN).soon().stream()
                .anyMatch(r -> r.votingOpen() && r.yes() == GalleryDemoData.VOTE_YES
                        && r.no() == GalleryDemoData.VOTE_NO && r.needed() == GalleryDemoData.VOTE_NEEDED
                        && r.myVote() == ConsolePayloads.NO_VOTE && r.voteBlock() == 0));
        assertTrue(GalleryDemoData.hud(GalleryDemoData.SCENE_COMBAT, 0L, DIM, ORIGIN).entries().stream()
                .anyMatch(e -> e.remaining() >= 0), "scene 3 has a wave count");
        HudEntry war = GalleryDemoData.hud(GalleryDemoData.SCENE_WAR, 0L, DIM, ORIGIN).entries().get(0);
        assertEquals(GalleryDemoData.WAR_OBJECTIVE, war.objective());
        assertTrue(war.marker());
        assertEquals(GalleryStations.FACTION_WAR.centre().at(ORIGIN), war.markerPos(), "the marker is the station");
    }

    @Test
    void nothingIsAUuidAndEveryKeyIsShipped() throws Exception {
        Set<String> keys = langKeys();
        for (int scene = 0; scene < GalleryDemoData.SCENES; scene++) {
            for (long age : AGES) {
                HudState hud = GalleryDemoData.hud(scene, age, DIM, ORIGIN);
                ConsoleSnapshot console = GalleryDemoData.console(scene, age, DIM, ORIGIN);
                List<String> all = new ArrayList<>();
                strings(hud, all);
                strings(console, all);
                for (String s : all) {
                    assertFalse(UUID_RE.matcher(s).find(), "UUID-shaped string " + s);
                }
                for (HudEntry e : hud.entries()) {
                    assertTrue(keys.contains(e.titleKey()), e.titleKey());
                    assertTrue(e.key() >= GalleryDemoData.K_MARKET && e.key() <= GalleryDemoData.K_WAR);
                }
                List<EventRow> rows = new ArrayList<>(console.live());
                rows.addAll(console.soon());
                for (EventRow r : rows) {
                    assertTrue(keys.contains(r.titleKey()), r.titleKey());
                    assertTrue(keys.contains(r.definitionTodoKey()) || keys.contains(r.todoKey())
                            || keys.contains(r.whatToDoKey()), r.titleKey() + " has no what-to-do line");
                    assertTrue(keys.contains(r.whatToDoKey()), r.whatToDoKey());
                    if (r.located()) {
                        assertEquals(GalleryStations.BOSS.centre().at(ORIGIN), r.location());
                    }
                }
                for (HistoryRow h : console.past()) {
                    assertTrue(keys.contains(h.titleKey()), h.titleKey());
                }
                for (LedgerRow l : console.ledger()) {
                    assertTrue(keys.contains(l.titleKey()), l.titleKey());
                }
                assertTrue(keys.contains(console.messageKey()), console.messageKey());
            }
        }
        assertTrue(keys.contains("console.neroevents.gallery.demo_locked"));
        ConsoleSnapshot locked = GalleryDemoData.console(0, 0L, DIM, ORIGIN, false,
                "console.neroevents.gallery.demo_locked", "");
        assertEquals("console.neroevents.gallery.demo_locked", locked.messageKey());
        assertFalse(locked.ok());
    }

    @Test
    void theWarScriptSweepsToBAndBackToA() {
        List<Byte> owners = new ArrayList<>();
        int captures = 0;
        for (int t = 0; t < GalleryWarScript.PERIOD; t++) {
            GalleryWarScript.Frame f = GalleryWarScript.at(t);
            assertTrue(f.progress() >= 0 && f.progress() <= GalleryWarScript.CAPTURE_TICKS, "t=" + t);
            assertEquals(t == 600 || t == 1400, f.capturedNow(), "t=" + t);
            boolean sweeping = t < 600 || (t >= 800 && t < 1400);
            assertEquals(sweeping, f.contested(), "t=" + t);
            if (f.capturedNow()) {
                captures++;
            }
            if (owners.isEmpty() || owners.get(owners.size() - 1) != f.owner()) {
                owners.add(f.owner());
            }
        }
        assertEquals(2, captures);
        assertEquals(List.of(GalleryWarScript.SIDE_A, GalleryWarScript.SIDE_B, GalleryWarScript.SIDE_A), owners);
        GalleryWarScript.Frame cue = GalleryWarScript.at(GalleryWarScript.CUE_T);
        assertEquals(GalleryWarScript.SIDE_B, cue.progressSide());
        assertEquals(420, cue.progress());
        assertTrue(cue.contested());
    }

    @Test
    void theDemoIsTheOwnersAloneAndIdleWithoutASession() {
        GalleryShowcase.setSessionForTests(null);
        assertEquals(EventConsoleBlockEntity.NONE, GalleryDemo.severityAt(null, BlockPos.ZERO));
        UUID owner = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID other = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Identifier nether = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
        GalleryShowcase.Session session = new GalleryShowcase.Session(owner, DIM, ORIGIN);
        assertTrue(session.matches(owner, DIM));
        assertFalse(session.matches(other, DIM), "another player sees nothing");
        assertFalse(session.matches(owner, nether), "nothing in another dimension");
        assertTrue(GalleryDemo.hudFor(session, owner, DIM).isPresent());
        assertTrue(GalleryDemo.hudFor(session, other, DIM).isEmpty());
        assertTrue(GalleryDemo.hudFor(session, owner, nether).isEmpty());
        assertTrue(GalleryDemo.hudFor(null, owner, DIM).isEmpty());
        assertTrue(session.covers(DIM, ORIGIN.offset(0, 1, -6)), "the front-row consoles are covered");
        assertFalse(session.covers(DIM, ORIGIN.offset(0, 1, 5)), "south of the marker is not the gallery");
        assertFalse(session.covers(nether, ORIGIN.offset(0, 1, -6)));
    }
}
