package za.co.neroland.neroevents.compat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.agriculture.AgricultureBridge;
import za.co.neroland.neroevents.compat.agriculture.AgricultureCompat;
import za.co.neroland.neroevents.compat.colonies.ColoniesBridge;
import za.co.neroland.neroevents.compat.colonies.ColoniesCompat;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.compat.creatures.CreaturesCompat;
import za.co.neroland.neroevents.compat.economy.EconomyCompat;
import za.co.neroland.neroevents.compat.factions.FactionsBridge;
import za.co.neroland.neroevents.compat.factions.FactionsCompat;
import za.co.neroland.neroevents.compat.nerospace.NerospaceBridge;
import za.co.neroland.neroevents.compat.nerospace.NerospaceCompat;
import za.co.neroland.neroevents.compat.opac.OpacBridge;
import za.co.neroland.neroevents.compat.opac.OpacCompat;

/**
 * Common init slot 5: every sibling façade, in a fixed order. Each façade registers its payload types or
 * protection source only when its sibling is loaded and this jar carries the bridge, so the event pool and the
 * protection chain are right by construction on any combination of installed mods.
 */
public final class CompatRegistry {

    /** Fake bridges for {@link #initForTests}; a {@code null} entry means "this jar has no bridge for it". */
    public record Fakes(@Nullable NerospaceBridge nerospace, @Nullable AgricultureBridge agriculture,
            @Nullable ColoniesBridge colonies, @Nullable OpacBridge opac, @Nullable CreaturesBridge creatures,
            @Nullable FactionsBridge factions) {

        public static Fakes none() {
            return new Fakes(null, null, null, null, null, null);
        }
    }

    private static boolean initialised;

    private CompatRegistry() {
    }

    /** Called once from common init slot 5 with the loader's {@code isModLoaded}. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        NerospaceCompat.init(presence);
        AgricultureCompat.init(presence);
        ColoniesCompat.init(presence);
        OpacCompat.init(presence);
        CreaturesCompat.init(presence);
        FactionsCompat.init(presence);
        EconomyCompat.init(presence);
    }

    /** Integration id → whether it is live, for {@code /neroevents status} and the pool test. */
    public static Map<String, Boolean> availability() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put(NerospaceCompat.MOD_ID, NerospaceCompat.available());
        out.put(AgricultureCompat.MOD_ID, AgricultureCompat.available());
        out.put(ColoniesCompat.MOD_ID, ColoniesCompat.available());
        out.put(OpacCompat.MOD_ID, OpacCompat.available());
        out.put(CreaturesCompat.MOD_ID, CreaturesCompat.available());
        out.put(FactionsCompat.MOD_ID, FactionsCompat.available());
        out.put(EconomyCompat.MOD_ID, EconomyCompat.available());
        return out;
    }

    /** Tests only: forgets every façade's state. */
    public static synchronized void resetForTests() {
        initialised = false;
        NerospaceCompat.resetForTests();
        AgricultureCompat.resetForTests();
        ColoniesCompat.resetForTests();
        OpacCompat.resetForTests();
        CreaturesCompat.resetForTests();
        FactionsCompat.resetForTests();
        EconomyCompat.resetForTests();
    }

    /** Tests only: the production order against a fake presence and fake bridges. */
    public static synchronized void initForTests(ModPresence presence, Fakes fakes) {
        resetForTests();
        initialised = true;
        NerospaceCompat.initForTests(presence, fakes.nerospace());
        AgricultureCompat.initForTests(presence, fakes.agriculture());
        ColoniesCompat.initForTests(presence, fakes.colonies());
        OpacCompat.initForTests(presence, fakes.opac());
        CreaturesCompat.initForTests(presence, fakes.creatures());
        FactionsCompat.initForTests(presence, fakes.factions());
        EconomyCompat.initForTests(presence);
    }
}
