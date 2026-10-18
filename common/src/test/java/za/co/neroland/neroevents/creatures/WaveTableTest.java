package za.co.neroland.neroevents.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

/** Scaling by online count, the caps, unknown types dropped, the seeded shuffle, and the codecs. */
class WaveTableTest {

    private static final Identifier PIRATE = Identifier.fromNamespaceAndPath("nerocreatures", "space_pirate");
    private static final Identifier DRONE = Identifier.fromNamespaceAndPath("nerocreatures", "rogue_drone");
    private static final Identifier GHOST = Identifier.fromNamespaceAndPath("nerocreatures", "ghost");

    private static WaveTable.Wave wave(int pirates, int drones) {
        return new WaveTable.Wave(List.of(new WaveTable.MobEntry(PIRATE, pirates), new WaveTable.MobEntry(DRONE, drones)),
                0, 4800);
    }

    @Test
    void oneOnlineIsTheTableAsWritten() {
        List<WaveTable.Planned> plan = WaveTable.scaled(wave(4, 2), 1, 0.25D, 24, id -> true);
        assertEquals(List.of(new WaveTable.Planned(PIRATE, 4), new WaveTable.Planned(DRONE, 2)), plan);
        assertEquals(6, WaveTable.total(plan));
    }

    @Test
    void zeroOnlineCountsAsOne() {
        assertEquals(6, WaveTable.total(WaveTable.scaled(wave(4, 2), 0, 0.25D, 24, id -> true)));
    }

    @Test
    void scalesByOnlineCount() {
        // factor 1 + 0.25 × (5 − 1) = 2 → 8 and 4
        List<WaveTable.Planned> plan = WaveTable.scaled(wave(4, 2), 5, 0.25D, 24, id -> true);
        assertEquals(List.of(new WaveTable.Planned(PIRATE, 8), new WaveTable.Planned(DRONE, 4)), plan);
        // factor 1.5 → 6 and 3
        assertEquals(9, WaveTable.total(WaveTable.scaled(wave(4, 2), 3, 0.25D, 24, id -> true)));
    }

    @Test
    void cappedByMaxPerWaveFromTheLargestLine() {
        List<WaveTable.Planned> plan = WaveTable.scaled(wave(20, 10), 1, 0.0D, 12, id -> true);
        assertEquals(12, WaveTable.total(plan));
        assertEquals(List.of(new WaveTable.Planned(PIRATE, 6), new WaveTable.Planned(DRONE, 6)), plan);
    }

    @Test
    void neverAboveTheInvasionBudgetMaximum() {
        assertEquals(WaveTable.MAX_BUDGET, WaveTable.cap(500));
        assertEquals(1, WaveTable.cap(0));
        List<WaveTable.Planned> plan = WaveTable.scaled(wave(32, 32), 50, 1.0D, 500, id -> true);
        assertEquals(WaveTable.MAX_BUDGET, WaveTable.total(plan));
    }

    @Test
    void unknownTypesAreDropped() {
        WaveTable.Wave w = new WaveTable.Wave(List.of(new WaveTable.MobEntry(GHOST, 5),
                new WaveTable.MobEntry(PIRATE, 3)), 0, 4800);
        List<WaveTable.Planned> plan = WaveTable.scaled(w, 1, 0.25D, 24, id -> !id.equals(GHOST));
        assertEquals(List.of(new WaveTable.Planned(PIRATE, 3)), plan);
    }

    @Test
    void flattenIsShuffledDeterministicallyBySeed() {
        List<WaveTable.Planned> plan = WaveTable.scaled(wave(10, 10), 1, 0.0D, 24, id -> true);
        List<Identifier> a = WaveTable.flatten(plan, new Random(7L));
        List<Identifier> b = WaveTable.flatten(plan, new Random(7L));
        List<Identifier> c = WaveTable.flatten(plan, new Random(8L));
        assertEquals(20, a.size());
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(10, a.stream().filter(PIRATE::equals).count());
    }

    @Test
    void configCodecReadsDefaultsAndRejectsBadTables() {
        var ok = WavePayloadBase.Config.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"waves": [{"mobs": [{"type": "nerocreatures:space_pirate", "count": 4}]},
                           {"mobs": [{"type": "nerocreatures:rogue_drone", "count": 2}], "delay_ticks": 600}]}
                """)).result();
        assertTrue(ok.isPresent());
        WavePayloadBase.Config config = ok.get();
        assertEquals(2, config.waves().size());
        assertEquals(4800, config.waves().get(0).timeoutTicks());
        assertEquals(600, config.waves().get(1).delayTicks());
        assertEquals(0.25D, config.scalePerOnline());
        assertEquals(24, config.maxPerWave());
        assertEquals(16, config.spawnRadiusMin());
        assertEquals(28, config.spawnRadiusMax());
        assertEquals(2, config.spawnPerTick());
        assertEquals(0.5D, config.successKillFraction());
        assertTrue(config.survivalCredit());
        assertEquals(false, config.blockDamage());

        assertTrue(WavePayloadBase.Config.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"waves\": []}")).result().isEmpty());
        assertTrue(WavePayloadBase.Config.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"waves\": [{\"mobs\": [{\"type\": \"nerocreatures:space_pirate\", \"count\": 40}]}]}"))
                .result().isEmpty());
    }
}
