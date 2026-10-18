package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.persist.EventEngineState;
import za.co.neroland.neroevents.event.persist.InstanceRecord;
import za.co.neroland.neroevents.protect.ExclusionZones.ExclusionZone;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** The resumable record round-trips, and the restore table behaves as documented. */
class ResumableRecordTest {

    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents_test", "resume");
    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("neroevents_test", "resume_type");

    private static InstanceRecord record(EventPhase phase, EventOrigin origin, Optional<BlockPos> anchor) {
        CompoundTag state = new CompoundTag();
        state.putInt("ticks", 7);
        return new InstanceRecord(UUID.randomUUID(), DEF, TYPE, FakeWorldView.OVERWORLD, anchor, origin, 42L, 10L,
                110L, 310L, phase, 50L, 3, state);
    }

    @Test
    void instanceRecordRoundTripsThroughNbtAndJsonForEveryPhaseAndOrigin() {
        for (EventPhase phase : EventPhase.values()) {
            for (EventOrigin origin : EventOrigin.values()) {
                for (Optional<BlockPos> anchor : List.of(Optional.<BlockPos>empty(), Optional.of(new BlockPos(1, -2, 3)))) {
                    InstanceRecord original = record(phase, origin, anchor);
                    Tag nbt = InstanceRecord.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
                    assertEquals(original, InstanceRecord.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
                    JsonElement json = InstanceRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
                    InstanceRecord fromJson = InstanceRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                    // JSON numbers carry no NBT tag type, so the payload tag is compared by value.
                    assertEquals(original.id(), fromJson.id());
                    assertEquals(original.phase(), fromJson.phase());
                    assertEquals(original.origin(), fromJson.origin());
                    assertEquals(original.anchor(), fromJson.anchor());
                    assertEquals(original.startsAt(), fromJson.startsAt());
                    assertEquals(original.endsAt(), fromJson.endsAt());
                    assertEquals(7, fromJson.payloadState().getIntOr("ticks", -1));
                }
            }
        }
    }

    @Test
    void engineStateRoundTrips() {
        EventEngineState state = new EventEngineState();
        state.setInstances(List.of(record(EventPhase.ACTIVE, EventOrigin.SCHEDULER, Optional.of(BlockPos.ZERO))));
        state.setCooldownUntil(DEF, 1234L);
        state.setReweight(DEF, 77);
        state.recordResolved(DEF);
        state.recordResolved(DEF);
        state.setLastFiredAt(99L);
        state.setNextRollAt(5000L);
        state.addZone(ExclusionZone.of("base", FakeWorldView.OVERWORLD, 10, 10, -10, -10));
        Tag nbt = EventEngineState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        EventEngineState back = EventEngineState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(state.instances(), back.instances());
        assertEquals(1234L, back.cooldownUntil(DEF));
        assertEquals(Optional.of(77), back.reweight(DEF));
        assertEquals(2L, back.resolvedCount(DEF));
        assertEquals(2L, back.totalResolved());
        assertEquals(99L, back.lastFiredAt());
        assertEquals(5000L, back.nextRollAt());
        assertEquals(state.zones(), back.zones());
        JsonElement json = EventEngineState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state.zones(), EventEngineState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().zones());
    }

    /** Runs an instance into {@code phaseWanted}, "stops" the server, and restores on a new engine. */
    private static TestEngine restart(TestEngine t) {
        t.build();
        t.engine.restore();
        return t;
    }

