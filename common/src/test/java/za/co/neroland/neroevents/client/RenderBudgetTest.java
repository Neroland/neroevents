package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.client.fx.RenderBudget;

/** Pins the renderer budget counters behind {@code /nsevrender}. */
class RenderBudgetTest {

    @AfterEach
    void clear() {
        RenderBudget.reset();
    }

    @Test
    void averagesDrawCountsAndFrameTimeOverClosedFrames() {
        for (int f = 0; f < 4; f++) {
            RenderBudget.openFrame();
            for (int i = 0; i < 40; i++) {
                RenderBudget.drawn(RenderBudget.Kind.METEOR);
            }
            for (int i = 0; i < 10; i++) {
                RenderBudget.drawn(RenderBudget.Kind.POD);
            }
            RenderBudget.culled();
            RenderBudget.nanos(2_000_000L);
            RenderBudget.particles(160);
            RenderBudget.closeFrame(f % 2 == 0 ? 10_000_000L : 20_000_000L);
        }
        RenderBudget.Snapshot a = RenderBudget.average();
        assertEquals(4, a.frames());
        assertEquals(40.0D, a.meteors());
        assertEquals(10.0D, a.pods());
        assertEquals(1.0D, a.culled());
        assertEquals(2.0D, a.rendererMillis(), 1.0e-9);
        assertEquals(15.0D, a.frameMillis(), 1.0e-9);
        assertEquals(20.0D, a.peakFrameMillis(), 1.0e-9);
        assertEquals(160.0D, a.particlesPerFrame());
        assertEquals(40, RenderBudget.meteorsLastFrame(), "the trail cap reads the last frame's meteors");
        assertTrue(RenderBudget.record().contains("meteors 40.0"));
    }

    @Test
    void aNewGameTimeOrPartialTickClosesTheFrame() {
        RenderBudget.frame(100L, 0.25F);
        RenderBudget.drawn(RenderBudget.Kind.METEOR);
        RenderBudget.frame(100L, 0.25F); // same frame
        RenderBudget.drawn(RenderBudget.Kind.METEOR);
        RenderBudget.frame(100L, 0.5F); // next frame: the first closes with two meteors
        assertEquals(1, RenderBudget.average().frames());
        assertEquals(2, RenderBudget.meteorsLastFrame());
    }

    @Test
    void resetEmptiesTheWindow() {
        RenderBudget.openFrame();
        RenderBudget.drawn(RenderBudget.Kind.DEBRIS);
        RenderBudget.closeFrame(1L);
        RenderBudget.reset();
        assertEquals(0, RenderBudget.average().frames());
        assertEquals(0, RenderBudget.meteorsLastFrame());
    }
}
