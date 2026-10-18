package za.co.neroland.neroevents.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardSummary;

/**
 * The data-subject access export (GDPR Art. 15/20, POPIA s23) behind {@code /neroevents privacy export}:
 * everything NeroEvents holds about <b>one player</b>, as one JSON file at
 * {@code <world>/neroevents/exports/<uuid>.json}.
 *
 * <p>Never another player's data: the subject's own ledger rows (anonymous rows cannot be linked to them
 * and are not included), their own crate entries as {@code {item, count}}, and <em>counts</em> of what the
 * running server holds about them in memory — never which events or which vote. The path is built from the
 * subject's own UUID and checked to stay inside the export folder; on a POSIX file system the folder and file
 * are restricted to the server's own user. An export is deleted by an erasure request and after
 * {@link #RETENTION_DAYS} days. Failures are logged by exception class only, because the real message holds
 * the path, and the file name is the UUID.
 */
public final class PrivacyExport {

    public static final int FORMAT_VERSION = 1;
    /** Export files are deleted this many days after they were written. */
    public static final int RETENTION_DAYS = 7;
    /** Relative to the world folder; shown to the player. */
    public static final String RELATIVE_DIR = "neroevents/exports";

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    /** In-memory facts about the subject, as counts only. */
    public record LiveCounts(int contributingToEvents, int openVotesCast, int reminders) {

        public static final LiveCounts NONE = new LiveCounts(0, 0, 0);
    }

    /** The privacy settings in force, for the document's {@code privacy} block. */
    public record Settings(boolean optedOut, boolean ledgerEnabled, int ledgerRetentionDays, int crateRetentionDays) {
    }

    private PrivacyExport() {
    }

    // --- the document (pure) ---------------------------------------------------------------------------

