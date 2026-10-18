package za.co.neroland.neroevents.compat.agriculture;

import java.util.function.BiFunction;

import net.minecraft.resources.Identifier;

/**
 * What NeroEvents needs from NeroAgriculture. A pure interface: it imports nothing from NeroAgriculture;
 * the one implementation that does is loaded by {@link java.util.ServiceLoader} only when NeroAgriculture
 * is present, and callers go through the façade, which catches anything a method throws.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface AgricultureBridge {

    /**
     * Registers one provider; the function is called by NeroAgriculture per (dimension, time) and returns
     * {@code {growth, yield, environment}}, or {@code null} for identity. May throw.
     */
    void registerCycleProvider(BiFunction<Identifier, Long, float[]> modifier);
}