    @Test
    void announcedKeepsItsTimers() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_announced");
            EventInstance instance = t.start(t.define("announced", type, "{\"announce_ticks\": 1000}"));
            t.tick(5);
            long startsAt = instance.startsAt();
            restart(t);
            EventInstance back = t.engine.live().get(0);
            assertEquals(instance.id(), back.id());
            assertEquals(EventPhase.ANNOUNCED, back.phase());
            assertEquals(startsAt, back.startsAt());
        }
    }

    @Test
    void votingWithTooLittleCountdownIsSkipped() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_vote");
            EventDefinition def = t.define("vote", type, "{\"announce_ticks\": 1000}");
            t.state.setInstances(List.of(new InstanceRecord(UUID.randomUUID(), def.id(), def.type(),
                    FakeWorldView.OVERWORLD, Optional.of(BlockPos.ZERO), EventOrigin.SCHEDULER, 1L, 0L,
                    t.clock.now + 100, t.clock.now + 400, EventPhase.VOTING, 0L, 0, new CompoundTag())));
            t.settings.consentMode = ConsentMode.VOTE;
            t.world.online(FakeWorldView.OVERWORLD, 3);
            restart(t);
            assertTrue(t.engine.live().isEmpty());
            EventInstance skipped = t.engine.recent().get(0);
            assertEquals(EventPhase.SKIPPED, skipped.phase());
            assertEquals("restart", skipped.reason());
        }
    }

    @Test
    void activeCallsLoadStateAndOnResume() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_active");
            EventInstance instance = t.start(t.define("active", type,
                    "{\"announce_ticks\": 0, \"duration_ticks\": 2000}"));
            t.tick(250); // ACTIVE, past the 200-tick save cadence
            assertEquals(EventPhase.ACTIVE, instance.phase());
            long endsAt = instance.endsAt();
            type.calls.clear();
            restart(t);
            assertEquals(List.of("loadState", "onResume"), type.calls);
            EventInstance back = t.engine.live().get(0);
            assertEquals(EventPhase.ACTIVE, back.phase());
            assertEquals(endsAt, back.endsAt());
            assertTrue(((RecordingPayloadType.State) back.payloadState()).ticks > 0, "state came back from the record");
        }
    }

    @Test
    void refusedResumeCancelsAndCleansUp() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_refused");
            t.start(t.define("refused", type, "{\"announce_ticks\": 0}"));
            t.tick(3);
            type.resume = ResumeResult.refuse("resume_failed");
            type.calls.clear();
            restart(t);
            EventInstance back = t.engine.recent().get(0);
            assertEquals(EventPhase.CANCELLED, back.phase());
            assertEquals(1L, type.count("onCleanup"));
        }
    }

    @Test
    void loadStateFailureCancelsWithResumeFailed() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_throws");
            t.start(t.define("throws", type, "{\"announce_ticks\": 0}"));
            t.tick(3);
            type.throwOnLoad = true;
            restart(t);
            EventInstance back = t.engine.recent().get(0);
            assertEquals(EventPhase.CANCELLED, back.phase());
            assertEquals("resume_failed", back.reason());
        }
    }

    @Test
    void resolvingIsCleanedUpAsInterruptedWithoutResolvedOrRewards() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("resume_resolving");
            EventDefinition def = t.define("resolving", type, null);
            t.state.setInstances(List.of(new InstanceRecord(UUID.randomUUID(), def.id(), def.type(),
                    FakeWorldView.OVERWORLD, Optional.of(BlockPos.ZERO), EventOrigin.ADMIN, 1L, 0L, 0L, 400L,
                    EventPhase.RESOLVING, 0L, 0, new CompoundTag())));
            restart(t);
            assertTrue(t.engine.live().isEmpty());
            EventInstance back = t.engine.recent().get(0);
            assertEquals(EventPhase.DONE, back.phase());
            assertEquals(EventOutcome.INTERRUPTED, back.outcome());
            assertEquals(1L, type.count("onCleanup"));
            assertEquals(0L, t.state.totalResolved());
            assertTrue(t.rewarded.isEmpty());
            assertEquals(1, t.history.size());
        }
    }

    @Test
    void missingDefinitionDropsTheRecord() {
        try (TestEngine t = new TestEngine()) {
            t.state.setInstances(List.of(record(EventPhase.ACTIVE, EventOrigin.SCHEDULER, Optional.empty())));
            restart(t);
            assertTrue(t.engine.live().isEmpty());
            assertTrue(t.engine.recent().isEmpty());
            assertTrue(t.state.instances().isEmpty());
        }
    }
}
