package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The population-band table parsed from {@code schedulerBands}: entries
 * {@code name:intervalMultiplier:concurrentCap} separated by {@code ;}, names {@code a-b} or {@code a+}.
 * Bands must start at 0, be contiguous and non-overlapping, end with an open band ({@code a+}), have
 * multipliers in {@code [0.1, 10]} and caps in {@code [0, maxConcurrentEvents]}. Any violation yields the
 * {@link #DEFAULT} table plus one issue naming the entry — a typo never turns the scheduler off.
 */
public final class BandTable {

    public static final String DEFAULT_SPEC = "0-1:2.0:1;2-4:1.0:1;5-9:0.8:2;10+:0.6:3";

    public static final BandTable DEFAULT = parseStrict(DEFAULT_SPEC, 8).orElseThrow();

    private final List<PopulationBand> bands;
    private final List<String> issues;

    private BandTable(List<PopulationBand> bands, List<String> issues) {
        this.bands = List.copyOf(bands);
        this.issues = List.copyOf(issues);
    }

    public List<PopulationBand> bands() {
        return bands;
    }

    /** What was wrong with the configured table (empty when it parsed cleanly). */
    public List<String> issues() {
        return issues;
    }

    /** The band whose range holds {@code online}; the last band for anything beyond the table. */
    public PopulationBand bandFor(int online) {
        int count = Math.max(0, online);
        for (PopulationBand band : bands) {
            if (band.contains(count)) {
                return band;
            }
        }
        return bands.get(bands.size() - 1);
    }

    /** Whether a band of this name exists. */
    public boolean hasBand(String name) {
        for (PopulationBand band : bands) {
            if (band.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Parses with caps up to 8 (the largest {@code maxConcurrentEvents}). */
    public static BandTable parse(String spec) {
        return parse(spec, 8);
    }

    /** Parses; an invalid table yields {@link #DEFAULT} with one issue. */
    public static BandTable parse(String spec, int maxConcurrentEvents) {
        List<String> problems = new ArrayList<>(1);
        Optional<BandTable> parsed = parse(spec, maxConcurrentEvents, problems);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        return new BandTable(DEFAULT.bands, problems.isEmpty() ? List.of("invalid band table") : problems.subList(0, 1));
    }

    private static Optional<BandTable> parseStrict(String spec, int maxConcurrent) {
        return parse(spec, maxConcurrent, new ArrayList<>());
    }

    private static Optional<BandTable> parse(String spec, int maxConcurrent, List<String> problems) {
        if (spec == null || spec.isBlank()) {
            problems.add("schedulerBands is empty");
            return Optional.empty();
        }
        List<PopulationBand> bands = new ArrayList<>();
        int expectedMin = 0;
        boolean open = false;
        for (String raw : spec.split(";")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (open) {
                problems.add("band '" + entry + "' follows the open top band");
                return Optional.empty();
            }
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                problems.add("band '" + entry + "' is not name:multiplier:cap");
                return Optional.empty();
            }
            String name = parts[0].trim();
            int min;
            int max;
            try {
                if (name.endsWith("+")) {
                    min = Integer.parseInt(name.substring(0, name.length() - 1));
                    max = -1;
                    open = true;
                } else {
                    int dash = name.indexOf('-');
                    if (dash <= 0) {
                        problems.add("band '" + entry + "' name must be a-b or a+");
                        return Optional.empty();
                    }
                    min = Integer.parseInt(name.substring(0, dash));
                    max = Integer.parseInt(name.substring(dash + 1));
                }
            } catch (NumberFormatException e) {
                problems.add("band '" + entry + "' has a bad range");
                return Optional.empty();
            }
            if (min != expectedMin) {
                problems.add("band '" + entry + "' leaves a gap or overlaps (expected it to start at "
                        + expectedMin + ")");
                return Optional.empty();
            }
            if (max >= 0 && max < min) {
                problems.add("band '" + entry + "' ends before it starts");
                return Optional.empty();
            }
            double multiplier;
            int cap;
            try {
                multiplier = Double.parseDouble(parts[1].trim());
                cap = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                problems.add("band '" + entry + "' has a bad multiplier or cap");
                return Optional.empty();
            }
            if (!(multiplier >= 0.1D && multiplier <= 10.0D)) {
                problems.add("band '" + entry + "' multiplier must be 0.1 to 10");
                return Optional.empty();
            }
            if (cap < 0 || cap > maxConcurrent) {
                problems.add("band '" + entry + "' cap must be 0 to " + maxConcurrent);
                return Optional.empty();
            }
            bands.add(new PopulationBand(name, min, max, multiplier, cap));
            expectedMin = max + 1;
        }
        if (bands.isEmpty()) {
            problems.add("schedulerBands has no bands");
            return Optional.empty();
        }
        if (!open) {
            problems.add("the last band must be open-ended (a+)");
            return Optional.empty();
        }
        return Optional.of(new BandTable(bands, List.of()));
    }
}
