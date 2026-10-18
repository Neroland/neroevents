package za.co.neroland.neroevents.client;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;

/**
 * Plain sentences for the console's Past and Mine tabs, built from the aggregate history record and the
 * viewer's own ledger rows: "A pirate raid on Cindara was repelled 2 hours ago — 6 players took part." One
 * template per event type and result ({@code history.neroevents.<type>.<result>}), a generic template for any
 * other type, and never a bare id. Pure (no Minecraft client classes): it returns {@link Sentence} trees that the
 * screen turns into translatable components; unit-tested.
 */
public final class HistorySentences {

    /** A translation key with its arguments: {@link String}, {@link Number}, {@link Place} or another sentence. */
    public record Sentence(String key, List<Object> args) {

        public Sentence {
            args = List.copyOf(args);
        }

        public static Sentence of(String key, Object... args) {
            return new Sentence(key, List.of(args));
        }
    }

    /** A dimension the screen names ("Cindara", "the Overworld"). */
    public record Place(Identifier dimension) {
    }

    /** A definition title the screen translates. */
    public record Title(String key) {
    }

    /** The five ways a past event is told. */
    public enum Result { SUCCESS, FAILURE, OVER, SKIPPED, CANCELLED }

    /** Event types (payload type paths) that ship their own templates. */
    public static final Set<String> TYPES = Set.of("supply_drop", "meteor_shower", "debris_crash", "pirate_raid",
            "alien_invasion", "boss", "faction_war", "market_shock", "solar_storm", "planetary_storm");

    private HistorySentences() {
    }

    /** How a history row ended, in the sentence's terms. */
    public static Result result(int terminalOrdinal, int outcomeOrdinal) {
        EventPhase[] phases = EventPhase.values();
        EventPhase terminal = terminalOrdinal >= 0 && terminalOrdinal < phases.length ? phases[terminalOrdinal] : null;
        if (terminal == EventPhase.SKIPPED) {
            return Result.SKIPPED;
        }
        if (terminal == EventPhase.CANCELLED) {
            return Result.CANCELLED;
        }
        EventOutcome[] outcomes = EventOutcome.values();
        EventOutcome outcome = outcomeOrdinal >= 0 && outcomeOrdinal < outcomes.length ? outcomes[outcomeOrdinal] : null;
        if (outcome == EventOutcome.SUCCESS) {
            return Result.SUCCESS;
        }
        if (outcome == EventOutcome.FAILURE || outcome == EventOutcome.ESCAPED) {
            return Result.FAILURE;
        }
        return Result.OVER;
    }

    /** The template key for a type and result. */
    public static String templateKey(String typePath, Result result) {
        String type = typePath != null && TYPES.contains(typePath) ? typePath : "generic";
        return "history.neroevents." + type + "." + result.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** "2 hours ago", "yesterday", "just now". */
    public static Sentence ago(long seconds) {
        long s = Math.max(0L, seconds);
        if (s < 60L) {
            return Sentence.of("time.neroevents.ago.now");
        }
        if (s < 3600L) {
            long m = s / 60L;
            return m == 1L ? Sentence.of("time.neroevents.ago.minute") : Sentence.of("time.neroevents.ago.minutes", m);
        }
        if (s < 86400L) {
            long h = s / 3600L;
            return h == 1L ? Sentence.of("time.neroevents.ago.hour") : Sentence.of("time.neroevents.ago.hours", h);
        }
        if (s < 172800L) {
            return Sentence.of("time.neroevents.ago.yesterday");
        }
        return Sentence.of("time.neroevents.ago.days", s / 86400L);
    }

    /** "nobody took part" / "1 player took part" / "6 players took part" (a count, never names). */
    public static Sentence tookPart(int participants) {
        if (participants <= 0) {
            return Sentence.of("history.neroevents.took_part.none");
        }
        if (participants == 1) {
            return Sentence.of("history.neroevents.took_part.one");
        }
        return Sentence.of("history.neroevents.took_part.many", participants);
    }

    /** The whole sentence for one Past row. Every template takes (place, time ago, title). */
    public static Sentence sentence(HistoryRow row) {
        Result result = result(row.terminal(), row.outcome());
        Sentence core = new Sentence(templateKey(row.typePath(), result),
                List.of(new Place(row.dimension()), ago(row.ageSeconds()), new Title(row.titleKey())));
        boolean ran = result == Result.SUCCESS || result == Result.FAILURE || result == Result.OVER;
        if (ran || row.participants() > 0) {
            return Sentence.of("history.neroevents.with_count", core, tookPart(row.participants()));
        }
        return Sentence.of("history.neroevents.plain", core);
    }

    /** "Meteor Shower — 2 items, 40 credits, 15 XP · yesterday" for one of the viewer's ledger rows. */
    public static Sentence ledger(LedgerRow row) {
        List<Object> parts = new java.util.ArrayList<>();
        if (row.items() > 0) {
            parts.add(row.items() == 1 ? Sentence.of("ledger.neroevents.item")
                    : Sentence.of("ledger.neroevents.items", row.items()));
        }
        if (row.credits() > 0L) {
            parts.add(Sentence.of("ledger.neroevents.credits", row.credits()));
        }
        if (row.xp() > 0L) {
            parts.add(Sentence.of("ledger.neroevents.xp", row.xp()));
        }
        Sentence what = parts.isEmpty() ? Sentence.of("ledger.neroevents.nothing")
                : new Sentence("ledger.neroevents.list" + parts.size(), parts);
        return Sentence.of("ledger.neroevents.row", new Title(row.titleKey()), what, ago(row.ageSeconds()));
    }
}
