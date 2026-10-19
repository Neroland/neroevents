package za.co.neroland.neroevents.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * The wave table of a raid or invasion definition: records, codecs and the pure scaling rules.
 *
 * <p>Pure: no world access, so the scaling, the caps and the shuffled spawn order are tested on a plain JVM.
 */
public final class WaveTable {

    /** {@code InvasionBudget.MAX_BUDGET}: NeroCreatures never reserves more than this for one wave. */
    public static final int MAX_BUDGET = 128;

    /** Default {@code timeout_ticks}. */
    public static final int DEFAULT_TIMEOUT_TICKS = 4800;

    private WaveTable() {
    }

    /** {@code {"type": id, "count": 1..32}}. */
    public record MobEntry(Identifier type, int count) {

        public static final Codec<MobEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("type").forGetter(MobEntry::type),
                Codec.intRange(1, 32).fieldOf("count").forGetter(MobEntry::count)
        ).apply(inst, MobEntry::new));
    }

    /** {@code {"mobs": [...], "delay_ticks": 0..6000, "timeout_ticks": 600..12000}}. */
    public record Wave(List<MobEntry> mobs, int delayTicks, int timeoutTicks) {

        public Wave {
            mobs = List.copyOf(mobs);
        }

        public static final Codec<Wave> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                sized(MobEntry.CODEC.listOf(), 1, 16, "mobs").fieldOf("mobs").forGetter(Wave::mobs),
                Codec.intRange(0, 6000).optionalFieldOf("delay_ticks", 0).forGetter(Wave::delayTicks),
                Codec.intRange(600, 12000).optionalFieldOf("timeout_ticks", DEFAULT_TIMEOUT_TICKS)
                        .forGetter(Wave::timeoutTicks)
        ).apply(inst, Wave::new));
    }

    /** One planned (type, count) line after scaling. */
    public record Planned(Identifier type, int count) {
    }

    /** A list codec bounded to {@code [min, max]} entries. */
    static <T> Codec<List<T>> sized(Codec<List<T>> codec, int min, int max, String what) {
        return codec.validate(list -> list.size() < min || list.size() > max
                ? DataResult.error(() -> what + " needs " + min + " to " + max + " entries, got " + list.size())
                : DataResult.success(list));
    }

    /** The per-wave ceiling: {@code max_per_wave}, never above {@link #MAX_BUDGET}. */
    public static int cap(int maxPerWave) {
        return Math.max(1, Math.min(maxPerWave, MAX_BUDGET));
    }

    /**
     * Scales one wave by the online count of the dimension: each entry becomes
     * {@code round(count × (1 + scale × (online − 1)))} (online below 1 counts as 1), entries whose type
     * {@code known} rejects are dropped, and the total is trimmed to {@link #cap(int)} by taking one from the
     * largest line (first in table order on a tie) until it fits. Lines trimmed to zero are removed.
     */
    public static List<Planned> scaled(Wave wave, int onlineInDimension, double scalePerOnline, int maxPerWave,
            Predicate<Identifier> known) {
        return scaled(wave, onlineInDimension, scalePerOnline, 1.0D, maxPerWave, known);
    }

    /**
     * As above, with a separate factor for a lone player: with one player (or none) in the dimension every
     * count is multiplied by {@code soloScale} instead of 1.0.
     */
    public static List<Planned> scaled(Wave wave, int onlineInDimension, double scalePerOnline, double soloScale,
            int maxPerWave, Predicate<Identifier> known) {
        int online = Math.max(1, onlineInDimension);
        double factor = online <= 1 ? soloScale : 1.0D + Math.max(0.0D, scalePerOnline) * (online - 1);
        List<Identifier> types = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (MobEntry entry : wave.mobs()) {
            if (!known.test(entry.type())) {
                continue;
            }
            types.add(entry.type());
            counts.add((int) Math.max(1L, Math.round(entry.count() * factor)));
        }
        int cap = cap(maxPerWave);
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        while (total > cap) {
            int largest = 0;
            for (int i = 1; i < counts.size(); i++) {
                if (counts.get(i) > counts.get(largest)) {
                    largest = i;
                }
            }
            counts.set(largest, counts.get(largest) - 1);
            total--;
        }
        List<Planned> out = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            if (counts.get(i) > 0) {
                out.add(new Planned(types.get(i), counts.get(i)));
            }
        }
        return List.copyOf(out);
    }

    /** The plan's total mob count. */
    public static int total(List<Planned> plan) {
        return plan.stream().mapToInt(Planned::count).sum();
    }

    /** The plan flattened to one type per mob and shuffled with {@code random} (deterministic per seed). */
    public static List<Identifier> flatten(List<Planned> plan, RandomGenerator random) {
        List<Identifier> out = new ArrayList<>(total(plan));
        for (Planned line : plan) {
            for (int i = 0; i < line.count(); i++) {
                out.add(line.type());
            }
        }
        for (int i = out.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Identifier swap = out.get(i);
            out.set(i, out.get(j));
            out.set(j, swap);
        }
        return out;
    }
}
