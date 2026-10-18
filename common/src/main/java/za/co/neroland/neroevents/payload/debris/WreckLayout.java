package za.co.neroland.neroevents.payload.debris;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * One wreck variant as a layer table (JSON layouts, not NBT — per-block protection and
 * structure-void semantics stay trivial and plain-JVM testable). On disk:
 * {@code {"size": [x, y, z], "loot_blocks": n, "layers": [{"y": int, "rows": ["..."]}]}} — rows run north to
 * south, each row's cells west to east; {@code y = 0} is the first block above the ground plane and
 * {@code y = -1} replaces exactly one ground block. The cell key is fixed:
 * {@code .} structure void, {@code P} hull plating, {@code S} scorched panel, {@code n e s w} bent strut facing
 * north/east/south/west, {@code C} salvage crate facing north, {@code c} salvage crate facing west.
 *
 * <p>{@link #CODEC} rejects a file whose rows are not all {@code size.x} long, whose row count is not
 * {@code size.z}, whose {@code y} is outside {@code -1..size.y-1}, with a character outside the key, or whose
 * {@code loot_blocks} does not equal the number of crate cells. {@link #rotate} turns the whole layout
 * clockwise about the vertical axis, remapping facings (n→e→s→w→n per 90°).
 */
public record WreckLayout(int sizeX, int sizeY, int sizeZ, int lootBlocks, List<Placement> placements) {

    /** What a cell becomes. */
    public enum Kind { HULL_PLATING, SCORCHED_PANEL, BENT_STRUT, SALVAGE_CRATE }

    /** One non-void cell, relative to the layout's north-west corner on the ground plane. */
    public record Placement(int x, int y, int z, Kind kind, Direction facing) {
    }

    /** One layer as written. */
    public record Layer(int y, List<String> rows) {
        static final Codec<Layer> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("y").forGetter(Layer::y),
                Codec.STRING.listOf().fieldOf("rows").forGetter(Layer::rows)
        ).apply(inst, Layer::new));
    }

    /** The file as written. */
    public record Raw(List<Integer> size, int lootBlocks, List<Layer> layers) {
        static final Codec<Raw> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.listOf().fieldOf("size").forGetter(Raw::size),
                Codec.INT.fieldOf("loot_blocks").forGetter(Raw::lootBlocks),
                Layer.CODEC.listOf().fieldOf("layers").forGetter(Raw::layers)
        ).apply(inst, Raw::new));
    }

    public static final Codec<WreckLayout> CODEC = Raw.CODEC.comapFlatMap(WreckLayout::fromRaw, WreckLayout::toRaw);

    public WreckLayout {
        placements = List.copyOf(placements);
    }

    static DataResult<WreckLayout> fromRaw(Raw raw) {
        if (raw.size().size() != 3) {
            return DataResult.error(() -> "size must be [x, y, z]");
        }
        int sx = raw.size().get(0);
        int sy = raw.size().get(1);
        int sz = raw.size().get(2);
        if (sx < 1 || sy < 1 || sz < 1 || sx > 16 || sy > 16 || sz > 16) {
            return DataResult.error(() -> "size out of range 1..16");
        }
        List<Placement> out = new ArrayList<>();
        int crates = 0;
        for (Layer layer : raw.layers()) {
            int y = layer.y();
            if (y < -1 || y > sy - 1) {
                return DataResult.error(() -> "layer y " + y + " outside -1.." + (sy - 1));
            }
            if (layer.rows().size() != sz) {
                return DataResult.error(() -> "layer y " + y + " has " + layer.rows().size() + " rows, expected " + sz);
            }
            for (int z = 0; z < sz; z++) {
                String row = layer.rows().get(z);
                if (row.length() != sx) {
                    int zz = z;
                    return DataResult.error(() -> "layer y " + y + " row " + zz + " is " + row.length()
                            + " cells, expected " + sx);
                }
                for (int x = 0; x < sx; x++) {
                    char c = row.charAt(x);
                    if (c == '.') {
                        continue;
                    }
                    Optional<Placement> cell = cell(c, x, y, z);
                    if (cell.isEmpty()) {
                        return DataResult.error(() -> "unknown cell '" + c + "'");
                    }
                    if (cell.get().kind() == Kind.SALVAGE_CRATE) {
                        crates++;
                    }
                    out.add(cell.get());
                }
            }
        }
        if (crates != raw.lootBlocks()) {
            int found = crates;
            return DataResult.error(() -> "loot_blocks " + raw.lootBlocks() + " but " + found + " crate cell(s)");
        }
        return DataResult.success(new WreckLayout(sx, sy, sz, crates, out));
    }

    private static Optional<Placement> cell(char c, int x, int y, int z) {
        return switch (c) {
            case 'P' -> Optional.of(new Placement(x, y, z, Kind.HULL_PLATING, Direction.NORTH));
            case 'S' -> Optional.of(new Placement(x, y, z, Kind.SCORCHED_PANEL, Direction.NORTH));
            case 'n' -> Optional.of(new Placement(x, y, z, Kind.BENT_STRUT, Direction.NORTH));
            case 'e' -> Optional.of(new Placement(x, y, z, Kind.BENT_STRUT, Direction.EAST));
            case 's' -> Optional.of(new Placement(x, y, z, Kind.BENT_STRUT, Direction.SOUTH));
            case 'w' -> Optional.of(new Placement(x, y, z, Kind.BENT_STRUT, Direction.WEST));
            case 'C' -> Optional.of(new Placement(x, y, z, Kind.SALVAGE_CRATE, Direction.NORTH));
            case 'c' -> Optional.of(new Placement(x, y, z, Kind.SALVAGE_CRATE, Direction.WEST));
            default -> Optional.empty();
        };
    }

    /** Re-encodes as layer rows (crates facing east/south, which the key cannot write, encode as {@code C}). */
    Raw toRaw() {
        int minY = placements.stream().mapToInt(Placement::y).min().orElse(0);
        List<Layer> layers = new ArrayList<>();
        for (int y = Math.min(0, minY); y < sizeY; y++) {
            char[][] grid = new char[sizeZ][sizeX];
            for (char[] row : grid) {
                java.util.Arrays.fill(row, '.');
            }
            boolean any = false;
            for (Placement p : placements) {
                if (p.y() == y) {
                    grid[p.z()][p.x()] = letter(p);
                    any = true;
                }
            }
            if (!any && y < 0) {
                continue;
            }
            List<String> rows = new ArrayList<>();
            for (char[] row : grid) {
                rows.add(new String(row));
            }
            layers.add(new Layer(y, rows));
        }
        return new Raw(List.of(sizeX, sizeY, sizeZ), lootBlocks, layers);
    }

    private static char letter(Placement p) {
        return switch (p.kind()) {
            case HULL_PLATING -> 'P';
            case SCORCHED_PANEL -> 'S';
            case BENT_STRUT -> switch (p.facing()) {
                case EAST -> 'e';
                case SOUTH -> 's';
                case WEST -> 'w';
                default -> 'n';
            };
            case SALVAGE_CRATE -> p.facing() == Direction.WEST ? 'c' : 'C';
        };
    }

    /** The layout turned clockwise (seen from above) by {@code rotation}; facings follow. */
    public WreckLayout rotate(Rotation rotation) {
        int steps = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        WreckLayout out = this;
        for (int i = 0; i < steps; i++) {
            out = out.clockwise();
        }
        return out;
    }

    private WreckLayout clockwise() {
        List<Placement> turned = new ArrayList<>(placements.size());
        for (Placement p : placements) {
            turned.add(new Placement(sizeZ - 1 - p.z(), p.y(), p.x(), p.kind(), p.facing().getClockWise()));
        }
        return new WreckLayout(sizeZ, sizeY, sizeX, lootBlocks, turned);
    }

    /** The number of crate cells. */
    public long crateCells() {
        return placements.stream().filter(p -> p.kind() == Kind.SALVAGE_CRATE).count();
    }
}
