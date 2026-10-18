package za.co.neroland.neroevents.testing.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.compat.agriculture.AgricultureBridge;

/** A NeroAgriculture stand-in that keeps the registered cycle providers. */
public final class FakeAgricultureBridge implements AgricultureBridge {

    public final List<BiFunction<Identifier, Long, float[]>> providers = new ArrayList<>();

    @Override
    public void registerCycleProvider(BiFunction<Identifier, Long, float[]> modifier) {
        providers.add(modifier);
    }
}
