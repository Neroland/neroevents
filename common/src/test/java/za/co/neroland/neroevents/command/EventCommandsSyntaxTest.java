package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the command syntax: the tree is built from {@link EventCommands#SYNTAX}, so checking the rows checks
 * the tree, and the wiki's Commands page must list exactly the same rows in the same order.
 */
class EventCommandsSyntaxTest {

    private static final Pattern WIKI_ROW = Pattern.compile("^\\|\\s*`(/neroevents[^`]*)`");

    @Test
    void everyRowUsesKnownLiteralsAndArguments() {
        Set<String> seen = new HashSet<>();
        for (String[] tokens : EventCommands.tokens()) {
            assertFalse(tokens[0].startsWith("<"), "the first token is a literal");
            for (String token : tokens) {
                if (token.startsWith("<")) {
                    assertTrue(token.endsWith(">"), token);
                    assertTrue(EventCommands.ARGUMENTS.contains(token.substring(1, token.length() - 1)), token);
                } else {
                    assertTrue(token.matches("[a-z]+"), token);
                }
            }
        }
        for (String row : EventCommands.SYNTAX) {
            assertTrue(row.startsWith("/neroevents "), row);
            assertTrue(seen.add(row), "duplicate row " + row);
        }
        assertTrue(EventCommands.PLAYER_LITERALS.containsAll(Set.of("claim", "rewards", "privacy")));
        assertFalse(EventCommands.PLAYER_LITERALS.contains("budget"), "the budget is for operators");
        for (String literal : EventCommands.PLAYER_LITERALS) {
            assertTrue(EventCommands.SYNTAX.stream().anyMatch(r -> r.startsWith("/neroevents " + literal)), literal);
        }
    }

    @Test
    void theSpecifiedFormsArePresent() {
        for (String form : List.of("/neroevents list", "/neroevents status", "/neroevents vote yes",
                "/neroevents vote no <instance>", "/neroevents preview <definition>",
                "/neroevents start <definition> <dimension> <anchor>", "/neroevents cancel <instance>",
                "/neroevents skip", "/neroevents reweight <definition> reset", "/neroevents zones exclusions",
                "/neroevents zones add <name> <from> <to> <dimension>", "/neroevents zones remove <name>",
                "/neroevents claim", "/neroevents rewards", "/neroevents privacy optout", "/neroevents privacy optin",
                "/neroevents privacy status", "/neroevents privacy export", "/neroevents budget",
                "/neroevents gallery", "/neroevents gallery exhibits", "/neroevents gallery events",
                "/neroevents gallery stress", "/neroevents gallery storm <storm>", "/neroevents gallery cue <station>",
                "/neroevents gallery clear")) {
            assertTrue(EventCommands.SYNTAX.contains(form), form);
        }
        assertEquals(Set.of("instance", "definition", "dimension", "anchor", "weight", "name", "from", "to", "storm",
                "station"), EventCommands.ARGUMENTS);
        assertFalse(EventCommands.PLAYER_LITERALS.contains("gallery"), "the gallery is for operators");
        List<String> gallery = EventCommands.SYNTAX.stream().filter(r -> r.startsWith("/neroevents gallery")).toList();
        assertEquals(7, gallery.size());
        assertEquals("/neroevents gallery clear", EventCommands.SYNTAX.get(EventCommands.SYNTAX.size() - 1));
    }

    @Test
    void wikiCommandsPageMatchesTheSyntaxTable() throws IOException {
        Path page = findWikiPage();
        assertNotNull(page, "wiki/Commands.md not found above " + Path.of("").toAbsolutePath());
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(page, StandardCharsets.UTF_8)) {
            Matcher m = WIKI_ROW.matcher(line);
            if (m.find()) {
                rows.add(m.group(1).trim());
            }
        }
        assertEquals(EventCommands.SYNTAX, rows);
    }

    private static Path findWikiPage() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("wiki").resolve("Commands.md");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
