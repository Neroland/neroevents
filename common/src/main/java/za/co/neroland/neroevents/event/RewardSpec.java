package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A definition's {@code rewards}: budget, split rule, reward lines and the pay rules. Validated at load;
 * paid by the budgeted reward resolver when the instance resolves.
 *
 * @param budget           item-value / currency units, at least 0
 * @param split            {@code contribution} or {@code equal}
 * @param minShareFraction 0..0.5, the smallest share a credited participant receives
 * @param lines            the reward lines
 * @param topBonus         extra lines for the top contributors by rank
 * @param payOnExpiry      also pay when the outcome is {@code EXPIRED} (events whose natural end is the timer)
 * @param decay            whether contributions of players who leave the bounds decay
 */
public record RewardSpec(long budget, Split split, double minShareFraction, List<RewardLine> lines,
        List<RewardLine> topBonus, boolean payOnExpiry, boolean decay) {

    public static final Set<String> KEYS = Set.of("budget", "split", "min_share_fraction", "lines", "top_bonus",
            "pay_on_expiry", "decay");

    public static final RewardSpec NONE = new RewardSpec(0L, Split.CONTRIBUTION, 0.05D, List.of(), List.of(), false,
            true);

    public enum Split {
        CONTRIBUTION, EQUAL;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Split> byName(String name) {
            for (Split split : values()) {
                if (split.serializedName().equals(name)) {
                    return Optional.of(split);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One reward line: currency / xp {@code amount}, an {@code item} × {@code count} (optional {@code value}
     * per item in value units), or reputation.
     */
    public record RewardLine(Kind kind, int rank, long amount, Optional<Identifier> item, int count,
            Optional<Identifier> faction, int delta, OptionalLong value) {

        public enum Kind { CURRENCY, XP, ITEM, REPUTATION }

        /**
         * Reads one line leniently; {@code problem} receives a short reason when the line is unusable
         * (the line is then dropped, the definition kept).
         */
        static Optional<RewardLine> parse(JsonElement json, boolean bonus, Consumer<String> problem) {
            if (json == null || !json.isJsonObject()) {
                problem.accept("a reward line is not an object");
                return Optional.empty();
            }
            JsonObject obj = json.getAsJsonObject();
            try {
                String kindName = obj.has("kind") ? obj.get("kind").getAsString() : "";
                int rank = 0;
                if (bonus) {
                    rank = obj.has("rank") ? obj.get("rank").getAsInt() : 0;
                    if (rank < 1) {
                        problem.accept("a top_bonus line needs rank >= 1");
                        return Optional.empty();
                    }
                }
                switch (kindName) {
                    case "currency", "xp" -> {
                        long amount = obj.has("amount") ? obj.get("amount").getAsLong() : -1L;
                        if (amount < 0) {
                            problem.accept("a " + kindName + " line needs amount >= 0");
                            return Optional.empty();
                        }
                        return Optional.of(new RewardLine("xp".equals(kindName) ? Kind.XP : Kind.CURRENCY, rank,
                                amount, Optional.empty(), 0, Optional.empty(), 0, OptionalLong.empty()));
                    }
                    case "item" -> {
                        Identifier item = obj.has("item") ? Identifier.tryParse(obj.get("item").getAsString()) : null;
                        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                        if (item == null || count < 1 || count > 64) {
                            problem.accept("an item line needs a valid item and count 1 to 64");
                            return Optional.empty();
                        }
                        OptionalLong value = OptionalLong.empty();
                        if (obj.has("value")) {
                            long v = obj.get("value").getAsLong();
                            if (v < 1L) {
                                problem.accept("an item line's value must be >= 1; using the default value");
                            } else {
                                value = OptionalLong.of(v);
                            }
                        }
                        return Optional.of(new RewardLine(Kind.ITEM, rank, 0L, Optional.of(item), count,
                                Optional.empty(), 0, value));
                    }
                    case "reputation" -> {
                        Identifier faction = obj.has("faction")
                                ? Identifier.tryParse(obj.get("faction").getAsString()) : null;
                        if (faction == null || !obj.has("delta")) {
                            problem.accept("a reputation line needs a faction and a delta");
                            return Optional.empty();
                        }
                        return Optional.of(new RewardLine(Kind.REPUTATION, rank, 0L, Optional.empty(), 0,
                                Optional.of(faction), obj.get("delta").getAsInt(), OptionalLong.empty()));
                    }
                    default -> {
                        problem.accept("unknown reward line kind '" + kindName + "'");
                        return Optional.empty();
                    }
                }
            } catch (RuntimeException e) {
                problem.accept("a reward line is malformed (" + e.getClass().getSimpleName() + ")");
                return Optional.empty();
            }
        }
    }

    /** The JSON shape as written; normalised by {@link #fromRaw}. */
    public record Raw(long budget, String split, double minShareFraction, List<JsonElement> lines,
            List<JsonElement> topBonus, boolean payOnExpiry, boolean decay) {
    }

    private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    public static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("budget", 0L).forGetter(Raw::budget),
            Codec.STRING.optionalFieldOf("split", "contribution").forGetter(Raw::split),
            Codec.DOUBLE.optionalFieldOf("min_share_fraction", 0.05D).forGetter(Raw::minShareFraction),
            JSON.listOf().optionalFieldOf("lines", List.of()).forGetter(Raw::lines),
            JSON.listOf().optionalFieldOf("top_bonus", List.of()).forGetter(Raw::topBonus),
            Codec.BOOL.optionalFieldOf("pay_on_expiry", false).forGetter(Raw::payOnExpiry),
            Codec.BOOL.optionalFieldOf("decay", true).forGetter(Raw::decay)
    ).apply(inst, Raw::new));

    /** Normalises the raw shape; every reset or dropped line is reported to {@code issue}. */
    public static RewardSpec fromRaw(Raw raw, Consumer<String> issue) {
        long budget = raw.budget();
        if (budget < 0) {
            issue.accept("budget must be >= 0; using 0");
            budget = 0L;
        }
        Split split = Split.byName(raw.split()).orElse(null);
        if (split == null) {
            issue.accept("split must be contribution or equal; using contribution");
            split = Split.CONTRIBUTION;
        }
        double minShare = raw.minShareFraction();
        if (!(minShare >= 0.0D && minShare <= 0.5D)) {
            issue.accept("min_share_fraction must be 0 to 0.5; using 0.05");
            minShare = 0.05D;
        }
        List<RewardLine> lines = new ArrayList<>();
        for (JsonElement json : raw.lines()) {
            RewardLine.parse(json, false, issue).ifPresent(lines::add);
        }
        List<RewardLine> bonus = new ArrayList<>();
        for (JsonElement json : raw.topBonus()) {
            RewardLine.parse(json, true, issue).ifPresent(bonus::add);
        }
        return new RewardSpec(budget, split, minShare, List.copyOf(lines), List.copyOf(bonus), raw.payOnExpiry(),
                raw.decay());
    }

    public RewardSpec withLines(List<RewardLine> newLines, List<RewardLine> newBonus) {
        return new RewardSpec(budget, split, minShareFraction, List.copyOf(newLines), List.copyOf(newBonus),
                payOnExpiry, decay);
    }

    /**
     * Load-time checks against the value table (each becomes an IGNORED validation row; the definition is
     * kept): lines worth more than the budget (clamped at pay time), and a minimum share with only item
     * lines to apply it to.
     */
    public List<String> valueWarnings(za.co.neroland.neroevents.reward.ValueTable values) {
        List<String> out = new ArrayList<>();
        List<za.co.neroland.neroevents.reward.RewardLine> all = new ArrayList<>();
        for (RewardLine line : lines) {
            all.add(za.co.neroland.neroevents.reward.RewardLine.of(line));
        }
        for (RewardLine line : topBonus) {
            all.add(za.co.neroland.neroevents.reward.RewardLine.of(line));
        }
        long total = values.total(all);
        if (total > budget) {
            out.add("lines total " + total + " VU exceeds budget " + budget + "; clamped at pay time");
        }
        boolean divisible = false;
        boolean anyItem = false;
        for (RewardLine line : lines) {
            if (line.kind() == RewardLine.Kind.CURRENCY || line.kind() == RewardLine.Kind.XP) {
                divisible = true;
            } else if (line.kind() == RewardLine.Kind.ITEM) {
                anyItem = true;
            }
        }
        if (minShareFraction > 0.0D && anyItem && !divisible) {
            out.add("floor has no divisible line");
        }
        return out;
    }

    /** A copy without item lines (a boss's items are paid by the boss mod, never by NeroEvents). */
    public RewardSpec withoutItems() {
        return withLines(lines.stream().filter(l -> l.kind() != RewardLine.Kind.ITEM).toList(),
                topBonus.stream().filter(l -> l.kind() != RewardLine.Kind.ITEM).toList());
    }
}
