package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * A definition's {@code conditions}: where and when it may fire. Evaluated by the scheduler against one
 * pass's {@link SchedulerSnapshot}; nothing here touches the world.
 *
 * @param dimensions          candidate dimensions (empty and no tag = every loaded dimension)
 * @param dimensionTag        a dimension-type tag whose dimensions are candidates (exclusive with the list)
 * @param minOnline           server-wide online minimum
 * @param maxOnline           server-wide online maximum, {@code -1} for none
 * @param timeOfDay           a day-time window evaluated per candidate dimension
 * @param bands               population band names the definition may fire in (absent = all)
 * @param requiresGate        a Core gate enough online players must have open
 * @param requiredGateHolders how many online players must have {@code requiresGate} open
 * @param requiresMods        mod ids that must all be loaded
 * @param allowEmptyDimension whether a candidate dimension may have nobody in it
 */
public record EventConditions(List<Identifier> dimensions, Optional<Identifier> dimensionTag, int minOnline,
        int maxOnline, Optional<TimeWindow> timeOfDay, Optional<List<String>> bands, Optional<Identifier> requiresGate,
        int requiredGateHolders, List<String> requiresMods, boolean allowEmptyDimension) {

    /** Every key {@code conditions} understands (anything else is warned about and ignored). */
    public static final Set<String> KEYS = Set.of("dimensions", "dimension_tag", "min_online", "max_online",
            "time_of_day", "bands", "requires_gate", "required_gate_holders", "requires_mods",
            "allow_empty_dimension");

    public static final EventConditions DEFAULT = new EventConditions(List.of(), Optional.empty(), 1, -1,
            Optional.empty(), Optional.empty(), Optional.empty(), 1, List.of(), false);

    public static final Codec<EventConditions> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(EventConditions::dimensions),
            Identifier.CODEC.optionalFieldOf("dimension_tag").forGetter(EventConditions::dimensionTag),
            Codec.INT.optionalFieldOf("min_online", 1).forGetter(EventConditions::minOnline),
            Codec.INT.optionalFieldOf("max_online", -1).forGetter(EventConditions::maxOnline),
            TimeWindow.CODEC.optionalFieldOf("time_of_day").forGetter(EventConditions::timeOfDay),
            Codec.STRING.listOf().optionalFieldOf("bands").forGetter(EventConditions::bands),
            Identifier.CODEC.optionalFieldOf("requires_gate").forGetter(EventConditions::requiresGate),
            Codec.INT.optionalFieldOf("required_gate_holders", 1).forGetter(EventConditions::requiredGateHolders),
            Codec.STRING.listOf().optionalFieldOf("requires_mods", List.of()).forGetter(EventConditions::requiresMods),
            Codec.BOOL.optionalFieldOf("allow_empty_dimension", false).forGetter(EventConditions::allowEmptyDimension)
    ).apply(inst, EventConditions::new));

    public EventConditions {
        dimensions = List.copyOf(dimensions);
        bands = bands.map(List::copyOf);
        requiresMods = List.copyOf(requiresMods);
    }

    /**
     * A day-time window: {@code from} inclusive, {@code to} exclusive, both 0..24000. {@code from > to}
     * wraps midnight ({@code {from: 13000, to: 1000}} = night); {@code from == to} is the whole day.
     */
    public record TimeWindow(int from, int to) {

        public static final Codec<TimeWindow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("from").forGetter(TimeWindow::from),
                Codec.INT.fieldOf("to").forGetter(TimeWindow::to)
        ).apply(inst, TimeWindow::new));

        public boolean valid() {
            return from >= 0 && from <= 24000 && to >= 0 && to <= 24000;
        }

        public boolean matches(long dayTime) {
            long t = Math.floorMod(dayTime, 24000L);
            if (from == to) {
                return true;
            }
            if (from < to) {
                return t >= from && t < to;
            }
            return t >= from || t < to;
        }
    }

    /**
     * The verdict for one pass.
     *
     * @param eligible            whether every condition holds for at least one dimension
     * @param candidateDimensions the dimensions that pass (sorted, deterministic)
     * @param firstFailure        {@code mods}, {@code min_online}, {@code band}, {@code gate_holders},
     *                            {@code dimension} or {@code time_of_day}; {@code null} when eligible
     */
    public record ConditionResult(boolean eligible, List<Identifier> candidateDimensions,
            @Nullable String firstFailure) {

        static ConditionResult fail(String code) {
            return new ConditionResult(false, List.of(), code);
        }
    }

    public ConditionResult evaluate(SchedulerSnapshot snap) {
        for (String mod : requiresMods) {
            if (!snap.loadedMods().contains(mod)) {
                return ConditionResult.fail("mods");
            }
        }
        if (snap.online() < minOnline || (maxOnline >= 0 && snap.online() > maxOnline)) {
            return ConditionResult.fail("min_online");
        }
        if (bands.isPresent() && !bands.get().contains(snap.band().name())) {
            return ConditionResult.fail("band");
        }
        if (requiresGate.isPresent()
                && snap.gateHolders().getOrDefault(requiresGate.get(), 0) < Math.max(1, requiredGateHolders)) {
            return ConditionResult.fail("gate_holders");
        }
        List<Identifier> candidates = new ArrayList<>();
        for (Identifier dimension : snap.loadedDimensions()) {
            if (!dimensions.isEmpty() && !dimensions.contains(dimension)) {
                continue;
            }
            if (dimensionTag.isPresent()
                    && !snap.dimensionTags().getOrDefault(dimensionTag.get(), Set.of()).contains(dimension)) {
                continue;
            }
            if (!allowEmptyDimension && snap.onlinePerDimension().getOrDefault(dimension, 0) < 1) {
                continue;
            }
            candidates.add(dimension);
        }
        if (candidates.isEmpty()) {
            return ConditionResult.fail("dimension");
        }
        if (timeOfDay.isPresent()) {
            TimeWindow window = timeOfDay.get();
            candidates.removeIf(d -> !window.matches(snap.dayTimePerDimension().getOrDefault(d, 0L)));
            if (candidates.isEmpty()) {
                return ConditionResult.fail("time_of_day");
            }
        }
        candidates.sort(null);
        return new ConditionResult(true, List.copyOf(candidates), null);
    }
}