    /** The export document for {@code subject} (plain-JVM testable). Rows not naming the subject are skipped. */
    public static JsonObject build(UUID subject, List<RewardLedgerEntry> rows, List<CrateEntry> crate,
            Settings settings, LiveCounts live, long now) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroEventsCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.ofEpochMilli(now).toString());
        root.addProperty("subject", subject.toString());

        JsonObject privacy = new JsonObject();
        privacy.addProperty("opted_out", settings.optedOut());
        privacy.addProperty("ledger_enabled", settings.ledgerEnabled());
        privacy.addProperty("ledger_retention_days", settings.ledgerRetentionDays());
        privacy.addProperty("crate_retention_days", settings.crateRetentionDays());
        root.add("privacy", privacy);

        JsonArray history = new JsonArray();
        for (RewardLedgerEntry row : rows) {
            if (!row.isFor(subject)) {
                continue; // anonymous rows are nobody's; other players' rows are theirs
            }
            RewardSummary s = row.summary();
            JsonObject out = new JsonObject();
            out.addProperty("instance", row.instanceId().toString());
            out.addProperty("definition", row.definitionId().toString());
            out.addProperty("at", Instant.ofEpochMilli(row.at()).toString());
            out.addProperty("rank", s.rank());
            out.addProperty("participant_count", s.participantCount());
            out.addProperty("share_ppm", s.sharePpm());
            out.addProperty("currency_paid", s.currencyPaid());
            out.addProperty("xp_granted", s.xpGranted());
            out.addProperty("item_stacks", s.itemStacks());
            out.addProperty("item_count", s.itemCount());
            out.addProperty("reputation_lines", s.reputationLines());
            out.addProperty("currency_skipped", s.currencySkipped());
            history.add(out);
        }
        root.add("reward_history", history);

        JsonArray crates = new JsonArray();
        for (CrateEntry entry : crate) {
            JsonObject out = new JsonObject();
            out.addProperty("instance", entry.instanceId().toString());
            out.addProperty("definition", entry.definitionId().toString());
            out.addProperty("at", Instant.ofEpochMilli(entry.at()).toString());
            out.addProperty("xp", entry.xp());
            JsonArray items = new JsonArray();
            for (CrateEntry.Stack stack : entry.items()) {
                JsonObject item = new JsonObject();
                item.addProperty("item", stack.item().toString());
                item.addProperty("count", stack.count());
                items.add(item);
            }
            out.add("items", items);
            crates.add(out);
        }
        root.add("reward_crate", crates);

        JsonObject liveBlock = new JsonObject();
        liveBlock.addProperty("contributing_to_events", live.contributingToEvents());
        liveBlock.addProperty("open_votes_cast", live.openVotesCast());
        liveBlock.addProperty("reminders", live.reminders());
        root.add("live", liveBlock);

        root.addProperty("milestones_note", "Your event milestones (first participation, supply claimed, raid "
                + "repelled, boss contributor) are yes/no flags in Neroland Core's progression store; Core exports "
                + "and erases them.");
        JsonObject notes = new JsonObject();
        notes.addProperty("others", "No other player appears in this file. Event history on this server is "
                + "aggregate (counts only).");
        notes.addProperty("anonymous_rows", "Rows written while you were opted out carry no identity and cannot be "
                + "linked to you, so they are not here.");
        notes.addProperty("erasure", "'/neroland data eraseme' removes everything above, forfeits any unclaimed "
                + "crate items, and deletes this file.");
        notes.addProperty("retention", "This file is deleted after " + RETENTION_DAYS + " days.");
        root.add("notes", notes);
        return root;
    }

    public static String toJson(JsonObject document) {
        return GSON.toJson(document);
    }

    // --- files -------------------------------------------------------------------------------------------

    public static Path directory(MinecraftServer server) {
        return directory(server.getWorldPath(LevelResource.ROOT));
    }

    public static Path directory(Path worldRoot) {
        return worldRoot.resolve("neroevents").resolve("exports").normalize();
    }

    /** One subject's export path, refusing anything that escapes the export folder. */
    public static Path file(Path directory, UUID subject) {
        return file(directory, String.valueOf(subject));
    }

    /** As {@link #file(Path, UUID)} for a raw name (the path-safety check is tested with hostile names). */
    static Path file(Path directory, String name) {
        Path root = directory.normalize();
        Path output = root.resolve(name + ".json").normalize();
        if (!output.startsWith(root) || output.getParent() == null || !output.getParent().equals(root)) {
            throw new IllegalStateException("Unsafe export path");
        }
        return output;
    }

    /** The path the player is told, relative to the world folder. */
    public static String relativePath(UUID subject) {
        return RELATIVE_DIR + "/" + subject + ".json";
    }

    /** Writes the document, replacing an earlier export. Server thread. */
    public static String write(Path directory, UUID subject, JsonObject document) throws IOException {
        Path output = file(directory, subject);
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY);
        } catch (UnsupportedOperationException | IOException bestEffort) {
            // Not POSIX (Windows), or not ours to re-mode: the platform defaults apply.
        }
        try {
            if (Files.exists(output)) {
                Files.setPosixFilePermissions(output, OWNER_FILE);
            } else {
                Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_FILE));
            }
        } catch (UnsupportedOperationException notPosix) {
            // No POSIX permission view on this file system.
        }
        Files.writeString(output, toJson(document), StandardCharsets.UTF_8);
        return relativePath(subject);
    }

    /** Erasure: an export must not outlive the data it was taken from. Never throws. */
    public static boolean delete(@Nullable Path directory, UUID subject) {
        if (directory == null || subject == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(directory, subject));
        } catch (IOException | RuntimeException failure) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] an export file could not be deleted ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    /** Conformance probe: does an export file exist for {@code subject}? */
    public static boolean exists(@Nullable Path directory, UUID subject) {
        try {
            return directory != null && subject != null && Files.exists(file(directory, subject));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Retention: deletes export files older than {@link #RETENTION_DAYS}. Returns how many. Never throws. */
    public static int purgeExpired(@Nullable Path directory, long now) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        long cutoff = now - RETENTION_DAYS * DAY_MS;
        int removed = 0;
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")
                        && Files.getLastModifiedTime(file).toMillis() <= cutoff && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException | RuntimeException failure) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] export retention sweep failed ({}).",
                    failure.getClass().getName());
        }
        return removed;
    }
}
