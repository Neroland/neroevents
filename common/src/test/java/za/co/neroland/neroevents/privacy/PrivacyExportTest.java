package za.co.neroland.neroevents.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardSummary;

/** The export document: its shape, only the subject's data, path safety, the rate limit. */
class PrivacyExportTest {

    private static final Pattern UUID_TEXT =
            Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop");
    private static final RewardSummary SUMMARY = new RewardSummary(69L, 46L, 1, 1, 0, false, 2, 5, 231_000L);

    private final UUID subject = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID instance = UUID.randomUUID();
    private final UUID crateInstance = UUID.randomUUID();

    private JsonObject document() {
        List<RewardLedgerEntry> rows = List.of(
                new RewardLedgerEntry(Optional.of(subject), instance, DEF, SUMMARY, 1_000L),
                new RewardLedgerEntry(Optional.empty(), instance, DEF, SUMMARY, 1_000L),
                new RewardLedgerEntry(Optional.of(other), instance, DEF, SUMMARY, 1_000L));
        List<CrateEntry> crate = List.of(new CrateEntry(crateInstance, DEF,
                List.of(new CrateEntry.Stack(Identifier.fromNamespaceAndPath("nerolandcore", "nero_alloy_ingot"), 3)),
                0L, 2_000L));
        return PrivacyExport.build(subject, rows, crate, new PrivacyExport.Settings(false, true, 7, 14),
                new PrivacyExport.LiveCounts(1, 1, 0), 3_000L);
    }

    @Test
    void theDocumentHasTheDocumentedShape() {
        JsonObject doc = document();
        assertEquals("neroevents", doc.get("mod").getAsString());
        assertEquals(1, doc.get("format").getAsInt());
        assertEquals(subject.toString(), doc.get("subject").getAsString());
        assertEquals(7, doc.getAsJsonObject("privacy").get("ledger_retention_days").getAsInt());
        assertEquals(1, doc.getAsJsonArray("reward_history").size(), "only the subject's own row");
        JsonObject row = doc.getAsJsonArray("reward_history").get(0).getAsJsonObject();
        assertEquals(231_000L, row.get("share_ppm").getAsLong());
        assertEquals(69L, row.get("currency_paid").getAsLong());
        JsonObject item = doc.getAsJsonArray("reward_crate").get(0).getAsJsonObject().getAsJsonArray("items").get(0)
                .getAsJsonObject();
        assertEquals(Set.of("item", "count"), item.keySet());
        assertEquals(1, doc.getAsJsonObject("live").get("contributing_to_events").getAsInt());
        assertTrue(doc.has("milestones_note"));
        assertEquals(Set.of("others", "anonymous_rows", "erasure", "retention"), doc.getAsJsonObject("notes").keySet());
    }

    @Test
    void noOtherPlayerIdAppearsAnywhere() {
        String text = PrivacyExport.toJson(document());
        Matcher m = UUID_TEXT.matcher(text);
        while (m.find()) {
            UUID found = UUID.fromString(m.group());
            assertTrue(found.equals(subject) || found.equals(instance) || found.equals(crateInstance),
                    "only the subject and instance ids may appear");
        }
        assertFalse(text.contains(other.toString()));
    }

    @Test
    void theFilePathCannotEscapeTheExportFolder(@TempDir Path world) {
        Path dir = PrivacyExport.directory(world);
        assertThrows(IllegalStateException.class, () -> PrivacyExport.file(dir, "../escape"));
        assertThrows(IllegalStateException.class, () -> PrivacyExport.file(dir, "sub/../../escape"));
        assertEquals(dir, PrivacyExport.file(dir, subject).getParent());
    }

    @Test
    void writeDeleteAndExists(@TempDir Path world) throws Exception {
        Path dir = PrivacyExport.directory(world);
        String relative = PrivacyExport.write(dir, subject, document());
        assertEquals("neroevents/exports/" + subject + ".json", relative);
        assertTrue(PrivacyExport.exists(dir, subject));
        assertTrue(Files.readString(PrivacyExport.file(dir, subject)).contains("\"reward_history\""));
        assertTrue(PrivacyExport.delete(dir, subject));
        assertFalse(PrivacyExport.exists(dir, subject));
    }

    @Test
    void theCooldownIsEnforcedAndRefundedOnFailure() {
        ExportCooldown cooldown = new ExportCooldown();
        assertEquals(0L, cooldown.tryAcquire(subject, 1_000L, 60_000L));
        assertEquals(59L, cooldown.tryAcquire(subject, 2_000L, 60_000L));
        cooldown.refund(subject);
        assertEquals(0L, cooldown.tryAcquire(subject, 2_500L, 60_000L), "a failed export gives the slot back");
        assertEquals(0L, cooldown.tryAcquire(other, 2_500L, 60_000L));
        assertEquals(0L, cooldown.tryAcquire(subject, 62_500L, 60_000L), "after the cooldown");
        cooldown.forget(subject);
        assertFalse(cooldown.holds(subject));
    }
}
