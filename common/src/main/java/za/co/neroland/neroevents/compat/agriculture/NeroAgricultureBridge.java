package za.co.neroland.neroevents.compat.agriculture;

import java.util.function.BiFunction;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroagriculture.cycle.CycleApi;
import za.co.neroland.neroagriculture.cycle.CycleModifier;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.neroagriculture}. NeroAgriculture is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link AgricultureCompat} only after {@code isModLoaded("neroagriculture")}, and left out of a build with
 * {@code neroagriculture_compat=false}.
 */
public final class NeroAgricultureBridge implements AgricultureBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroAgricultureBridge() {
    }

    @Override
    public void registerCycleProvider(BiFunction<Identifier, Long, float[]> modifier) {
        CycleApi.PROVIDERS.add((dimension, time) -> {
            float[] m = modifier.apply(dimension, time);
            return m == null || m.length < 3 ? CycleModifier.IDENTITY : new CycleModifier(m[0], m[1], m[2]);
        });
    }
}
