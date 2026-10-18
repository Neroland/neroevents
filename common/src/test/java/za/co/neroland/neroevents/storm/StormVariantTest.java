package za.co.neroland.neroevents.storm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.storm.StormVariant.EffectSpec;

class StormVariantTest {

    private static EffectSpec e(String name, int amp) {
        return new EffectSpec(name, amp);
    }

    @Test
    void solarEffectsPerIntensity() {
        assertEquals(List.of(e("weakness", 0)), StormVariant.SOLAR.effects(1));
        assertEquals(List.of(e("weakness", 0), e("hunger", 0)), StormVariant.SOLAR.effects(2));
        assertEquals(List.of(e("weakness", 1), e("hunger", 0)), StormVariant.SOLAR.effects(3));
    }

    @Test
    void dustEffectsPerIntensity() {
        assertEquals(List.of(e("slowness", 0)), StormVariant.DUST.effects(1));
        assertEquals(List.of(e("slowness", 0), e("mining_fatigue", 0)), StormVariant.DUST.effects(2));
        assertEquals(List.of(e("slowness", 1), e("mining_fatigue", 0)), StormVariant.DUST.effects(3));
    }

    @Test
    void ionEffectsAndJolt() {
        assertEquals(List.of(), StormVariant.ION.effects(1), "sparks only");
        assertEquals(List.of(e("weakness", 0)), StormVariant.ION.effects(2));
        assertEquals(List.of(e("weakness", 0)), StormVariant.ION.effects(3));
        assertEquals(Optional.empty(), StormVariant.ION.jolt(2));
        assertEquals(Optional.of(e("nausea", 0)), StormVariant.ION.jolt(3));
        assertEquals(Optional.empty(), StormVariant.SOLAR.jolt(3));
        assertTrue(StormEffects.joltDue(400L));
        assertTrue(StormEffects.joltDue(439L));
        assertFalse(StormEffects.joltDue(440L));
    }

    @Test
    void cryoEffectsAndFreezing() {
        assertEquals(List.of(e("slowness", 0)), StormVariant.CRYO.effects(1));
        assertEquals(List.of(e("slowness", 0)), StormVariant.CRYO.effects(2));
        assertEquals(List.of(e("slowness", 1), e("mining_fatigue", 0)), StormVariant.CRYO.effects(3));
        assertTrue(StormVariant.CRYO.freezes());
        assertFalse(StormVariant.SOLAR.freezes() || StormVariant.DUST.freezes() || StormVariant.ION.freezes());
        assertEquals(100, StormVariant.freezeLevel(1));
        assertEquals(139, StormVariant.freezeLevel(2));
        assertEquals(220, StormVariant.freezeLevel(3));
        assertEquals(100, StormVariant.freezeLevel(0), "clamped to 1");
        assertEquals(220, StormVariant.freezeLevel(9), "clamped to 3");
        // Vanilla thaws 2/tick over the 40-tick interval and hurts only at >= 140.
        assertTrue(StormVariant.freezeLevel(2) < 140, "intensity 2 never damages");
        assertTrue(StormVariant.freezeLevel(3) - 2 * StormVariant.EFFECT_INTERVAL_TICKS >= 140,
                "intensity 3 stays frozen across the whole interval");
    }

    @Test
    void intensityIsClamped() {
        assertEquals(StormVariant.SOLAR.effects(1), StormVariant.SOLAR.effects(0));
        assertEquals(StormVariant.SOLAR.effects(3), StormVariant.SOLAR.effects(9));
    }

    @Test
    void drainFlags() {
        assertTrue(StormVariant.SOLAR.drains());
        assertTrue(StormVariant.ION.drains());
        assertFalse(StormVariant.DUST.drains());
        assertFalse(StormVariant.CRYO.drains());
        assertEquals(1.0D, StormVariant.SOLAR.drainMultiplier());
        assertEquals(1.5D, StormVariant.ION.drainMultiplier());
    }

    @Test
    void agricultureTriples() {
        assertArrayEquals(new float[] {0.9F, 1.0F, 0.9F}, StormVariant.SOLAR.agriculture());
        assertArrayEquals(new float[] {0.6F, 0.8F, 0.7F}, StormVariant.DUST.agriculture());
        assertArrayEquals(new float[] {0.8F, 0.9F, 0.8F}, StormVariant.ION.agriculture());
        assertArrayEquals(new float[] {0.5F, 0.7F, 0.6F}, StormVariant.CRYO.agriculture());
        float[] copy = StormVariant.DUST.agriculture();
        copy[0] = 9.0F;
        assertEquals(0.6F, StormVariant.DUST.agriculture()[0], "callers get a copy");
    }

    @Test
    void planetMapping() {
        assertEquals(Optional.of(StormVariant.DUST), StormVariant.forPlanet(id("nerospace", "greenxertz")));
        assertEquals(Optional.of(StormVariant.ION), StormVariant.forPlanet(id("nerospace", "cindara")));
        assertEquals(Optional.of(StormVariant.CRYO), StormVariant.forPlanet(id("nerospace", "glacira")));
        assertEquals(Optional.empty(), StormVariant.forPlanet(id("nerospace", "station")));
        assertEquals(Optional.empty(), StormVariant.forPlanet(id("other", "cindara")));
        assertEquals(Optional.empty(), StormVariant.forPlanet(null));
    }

    @Test
    void wireIdsAndNamesRoundTrip() {
        for (StormVariant v : StormVariant.values()) {
            assertEquals(Optional.of(v), StormVariant.byWireId(v.wireId()));
            assertEquals(Optional.of(v), StormVariant.byName(v.serializedName()));
        }
        assertEquals(Optional.empty(), StormVariant.byWireId(StormVariant.NONE_WIRE_ID));
        assertEquals(Optional.empty(), StormVariant.byName("auto"));
    }

    private static Identifier id(String ns, String path) {
        return Identifier.fromNamespaceAndPath(ns, path);
    }
}
