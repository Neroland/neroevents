package za.co.neroland.neroevents.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.event.ServerPayloadContext;

/** No providers ⇒ empty (the density fallback); with providers, registration order wins. */
class RaidTargetsTest {

    private record Fixed(String id, Optional<BlockPos> answer) implements RaidTargetProvider {
        @Override
        public Optional<BlockPos> approachAnchor(ServerPayloadContext ctx, int spawnRadiusMax) {
            return answer;
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        RaidTargets.resetForTests();
    }

    @Test
    void noProvidersIsEmpty() {
        assertEquals(0, RaidTargets.size());
        assertTrue(RaidTargets.pick(null, 28).isEmpty());
    }

    @Test
    void firstPresentAnswerInRegistrationOrder() {
        RaidTargets.register(new Fixed("colony", Optional.empty()));
        RaidTargets.register(new Fixed("station", Optional.of(new BlockPos(1, 2, 3))));
        RaidTargets.register(new Fixed("installation", Optional.of(new BlockPos(9, 9, 9))));
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), RaidTargets.pick(null, 28));
    }

    @Test
    void aThrowingProviderIsSkipped() {
        RaidTargets.register(new RaidTargetProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public Optional<BlockPos> approachAnchor(ServerPayloadContext ctx, int spawnRadiusMax) {
                throw new IllegalStateException("boom");
            }
        });
        RaidTargets.register(new Fixed("station", Optional.of(new BlockPos(4, 5, 6))));
        assertEquals(Optional.of(new BlockPos(4, 5, 6)), RaidTargets.pick(null, 28));
    }
}
