package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.event.reward.RewardResolver;
import za.co.neroland.neroevents.privacy.BackupHook;
import za.co.neroland.neroevents.privacy.EventsPrivacy;
import za.co.neroland.neroevents.privacy.EventsPrivacyActions;
import za.co.neroland.neroevents.privacy.PrivacyExport;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardCrates;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.reward.RewardSummary;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/**
 * POPIA/GDPR erasure conformance through Core's {@link ErasureConformance} harness: every NeroEvents store
 * that names a player — the reward ledger, the reward crates, the opt-out set, export files and the
 * engine's in-memory state (contributions, votes, session crate, session paid markers) — loses the
 * subject through the composite eraser while a bystander keeps theirs; and every persisted store refreshes
 * its backup exactly once per erasure.
 */
class ErasureConformanceTest {

    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop");
    private static final RewardSummary SUMMARY = new RewardSummary(1L, 0L, 0, 0, 0, false, 1, 2, 500_000L);

    private final List<NeroEventsData.StoreEraser> added = new ArrayList<>();
    private boolean registered;

    @AfterEach
    void tearDown() {
        added.forEach(NeroEventsData::removeStore);
        added.clear();
        if (registered) {
            PlayerDataErasure.unregister(NeroEventsData.eraser());
            registered = false;
        }
        BackupHook.set(null);
    }

    private void add(NeroEventsData.StoreEraser store) {
        NeroEventsData.addStore(store);
        added.add(store);
    }

    @Test
    void everyStoreForgetsTheSubjectAndKeepsTheBystander(@TempDir Path world) throws Exception {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        RewardLedgerState ledger = new RewardLedgerState();
        RewardCrateState crateState = new RewardCrateState();
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        Path exports = PrivacyExport.directory(world);
        for (UUID who : List.of(subject, bystander)) {
            ledger.record(new RewardLedgerEntry(Optional.of(who), instanceId, DEF, SUMMARY, 1L), 100);
            crateState.add(who, new CrateEntry(instanceId, DEF,
                    List.of(new CrateEntry.Stack(Identifier.fromNamespaceAndPath("minecraft", "diamond"), 1)), 0L, 1L));
            optOuts.set(who, true);
            PrivacyExport.write(exports, who, PrivacyExport.build(who, List.of(), List.of(),
                    new PrivacyExport.Settings(true, true, 7, 14), PrivacyExport.LiveCounts.NONE, 1L));
        }

        try (TestEngine t = new TestEngine()) {
            RewardCrates crates = new RewardCrates(() -> crateState);
            t.engine.rewards(RewardResolver.NONE, crates);
            t.settings.consentMode = ConsentMode.VOTE;
            EventDefinition def = t.define("erasure", new RecordingPayloadType("erasure"), null);
            EventInstance instance = t.start(def);
            instance.votes().openVoting();
            for (UUID who : List.of(subject, bystander)) {
                instance.contributions().credit(who, ContributionTable.Kind.DAMAGE, 5.0D);
                instance.votes().cast(who, true);
                crates.add(who, instanceId, DEF, List.of(new CrateEntry.Stack(DEF, 1)), 0L, 1L, true, 27);
                t.engine.participation().markPaid(instanceId, who);
                assertEquals(0L, EventsPrivacyActions.cooldown().tryAcquire(who, System.currentTimeMillis(),
                        3_600_000L));
            }

            add(RewardLedgerState.eraser(server -> ledger));
            add(RewardCrateState.eraser(server -> crateState));
            add(PrivacyOptOuts.eraser(server -> optOuts));
            add(EventsPrivacy.exportEraser(server -> exports));
            add(EventEngine.liveEraser(() -> t.engine));
            PlayerDataErasure.register(NeroEventsData.eraser());
            registered = true;

            ErasureConformance.Report report = ErasureConformance.create()
                    .probe("neroevents:reward_ledger", ledger::holds)
                    .probe("neroevents:reward_crates", crateState::holds)
                    .probe("neroevents:privacy_optouts", optOuts::holds)
                    .probe("neroevents:exports", uuid -> PrivacyExport.exists(exports, uuid))
                    .probe("neroevents:live_contributions", t.engine::holdsLiveContributions)
                    .probe("neroevents:live_votes", t.engine::holdsLiveVotes)
                    .probe("neroevents:session_crate", t.engine::holdsSessionCrate)
                    .probe("neroevents:session_paid", t.engine::holdsSessionPaid)
                    .probe("neroevents:export_cooldown", EventsPrivacyActions.cooldown()::holds)
                    .run(null, subject);
            assertTrue(report.passed(), report.describe());
            assertFalse(report.describe().contains(subject.toString()), "the report never carries the UUID");

            assertTrue(ledger.holds(bystander));
            assertTrue(crateState.holds(bystander));
            assertTrue(optOuts.holds(bystander));
            assertTrue(PrivacyExport.exists(exports, bystander));
            assertTrue(t.engine.holdsLiveContributions(bystander));
            assertTrue(t.engine.holdsLiveVotes(bystander));
            assertTrue(t.engine.holdsSessionCrate(bystander));
            assertTrue(t.engine.holdsSessionPaid(bystander));
            assertTrue(EventsPrivacyActions.cooldown().holds(bystander));
        } finally {
            EventsPrivacyActions.cooldown().forget(subject);
            EventsPrivacyActions.cooldown().forget(bystander);
        }
    }

    @Test
    void everyPersistedStoreRefreshesItsBackupOnce() {
        Map<String, Integer> backups = new LinkedHashMap<>();
        BackupHook.set((server, store, backup) -> backups.merge(store, 1, Integer::sum));
        List<NeroEventsData.StoreEraser> stores = List.of(
                RewardLedgerState.eraser(server -> new RewardLedgerState()),
                RewardCrateState.eraser(server -> new RewardCrateState()),
                PrivacyOptOuts.eraser(server -> new PrivacyOptOuts()),
                EventsPrivacy.exportEraser(server -> null),
                EventEngine.liveEraser(() -> null));
        for (NeroEventsData.StoreEraser store : stores) {
            store.erase(null, UUID.randomUUID());
            store.backupNow(null);
        }
        assertEquals(Map.of("reward_ledger", 1, "reward_crates", 1, "privacy_optouts", 1), backups);
    }
}
