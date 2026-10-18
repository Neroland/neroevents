package za.co.neroland.neroevents.telemetry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A cheap textual guard: no INFO or WARN log statement in the main sources passes an argument that looks
 * like a player id, a name, a share or an amount. The real review is done by reading the code; this
 * catches the obvious regression.
 */
class NoPersonalDataInLogsTest {

    private static final Pattern CALL = Pattern.compile("LOGGER\\.(info|warn)\\(");
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(UUID|uuid|getUUID|getName|share|amount)\\b");

    @Test
    void infoAndWarnLinesCarryNoPersonalData() throws IOException {
        Path root = findSources();
        assertNotNull(root, "common/src/main/java not found above " + Path.of("").toAbsolutePath());
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = CALL.matcher(text);
                while (m.find()) {
                    int end = text.indexOf(';', m.end());
                    String statement = text.substring(m.end(), end < 0 ? text.length() : end);
                    String arguments = withoutStringLiterals(statement);
                    checked++;
                    if (FORBIDDEN.matcher(arguments).find()) {
                        offenders.add(root.relativize(file) + ": " + statement.strip());
                    }
                }
            }
        }
        assertTrue(checked > 10, "the scan found the log statements");
        assertTrue(offenders.isEmpty(), "log lines with personal-data-looking arguments: " + offenders);
    }

    private static String withoutStringLiterals(String statement) {
        // An exception's class name is fine (it is how failures are logged); a player's name is not.
        return statement.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"")
                .replaceAll("getClass\\(\\)\\.get(Simple)?Name\\(\\)", "");
    }

    private static Path findSources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("common").resolve("src").resolve("main").resolve("java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
