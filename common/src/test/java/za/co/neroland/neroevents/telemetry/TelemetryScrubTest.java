package za.co.neroland.neroevents.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** POPIA/GDPR: the OS account never survives {@link NeroEventsTelemetry#scrub}. Plain JVM. */
class TelemetryScrubTest {

    @Test
    void removesThisMachinesHomeDirectory() {
        String home = System.getProperty("user.home");
        String scrubbed = NeroEventsTelemetry.scrub("failed to read " + home + "/saves/world/level.dat");
        assertFalse(scrubbed.contains(home), scrubbed);
    }

    @Test
    void masksForeignHomePaths() {
        assertEquals("open /~\\mods\\x.jar", NeroEventsTelemetry.scrub("open C:\\Users\\someone\\mods\\x.jar"));
        assertEquals("/~/game", NeroEventsTelemetry.scrub("/home/someone/game"));
    }

    @Test
    void nullStaysNull() {
        assertNull(NeroEventsTelemetry.scrub(null));
    }
}
