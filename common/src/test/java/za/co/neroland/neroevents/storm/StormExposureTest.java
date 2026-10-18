package za.co.neroland.neroevents.storm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class StormExposureTest {

    @Test
    void openSkyOutsidePressureIsExposed() {
        assertTrue(StormExposure.exposed(true, Optional.of("VACUUM"), false));
        assertTrue(StormExposure.exposed(true, Optional.of("BREATHABLE"), false), "breathable air is no shelter");
        assertTrue(StormExposure.exposed(true, Optional.of("TERRAFORMING"), false));
    }

    @Test
    void openSkyInsidePressureIsSheltered() {
        assertFalse(StormExposure.exposed(true, Optional.of("PRESSURIZED"), false));
    }

    @Test
    void roofedIsShelteredWhateverTheAir() {
        assertFalse(StormExposure.exposed(false, Optional.of("VACUUM"), false));
        assertFalse(StormExposure.exposed(false, Optional.of("PRESSURIZED"), false));
    }

    @Test
    void unreadableAtmosphereIsSheltered() {
        assertFalse(StormExposure.exposed(true, Optional.empty(), false));
    }

    @Test
    void spectatorOrCreativeIsNeverExposed() {
        assertFalse(StormExposure.exposed(true, Optional.of("VACUUM"), true));
    }
}
