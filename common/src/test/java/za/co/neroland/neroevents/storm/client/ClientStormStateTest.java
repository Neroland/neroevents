package za.co.neroland.neroevents.storm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.storm.StormExposurePayload;
import za.co.neroland.neroevents.storm.StormSyncPayload;
import za.co.neroland.neroevents.storm.StormVariant;

class ClientStormStateTest {

    private static final Identifier CINDARA = Identifier.fromNamespaceAndPath("nerospace", "cindara");
    private static final Identifier GLACIRA = Identifier.fromNamespaceAndPath("nerospace", "glacira");

    @BeforeEach
    @AfterEach
    void reset() {
        ClientStormState.clear();
    }

    private static void ticks(int n) {
        for (int i = 0; i < n; i++) {
            ClientStormState.tick();
        }
    }

    @Test
    void fadesInOverTheRampAndOutOnNone() {
        ClientStormState.onSync(StormSyncPayload.active(CINDARA, StormVariant.ION, 3, 100, 0));
        assertTrue(ClientStormState.view(CINDARA).isEmpty(), "nothing drawn before the first tick");
        ticks(50);
        assertEquals(0.5F, ClientStormState.view(CINDARA).orElseThrow().strength(), 1e-4);
        ticks(60);
        assertEquals(1.0F, ClientStormState.view(CINDARA).orElseThrow().strength(), 1e-4);
        assertTrue(ClientStormState.view(GLACIRA).isEmpty(), "per dimension");

        ClientStormState.onSync(StormSyncPayload.none(CINDARA)); // fades over 200
        ticks(100);
        assertEquals(0.5F, ClientStormState.view(CINDARA).orElseThrow().strength(), 1e-4);
        ticks(100);
        assertTrue(ClientStormState.view(CINDARA).isEmpty());
    }

    @Test
    void strengthScalesWithIntensity() {
        ClientStormState.onSync(StormSyncPayload.active(GLACIRA, StormVariant.CRYO, 1, 100, 90));
        ticks(100);
        ClientStormState.View view = ClientStormState.view(GLACIRA).orElseThrow();
        assertEquals(1.0F / 3.0F, view.strength(), 1e-4);
        assertEquals(90, view.windYaw());
        assertEquals(StormVariant.CRYO, view.variant());
    }

    @Test
    void repeatedSyncIsIdempotent() {
        ClientStormState.onSync(StormSyncPayload.active(CINDARA, StormVariant.ION, 2, 100, 0));
        ticks(100);
        ClientStormState.onSync(StormSyncPayload.active(CINDARA, StormVariant.ION, 2, 100, 0));
        assertEquals(2.0F / 3.0F, ClientStormState.view(CINDARA).orElseThrow().strength(), 1e-4,
                "a refresh does not restart the fade");
    }

    @Test
    void loopVolumeFollowsExposure() {
        ClientStormState.onSync(StormSyncPayload.active(CINDARA, StormVariant.ION, 3, 20, 0));
        ticks(20);
        assertEquals(ClientStormState.SHELTERED_VOLUME, ClientStormState.view(CINDARA).orElseThrow().loopVolume(), 1e-4);
        ClientStormState.onExposure(new StormExposurePayload(true));
        ticks(20);
        assertEquals(1.0F, ClientStormState.view(CINDARA).orElseThrow().loopVolume(), 1e-4);
        assertTrue(ClientStormState.view(CINDARA).orElseThrow().exposed());
    }

    @Test
    void lookMath() {
        assertEquals(0xFF102030, StormLook.tintArgb(0xFF102030, StormVariant.SOLAR, 0.0F));
        int full = StormLook.tintArgb(0xFF000000, StormVariant.SOLAR, 1.0F);
        assertEquals(0xFF000000, full & 0xFF000000, "alpha kept");
        assertEquals(Math.round(0xFF * 0.55F), (full >> 16) & 0xFF);
        assertEquals(192.0F, StormLook.fogEnd(192.0F, StormVariant.DUST, 0.0F), 1e-4);
        assertEquals(40.0F, StormLook.fogEnd(192.0F, StormVariant.DUST, 1.0F), 1e-4);
        assertEquals(30.0F, StormLook.fogEnd(30.0F, StormVariant.DUST, 1.0F), 1e-4, "never pushes fog out");
        assertEquals(8.0F, StormLook.fogStart(100.0F, StormVariant.DUST, 1.0F), 1e-4);
    }
}
