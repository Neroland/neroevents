package za.co.neroland.neroevents.client;

import java.util.Locale;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.client.HistorySentences.Place;
import za.co.neroland.neroevents.client.HistorySentences.Sentence;
import za.co.neroland.neroevents.client.HistorySentences.Title;

/**
 * The console's and the HUD's copy rules in code: the title line ({@code <Event noun> on <Dimension>}, at most
 * 32 characters, the noun cut before the place), the what-to-do line (the definition's own {@code .todo} key,
 * else the type's, with the time left), {@code mm:ss} countdowns and dimension names (a Nerospace lang key when
 * one exists, else the vanilla-style name). Turns {@link HistorySentences} trees into components. CLIENT-ONLY (reads the client language).
 */
public final class ConsoleText {

    /** The title line's character budget. */
    public static final int TITLE_MAX = 32;

    private ConsoleText() {
    }

    private static boolean has(String key) {
        return key != null && !key.isEmpty() && Language.getInstance().has(key);
    }

    /** "Cindara", "the Overworld", "the Nether". */
    public static String dimensionName(Identifier dimension) {
        if (dimension == null) {
            return "";
        }
        String path = dimension.getPath().replace('/', '.');
        String own = "dimension." + dimension.getNamespace() + "." + path;
        if (has(own)) {
            return Language.getInstance().getOrDefault(own);
        }
        String ours = "dimension.neroevents." + dimension.getNamespace() + "." + path;
        if (has(ours)) {
            return Language.getInstance().getOrDefault(ours);
        }
        String last = dimension.getPath().substring(dimension.getPath().lastIndexOf('/') + 1).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : last.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    /** {@code mm:ss} of a tick count. */
    public static String clock(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** "8 min left" (minutes rounded up) or "40 s left" in the last minute. */
    public static Component timeLeft(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds < 60L) {
            return Component.translatable("console.neroevents.time.seconds_left", seconds);
        }
        return Component.translatable("console.neroevents.time.minutes_left", (seconds + 59L) / 60L);
    }

    /** "starts in 4 min" / "starts in 40 s". */
    public static Component startsIn(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds < 60L) {
            return Component.translatable("console.neroevents.time.starts_seconds", seconds);
        }
        return Component.translatable("console.neroevents.time.starts_minutes", (seconds + 59L) / 60L);
    }

    /** The event noun ("Meteor Shower"); "An event" when the key is unknown. */
    public static String noun(String titleKey) {
        return has(titleKey) ? Language.getInstance().getOrDefault(titleKey)
                : Language.getInstance().getOrDefault("console.neroevents.an_event");
    }

    /** {@code <Event noun> on <Dimension>}, at most {@link #TITLE_MAX} characters; the noun is cut, never the place. */
    public static Component titleLine(String titleKey, Identifier dimension) {
        String place = dimensionName(dimension);
        String noun = noun(titleKey);
        String joiner = Language.getInstance().getOrDefault("console.neroevents.title_on");
        String full = String.format(Locale.ROOT, joiner, noun, place);
        if (full.length() > TITLE_MAX) {
            int room = Math.max(1, noun.length() - (full.length() - TITLE_MAX) - 1);
            noun = noun.substring(0, Math.min(noun.length(), room)).stripTrailing() + "…";
            full = String.format(Locale.ROOT, joiner, noun, place);
        }
        return Component.literal(full);
    }

    /**
     * The what-to-do line with the time ({@code timePhrase}): the definition's own {@code .todo} sentence when a
     * language file has one, else the type's, else the definition's what-to-do sentence, else the generic line.
     */
    public static Component whatToDo(String definitionTodoKey, String todoKey, String whatToDoKey,
            Component timePhrase) {
        if (has(definitionTodoKey)) {
            return Component.translatable(definitionTodoKey, timePhrase);
        }
        if (has(todoKey)) {
            return Component.translatable(todoKey, timePhrase);
        }
        if (has(whatToDoKey)) {
            return Component.translatable(whatToDoKey);
        }
        return Component.translatable("console.neroevents.todo.generic", timePhrase);
    }

    /** "· 320 m north-east" from the viewer to a public location; empty when not in the same dimension. */
    public static Component bearing(int dx, int dz) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0D), 8L);
        return Component.translatable("console.neroevents.where", Math.round(distance),
                Component.translatable("bearing.neroevents." + points[sector]));
    }

    /** A {@link Sentence} tree as a component. */
    public static Component of(Sentence sentence) {
        Object[] args = new Object[sentence.args().size()];
        for (int i = 0; i < args.length; i++) {
            Object arg = sentence.args().get(i);
            if (arg instanceof Sentence nested) {
                args[i] = of(nested);
            } else if (arg instanceof Place place) {
                args[i] = dimensionName(place.dimension());
            } else if (arg instanceof Title title) {
                args[i] = noun(title.key());
            } else if (arg instanceof Number || arg instanceof String) {
                args[i] = String.valueOf(arg);
            } else {
                args[i] = "";
            }
        }
        return Component.translatable(sentence.key(), args);
    }
}
