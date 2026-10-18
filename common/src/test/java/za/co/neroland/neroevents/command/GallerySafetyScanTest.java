package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A textual guard on the gallery: none of its sources reaches a reward, a ledger, the history, a wreck record,
 * a threshold, a gate, a quest hook, a sibling's mutating API, a storm or war channel or the entity index, and
 * none reads a player's name or logs at INFO. The protection check and the null-loot worlds are present.
 */
class GallerySafetyScanTest {

    private static final List<String> FORBIDDEN = List.of("RewardResolver", "BudgetedRewardResolver", "RewardCrates",
            "RewardLedgerState", "EventHistoryState", "WreckState", "ThresholdEvents", "ProgressionGates",
            "QuestHooks", "EventMilestones", "MarketShockRequests", "BossSummons", "InvasionBudget", "reserveWave",
            ".summon(", "ActiveStorms.set", "StormChannel", "WarChannel", "EventEntityIndex.bind", "getName()",
            "getGameProfile", "LOGGER.info", "CreaturesBridge", "FactionsBridge", "ReputationApi", "EconomyBridge",
            "EventEngine.get(", "EventEngine.current(");

    private static Path sources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("common/src/main/java/za/co/neroland/neroevents");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("common/src/main/java not found above " + Path.of("").toAbsolutePath());
    }

    @Test
    void theGalleryTouchesNothingReal() throws IOException {
        Path root = sources();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> commands = Files.list(root.resolve("command"))) {
            commands.filter(p -> p.getFileName().toString().startsWith("Gallery")
                    && p.getFileName().toString().endsWith(".java")).forEach(files::add);
        }
        files.add(root.resolve("client").resolve("GalleryCaptureHarness.java"));
        files.add(root.resolve("client").resolve("GalleryShots.java"));
        assertTrue(files.size() >= 9, "the scan found the gallery sources: " + files);
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String word : FORBIDDEN) {
                if (text.contains(word)) {
                    offenders.add(file.getFileName() + ": " + word);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "the gallery reaches real state: " + offenders);
    }

    @Test
    void theBuildChecksProtectionAndPlacesNoLoot() throws IOException {
        Path command = sources().resolve("command");
        String commands = Files.readString(command.resolve("GalleryCommands.java"), StandardCharsets.UTF_8);
        assertTrue(commands.contains("ProtectionQuery"));
        assertTrue(commands.contains("mayAffectAll"));
        String showcase = Files.readString(command.resolve("GalleryShowcase.java"), StandardCharsets.UTF_8);
        assertTrue(showcase.contains("configure(null, null"), "the crater pocket holds no loot");
        assertTrue(showcase.contains("ServerWreckWorld(level, null, null"), "the wreck crates hold no loot");
        assertFalse(showcase.contains("EventEngine.get("), "the showcase never reaches the engine");
        // A creative player can kill an invulnerable mob, and a killed NeroCreatures mob drops loot (a boss even
        // publishes a defeat), so the creature and boss bays are labels only.
        String stations = Files.readString(command.resolve("GalleryStations.java"), StandardCharsets.UTF_8);
        assertFalse(showcase.contains("\"nerocreatures:") || stations.contains("\"nerocreatures:"),
                "the gallery spawns no NeroCreatures mob");
    }
}
