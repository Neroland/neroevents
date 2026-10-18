package za.co.neroland.neroevents.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.Severity;
import za.co.neroland.neroevents.war.FactionWarPayload;

/**
 * Data minimisation of the console and HUD packets: no player name or UUID ever crosses the wire, the instance
 * UUID is replaced by an opaque key, raid and invasion targets never carry a location, a private location is
 * never sent, the HUD marker appears only inside the event's bounds, tallies are numbers, and every payload
 * survives an encode/decode round trip unchanged.
 */
class ConsolePayloadsTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final BlockPos ANCHOR = new BlockPos(1234, 70, -5678);

    private static EventView view(UUID id, EventCategory category, EventPhase phase, boolean locationPublic,
            String type) {
        return new EventView(id, "ab12", Identifier.fromNamespaceAndPath("neroevents", type),
                Identifier.fromNamespaceAndPath("neroevents", type), category, category.defaultSeverity(),
                "event.neroevents." + type + ".title", "event.neroevents." + type + ".what_to_do", OVERWORLD, phase,
                EventOrigin.SCHEDULER, 1000L, 7000L, 400L, 6, phase == EventPhase.VOTING,
                12, 3, locationPublic, locationPublic ? ANCHOR : null, 2, 5, null, null);
    }

    private static <T> byte[] encode(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        byte[] out = new byte[buf.readableBytes()];
        buf.getBytes(0, out);
        return out;
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "trailing bytes");
        return back;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .array();
    }

    @Test
    void raidAndInvasionTargetsNeverCarryALocation() {
        for (EventCategory category : new EventCategory[] {EventCategory.RAID, EventCategory.INVASION}) {
            EventView raid = view(UUID.randomUUID(), category, EventPhase.ACTIVE, true, "pirate_raid");
            EventRow row = ConsoleRows.row(raid, 3, ConsolePayloads.NO_VOTE, Optional.empty());
            assertFalse(row.located(), category + " row must not be located");
            assertEquals(BlockPos.ZERO, row.location());
            HudEntry hud = ConsoleRows.hud(raid, 10L, 4, "", ANCHOR, OVERWORLD, 64);
            assertFalse(hud.marker(), category + " must never get a marker");
            byte[] bytes = encode(ConsolePayloads.HudState.STREAM_CODEC, new HudState(List.of(hud)));
            FriendlyByteBuf posOnly = new FriendlyByteBuf(Unpooled.buffer());
            posOnly.writeBlockPos(ANCHOR);
            byte[] pos = new byte[posOnly.readableBytes()];
            posOnly.getBytes(0, pos);
            assertFalse(contains(bytes, pos), "the raid target's position leaked into the HUD packet");
        }
    }

    @Test
    void privateLocationsAreNeverSent() {
        EventView hidden = view(UUID.randomUUID(), EventCategory.HAZARD, EventPhase.ACTIVE, false, "meteor_shower");
        assertFalse(ConsoleRows.row(hidden, 1, ConsolePayloads.NO_VOTE, Optional.empty()).located());
        assertFalse(ConsoleRows.hud(hidden, 0L, -1, "", ANCHOR, OVERWORLD, 64).marker());
    }

    @Test
    void markerOnlyInsideBoundsAndInTheSameDimension() {
        EventView drop = view(UUID.randomUUID(), EventCategory.REWARD, EventPhase.ACTIVE, true, "supply_drop");
        assertTrue(ConsoleRows.hud(drop, 0L, -1, "", ANCHOR.offset(30, 0, 30), OVERWORLD, 64).marker());
        assertFalse(ConsoleRows.hud(drop, 0L, -1, "", ANCHOR.offset(200, 0, 0), OVERWORLD, 64).marker(),
                "outside the bounds radius");
        assertFalse(ConsoleRows.hud(drop, 0L, -1, "", ANCHOR, Identifier.withDefaultNamespace("the_nether"), 64)
                .marker(), "another dimension");
        assertFalse(ConsoleRows.hud(drop, 0L, -1, "", ANCHOR, OVERWORLD, 0).marker(), "dimension-wide bounds");
        EventView announced = view(UUID.randomUUID(), EventCategory.REWARD, EventPhase.ANNOUNCED, true, "supply_drop");
        assertFalse(ConsoleRows.hud(announced, 0L, -1, "", ANCHOR, OVERWORLD, 64).marker(), "not active yet");
        // The console may still show where a public event is (anywhere), as a row location.
        assertTrue(ConsoleRows.row(drop, 1, ConsolePayloads.NO_VOTE, Optional.empty()).located());
    }

    @Test
    void theInstanceUuidNeverCrossesTheWire() {
        UUID id = UUID.randomUUID();
        EventView v = view(id, EventCategory.REWARD, EventPhase.VOTING, true, "supply_drop");
        EventRow row = ConsoleRows.row(v, 2, (byte) 1, Optional.empty());
        assertEquals(ConsoleRows.keyOf(id), row.key());
        assertNotEquals((int) id.getMostSignificantBits(), row.key());
        ConsoleSnapshot snap = snapshot(List.of(row), List.of(row));
        byte[] bytes = encode(ConsoleSnapshot.STREAM_CODEC, snap);
        assertFalse(contains(bytes, uuidBytes(id)), "instance UUID in the snapshot");
        assertFalse(contains(bytes, id.toString().getBytes(StandardCharsets.UTF_8)), "instance UUID text");
        assertFalse(contains(bytes, "ab12".getBytes(StandardCharsets.UTF_8)), "short id in the snapshot");
    }

    @Test
    void noPayloadRecordCarriesAUuidOrAName() {
        for (Class<?> type : new Class<?>[] {EventRow.class, HistoryRow.class, LedgerRow.class, HudEntry.class,
                ConsoleSnapshot.class, HudState.class}) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertNotEquals(UUID.class, component.getType(), type.getSimpleName() + "." + component.getName());
                String name = component.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(name.contains("playerid") || name.contains("profile"), type.getSimpleName() + "." + name);
                assertFalse(name.contains("name") || name.contains("uuid") || name.contains("voter"),
                        type.getSimpleName() + "." + name);
            }
        }
    }

    @Test
    void theWarObjectiveLineNeverCarriesANamespacedId() {
        Identifier red = Identifier.fromNamespaceAndPath("nerofactions", "red");
        Identifier blue = Identifier.fromNamespaceAndPath("nerofactions", "blue_team");
        String line = FactionWarPayload.objectiveLine(FactionWarPayload.sideName(Optional.empty(), red), 34,
                FactionWarPayload.sideName(Optional.empty(), blue), 21);
        assertEquals("Red 34 · Blue Team 21", line);
        assertFalse(line.contains(":"), line);
        assertEquals("The Reds", FactionWarPayload.sideName(Optional.of("The Reds"), red));
        assertFalse(FactionWarPayload.sideName(Optional.of(" "), red).contains(":"));
    }

    @Test
    void definitionTodoKeyFollowsTheDefinitionId() {
        assertEquals("event.neroevents.core.meteor_shower.todo",
                ConsoleRows.definitionTodoKey(Identifier.fromNamespaceAndPath("neroevents", "core/meteor_shower")));
        EventView v = view(UUID.randomUUID(), EventCategory.HAZARD, EventPhase.ACTIVE, false, "meteor_shower");
        EventRow row = ConsoleRows.row(v, 1, ConsolePayloads.NO_VOTE, Optional.empty());
        assertEquals("event.neroevents.meteor_shower.todo", row.definitionTodoKey());
        assertEquals("event.neroevents.meteor_shower.todo", row.todoKey());
    }

    @Test
    void phaseSwitchShowsSupplyDropsAsInfoUntilActive() {
        assertEquals(Severity.INFO, ConsoleRows.severityFor(Severity.REWARD, EventPhase.ANNOUNCED));
        assertEquals(Severity.INFO, ConsoleRows.severityFor(Severity.REWARD, EventPhase.VOTING));
        assertEquals(Severity.REWARD, ConsoleRows.severityFor(Severity.REWARD, EventPhase.ACTIVE));
        assertEquals(Severity.COMBAT, ConsoleRows.severityFor(Severity.COMBAT, EventPhase.ANNOUNCED));
    }

    @Test
    void everyPayloadRoundTrips() {
        EventView v = view(UUID.randomUUID(), EventCategory.REWARD, EventPhase.VOTING, true, "supply_drop");
        EventRow row = ConsoleRows.row(v, 2, (byte) 0, Optional.of(ConsoleIntents.Deny.VOTING_OFF));
        assertEquals(row, roundTrip(EventRowCodec.CODEC, row));
        ConsoleSnapshot snap = snapshot(List.of(row), List.of());
        assertEquals(snap, roundTrip(ConsoleSnapshot.STREAM_CODEC, snap));
        HudState hud = new HudState(List.of(ConsoleRows.hud(view(UUID.randomUUID(), EventCategory.WAR,
                EventPhase.ACTIVE, true, "faction_war"), 5L, -1, "Red 34 · Blue 21", ANCHOR, OVERWORLD, 96)));
        assertEquals(hud, roundTrip(HudState.STREAM_CODEC, hud));
        assertEquals(ConsoleIntents.Deny.VOTING_OFF, ConsoleIntents.Deny.fromWire(row.voteBlock()));
    }

    @Test
    void listsAreCappedOnTheWire() {
        EventView v = view(UUID.randomUUID(), EventCategory.HAZARD, EventPhase.ACTIVE, false, "meteor_shower");
        EventRow row = ConsoleRows.row(v, 1, ConsolePayloads.NO_VOTE, Optional.empty());
        List<EventRow> many = java.util.Collections.nCopies(ConsolePayloads.MAX_ROWS + 5, row);
        ConsoleSnapshot back = roundTrip(ConsoleSnapshot.STREAM_CODEC, snapshot(many, List.of()));
        assertEquals(ConsolePayloads.MAX_ROWS, back.live().size());
    }

    private static ConsoleSnapshot snapshot(List<EventRow> live, List<EventRow> soon) {
        return new ConsoleSnapshot(live, soon,
                List.of(new HistoryRow("pirate_raid", "event.neroevents.pirate_raid.title", OVERWORLD,
                        (byte) EventPhase.DONE.ordinal(), (byte) 0, 7200L, 6)),
                false, (byte) 1, false, 3, 40L, true, false, true, false, 7, 14,
                List.of(new LedgerRow("event.neroevents.core.meteor_shower.title", 2, 40L, 15L, 90000L)), true,
                "console.neroevents.vote.recorded_yes", "");
    }

    /** The row codec on its own (rows are parts of payloads, not payloads themselves). */
    private static final class EventRowCodec {
        static final StreamCodec<FriendlyByteBuf, EventRow> CODEC = StreamCodec.of(EventRow::write, EventRow::read);
    }
}
