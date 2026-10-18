package za.co.neroland.neroevents.payload.meteor;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Pure: the block list of one crater, in placement order (clears, crust, slag ring, ore, pocket last).
 *
 * <p>A full crater is a hemisphere of radius {@code r} below the impact block: for each layer
 * {@code dy ∈ [−r, 0]} with {@code ρ = ⌊√(r² − dy²)⌋}, every column offset with {@code dx² + dz² ≤ ρ²} is
 * cleared to air except the lowest cell of each column, which becomes {@code cooled_crust}; the ring
 * {@code ρ² < dx² + dz² ≤ (ρ + 1)²} of the top layer becomes {@code meteor_slag} (where the ground is solid —
 * the builder checks); {@code oreCount} distinct crust cells become ore; the crust cell under the impact column
 * becomes the {@code crater_pocket}. A <b>soft</b> plan is exactly one cell: the pocket on the air block above
 * the impact. Deterministic for a given random sequence.
 */
public record CraterPlan(BlockPos impact, int radius, boolean soft, List<Cell> cells) {

    /** What a planned cell becomes. */
    public enum Kind { AIR, CRUST, SLAG, ORE, POCKET }

    /** One planned cell; {@code ore} indexes the configured ore list for {@link Kind#ORE}, else −1. */
    public record Cell(BlockPos pos, Kind kind, int ore) {
    }

    public CraterPlan {
        cells = List.copyOf(cells);
    }

    public static CraterPlan of(BlockPos impact, int radius, boolean soft, int oreCount, int oreKinds,
            RandomGenerator random) {
        if (soft) {
            return new CraterPlan(impact, radius, true, List.of(new Cell(impact.above(), Kind.POCKET, -1)));
        }
        int r = Math.max(1, radius);
        List<Cell> clears = new ArrayList<>();
        List<BlockPos> crust = new ArrayList<>();
        @Nullable BlockPos pocket = null;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                int lowest = Integer.MIN_VALUE;
                for (int dy = -r; dy <= 0; dy++) {
                    int rho = rho(r, dy);
                    if (d2 <= rho * rho) {
                        if (lowest == Integer.MIN_VALUE) {
                            lowest = dy;
                        } else {
                            clears.add(new Cell(impact.offset(dx, dy, dz), Kind.AIR, -1));
                        }
                    }
                }
                if (lowest != Integer.MIN_VALUE) {
                    BlockPos floor = impact.offset(dx, lowest, dz);
                    if (dx == 0 && dz == 0) {
                        pocket = floor;
                    } else {
                        crust.add(floor);
                    }
                }
            }
        }
        List<Cell> cells = new ArrayList<>(clears);
        List<Integer> oreSlots = pickDistinct(crust.size(), Math.max(0, oreCount), random);
        for (int i = 0; i < crust.size(); i++) {
            int slot = oreSlots.indexOf(i);
            if (slot >= 0 && oreKinds > 0) {
                cells.add(new Cell(crust.get(i), Kind.ORE, random.nextInt(oreKinds)));
            } else {
                cells.add(new Cell(crust.get(i), Kind.CRUST, -1));
            }
        }
        int top = rho(r, 0);
        int outer = (top + 1) * (top + 1);
        for (int dx = -(top + 1); dx <= top + 1; dx++) {
            for (int dz = -(top + 1); dz <= top + 1; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > top * top && d2 <= outer) {
                    cells.add(new Cell(impact.offset(dx, 0, dz), Kind.SLAG, -1));
                }
            }
        }
        if (pocket != null) {
            cells.add(new Cell(pocket, Kind.POCKET, -1));
        }
        return new CraterPlan(impact, r, false, cells);
    }

    private static int rho(int r, int dy) {
        return (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
    }

    private static List<Integer> pickDistinct(int size, int count, RandomGenerator random) {
        List<Integer> pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pool.add(i);
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            out.add(pool.remove(random.nextInt(pool.size())));
        }
        return out;
    }

    /** How many cells of {@code kind} the plan holds. */
    public long count(Kind kind) {
        return cells.stream().filter(c -> c.kind() == kind).count();
    }

    /** The pocket cell (always the last cell), or null when the plan has none. */
    public @Nullable Cell pocket() {
        if (cells.isEmpty()) {
            return null;
        }
        Cell last = cells.get(cells.size() - 1);
        return last.kind() == Kind.POCKET ? last : null;
    }
}
