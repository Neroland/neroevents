package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.5 only (empty on every other line). Before 1.21.6 block entities and entities saved
// to a CompoundTag; Neroland Core's data.compat.ValueInput/ValueOutput stand in for the 1.21.6+ types but
// only carry the common accessors. The build rewrites the few other ValueInput/ValueOutput calls Nerospace
// makes (doubles, floats, nullable codecs, child objects, item lists) to these helpers on those lines, so the
// stores read and write exactly the same keys as on 1.21.6+.

//? if <1.21.6 {
/*import com.mojang.serialization.Codec;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.compat.ValueInput;
import za.co.neroland.nerolandcore.data.compat.ValueOutput;

public final class Values {

    private Values() {
    }

    public static void putDouble(ValueOutput output, String key, double value) {
        output.tag().putDouble(key, value);
    }

    public static void putFloat(ValueOutput output, String key, float value) {
        output.tag().putFloat(key, value);
    }

    public static double getDoubleOr(ValueInput input, String key, double fallback) {
        //? if >=1.21.5 {
        return input.tag().getDoubleOr(key, fallback);
        //?} else {
        /^return input.tag().contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? input.tag().getDouble(key) : fallback;
        ^///?}
    }

    public static float getFloatOr(ValueInput input, String key, float fallback) {
        //? if >=1.21.5 {
        return input.tag().getFloatOr(key, fallback);
        //?} else {
        /^return input.tag().contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? input.tag().getFloat(key) : fallback;
        ^///?}
    }

    public static void putByte(ValueOutput output, String key, byte value) {
        output.tag().putByte(key, value);
    }

    public static byte getByteOr(ValueInput input, String key, byte fallback) {
        //? if >=1.21.5 {
        return input.tag().getByteOr(key, fallback);
        //?} else {
        /^return input.tag().contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? input.tag().getByte(key) : fallback;
        ^///?}
    }

    public static <T> void storeNullable(ValueOutput output, String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            output.store(key, codec, value);
        }
    }

    public static ValueOutput child(ValueOutput output, String key) {
        CompoundTag sub = new CompoundTag();
        output.tag().put(key, sub);
        return ValueOutput.of(sub, output.registries());
    }

    public static ValueInput childOrEmpty(ValueInput input, String key) {
        //? if >=1.21.5 {
        return ValueInput.of(input.tag().getCompoundOrEmpty(key), input.registries());
        //?} else {
        /^return ValueInput.of(input.tag().getCompound(key), input.registries());
        ^///?}
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(output.tag(), items, output.registries());
    }

    public static void loadAllItems(ValueInput input, NonNullList<ItemStack> items) {
        ContainerHelper.loadAllItems(input.tag(), items, input.registries());
    }
}
*///?}
