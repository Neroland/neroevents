package za.co.neroland.neroevents.compat.factions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.PayloadTypes;
import za.co.neroland.neroevents.testing.compat.FakeFactionsBridge;
import za.co.neroland.neroevents.war.FactionWarPayload;

/** The NeroFactions façade with NeroFactions present and absent: awards, failures and the no-op fallback. */
class FactionsCompatTest {

    private static final Identifier RED = Identifier.fromNamespaceAndPath("test", "red");
    private static final Identifier REASON = FactionWarPayload.REASON_WAR_VICTORY;

    private final FakeFactionsBridge fake = new FakeFactionsBridge();
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PayloadTypes.resetForTests();
        FactionsCompat.resetForTests();
    }

    @AfterEach
    void tearDown() {
        FactionsCompat.resetForTests();
        PayloadTypes.resetForTests();
    }

    @Test
    void absentNeroFactionsIsANoOp() {
        FactionsCompat.initForTests(id -> false, fake);
        assertFalse(FactionsCompat.available());
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
        assertTrue(FactionsCompat.factionIds(null).isEmpty());
        assertTrue(FactionsCompat.displayName(null, RED).isEmpty());
        assertEquals(0, fake.calls, "the bridge is never asked");
        assertTrue(PayloadTypes.get(FactionWarPayload.ID).isEmpty(), "faction wars stay out of the pool");
    }

    @Test
    void presentWithoutTheBridgeInThisJarIsANoOp() {
        FactionsCompat.initForTests(FactionsCompat.MOD_ID::equals, null);
        assertFalse(FactionsCompat.available());
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
    }

    @Test
    void presentReturnsTheAppliedAmountAndDoesNotDeduplicate() {
        fake.awardAnswer = 6;
        FactionsCompat.initForTests(FactionsCompat.MOD_ID::equals, fake);
        assertTrue(FactionsCompat.available());
        assertTrue(PayloadTypes.get(FactionWarPayload.ID).isPresent());
        assertEquals(6, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
        assertEquals(6, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
        assertEquals(2, fake.awards, "NeroFactions does not deduplicate: the war payload awards once");
    }

    @Test
    void capReachedOrInvalidCallsAnswerZero() {
        fake.awardAnswer = 0;
        FactionsCompat.initForTests(FactionsCompat.MOD_ID::equals, fake);
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON), "daily EVENT cap reached");
        fake.awardAnswer = -3;
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON), "never negative");
        int asked = fake.awards;
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 0, REASON));
        assertEquals(0, FactionsCompat.awardEvent(null, null, RED, 10, REASON));
        assertEquals(0, FactionsCompat.awardEvent(null, player, null, 10, REASON));
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, null));
        assertEquals(asked, fake.awards, "invalid calls never reach NeroFactions");
    }

    @Test
    void aThrowingApiTurnsTheIntegrationOff() {
        fake.throwing = true;
        FactionsCompat.initForTests(FactionsCompat.MOD_ID::equals, fake);
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
        assertFalse(FactionsCompat.available());
        int calls = fake.calls;
        assertEquals(0, FactionsCompat.awardEvent(null, player, RED, 10, REASON));
        assertEquals(calls, fake.calls, "a failed integration is not asked again");
    }
}
