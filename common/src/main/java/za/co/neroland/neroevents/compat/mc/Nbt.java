package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.4 only (empty on every other line). 1.21.5 gave CompoundTag typed getters with a
// fallback (getIntOr, getStringOr, ...), Optional array getters and getListOrEmpty; before it the getters took a
// tag-type check. The build rewrites those calls on the resumable-state tags to these helpers on those lines, so
// the same keys are read the same way.

//? if <1.21.5 {
/*import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class Nbt {

    private Nbt() {
    }

    public static int getIntOr(CompoundTag nbt, String key, int fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getInt(key) : fallback;
    }

    public static long getLongOr(CompoundTag nbt, String key, long fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getLong(key) : fallback;
    }

    public static byte getByteOr(CompoundTag nbt, String key, byte fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getByte(key) : fallback;
    }

    public static float getFloatOr(CompoundTag nbt, String key, float fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getFloat(key) : fallback;
    }

    public static double getDoubleOr(CompoundTag nbt, String key, double fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getDouble(key) : fallback;
    }

    public static boolean getBooleanOr(CompoundTag nbt, String key, boolean fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getBoolean(key) : fallback;
    }

    public static String getStringOr(CompoundTag nbt, String key, String fallback) {
        return nbt.contains(key, Tag.TAG_STRING) ? nbt.getString(key) : fallback;
    }

    public static Optional<long[]> getLongArray(CompoundTag nbt, String key) {
        if (!nbt.contains(key, Tag.TAG_LONG_ARRAY)) {
            return Optional.empty();
        }
        long[] value = nbt.getLongArray(key);
        return Optional.of(value);
    }

    public static Optional<int[]> getIntArray(CompoundTag nbt, String key) {
        if (!nbt.contains(key, Tag.TAG_INT_ARRAY)) {
            return Optional.empty();
        }
        int[] value = nbt.getIntArray(key);
        return Optional.of(value);
    }

    // A list of compounds (the only kind NeroEvents stores in a plain tag).
    public static ListTag getListOrEmpty(CompoundTag nbt, String key) {
        return nbt.getList(key, Tag.TAG_COMPOUND);
    }
}
*///?}
