package za.co.neroland.neroevents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import org.junit.jupiter.api.Test;

/**
 * Pins {@code tools/merge_lang.py}: the committed {@code en_us.json} must be exactly the merge of the
 * {@code tools/lang/*.en_us.json} fragments (sorted keys, two-space indent, LF, trailing newline; a key
 * repeated with the same text is fine, with different text the merge fails), and every lang key a shipped
 * event definition names must exist. Fix a failure by editing the fragment, then running
 * {@code python tools/merge_lang.py}; never edit {@code en_us.json} by hand.
 */
class LangMergeTest {

    private static final String SUFFIX = ".en_us.json";

    @Test
    void committedLangFileIsTheMergeOfTheFragments() throws IOException {
        Path repo = repoRoot();
        Map<String, String> merged = merge(repo.resolve("tools").resolve("lang"));
        assertFalse(merged.isEmpty(), "no lang fragments found");
        String committed = Files.readString(langFile(repo), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(render(merged), committed,
                "en_us.json is not the merge of tools/lang/*.en_us.json; run python tools/merge_lang.py");
    }

    @Test
    void everyShippedDefinitionDisplayKeyExists() throws IOException {
        Path repo = repoRoot();
        Map<String, String> merged = merge(repo.resolve("tools").resolve("lang"));
        Path data = repo.resolve("common").resolve("src").resolve("main").resolve("resources").resolve("data");
        List<String> missing = new ArrayList<>();
        int definitions = 0;
        try (Stream<Path> files = Files.walk(data)) {
            for (Path file : files.filter(LangMergeTest::isDefinitionFile).sorted().toList()) {
                definitions++;
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                JsonObject display = json.getAsJsonObject().getAsJsonObject("display");
                assertNotNull(display, data.relativize(file) + " has no display block");
                for (String field : List.of("title", "what_to_do", "announce")) {
                    JsonElement key = display.get(field);
                    if (key == null || !merged.containsKey(key.getAsString())) {
                        missing.add(data.relativize(file) + " display." + field + " = " + key);
                    }
                }
            }
        }
        assertTrue(definitions > 0, "the scan found the shipped definitions");
        assertTrue(missing.isEmpty(), "lang keys missing from the fragments: " + missing);
    }

    @Test
    void sameTextRepeatedMergesAndDifferentTextFails() throws IOException {
        assertEquals(Map.of("a", "x", "b", "y"),
                merge(List.of(fragment("{\"b\": \"y\", \"a\": \"x\"}"), fragment("{\"a\": \"x\"}"))));
        assertThrows(IllegalStateException.class,
                () -> merge(List.of(fragment("{\"a\": \"x\"}"), fragment("{\"a\": \"z\"}"))));
        assertThrows(IllegalStateException.class, () -> merge(List.of(fragment("{\"a\": \"x\", \"a\": \"z\"}"))));
        assertThrows(IllegalStateException.class, () -> merge(List.of(fragment("{\"a\": 1}"))));
    }

    @Test
    void renderMatchesPythonJsonDumps() {
        Map<String, String> sample = new TreeMap<>();
        sample.put("k", "quote \" slash \\ tab \t line \n ✔ — µ");
        assertEquals("{\n  \"k\": \"quote \\\" slash \\\\ tab \\t line \\n ✔ — µ\"\n}\n", render(sample));
        assertEquals("{}\n", render(new TreeMap<>()));
    }

    // --- The merge, mirroring tools/merge_lang.py ------------------------------------------------------

    private static Map<String, String> merge(Path dir) throws IOException {
        List<Reader> readers = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).sorted().toList()) {
                readers.add(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return merge(readers);
    }

    private static Map<String, String> merge(List<Reader> fragments) throws IOException {
        Map<String, String> merged = new TreeMap<>();
        for (Reader fragment : fragments) {
            try (JsonReader reader = new JsonReader(fragment)) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (reader.peek() != JsonToken.STRING) {
                        throw new IllegalStateException(key + " must be a string");
                    }
                    String value = reader.nextString();
                    String previous = merged.putIfAbsent(key, value);
                    if (previous != null && !previous.equals(value)) {
                        throw new IllegalStateException("duplicate key " + key + " with different text");
                    }
                }
                reader.endObject();
            }
        }
        return merged;
    }

    /** {@code json.dumps(merged, indent=2, ensure_ascii=False) + "\n"} for a flat string map. */
    private static String render(Map<String, String> merged) {
        if (merged.isEmpty()) {
            return "{}\n";
        }
        StringBuilder out = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            out.append("  ");
            quote(out, entry.getKey());
            out.append(": ");
            quote(out, entry.getValue());
            out.append(++i < merged.size() ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static Reader fragment(String json) {
        return new StringReader(json);
    }

    private static boolean isDefinitionFile(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.endsWith(".json") && path.contains("/neroevents/events/");
    }

    private static Path langFile(Path repo) {
        return repo.resolve("common").resolve("src").resolve("main").resolve("resources").resolve("assets")
                .resolve("neroevents").resolve("lang").resolve("en_us.json");
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("tools").resolve("lang"))
                    && Files.isDirectory(dir.resolve("common").resolve("src").resolve("main"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root (tools/lang) not found above " + Path.of("").toAbsolutePath());
    }
}
