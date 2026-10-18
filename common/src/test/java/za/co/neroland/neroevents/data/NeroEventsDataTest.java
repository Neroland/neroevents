package za.co.neroland.neroevents.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

/**
 * The erasure seam: registered with Core exactly once, walks every added store, keeps going past a
 * failing store and then reports the request as incomplete. Plain JVM, no game bootstrap (the server
 * is {@code null}, so no backup refresh is attempted).
 */
class NeroEventsDataTest {

    @Test
    void registersWithCoreExactlyOnce() {
        int before = PlayerDataErasure.registeredCount();
        boolean wasRegistered = NeroEventsData.isRegistered();
        NeroEventsData.init();
        NeroEventsData.init();
        assertTrue(NeroEventsData.isRegistered());
        assertEquals(wasRegistered ? before : before + 1, PlayerDataErasure.registeredCount());
    }

    @Test
    void walksEveryStoreAndReportsFailures() {
        List<String> erased = new ArrayList<>();
        NeroEventsData.StoreEraser failing = store("failing", erased, true);
        NeroEventsData.StoreEraser healthy = store("healthy", erased, false);
        NeroEventsData.addStore(failing);
        NeroEventsData.addStore(healthy);
        NeroEventsData.addStore(healthy); // duplicate ignored
        try {
            assertEquals(2, NeroEventsData.storeCount());
            UUID player = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> NeroEventsData.eraser().erase(null, player));
            assertEquals(List.of("failing", "healthy"), erased);
        } finally {
            NeroEventsData.removeStore(failing);
            NeroEventsData.removeStore(healthy);
        }
        assertEquals(0, NeroEventsData.storeCount());
    }

    private static NeroEventsData.StoreEraser store(String name, List<String> log, boolean fail) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                log.add(name);
                if (fail) {
                    throw new IllegalStateException("simulated store failure");
                }
            }

            @Override
            public void backupNow(MinecraftServer server) {
                throw new AssertionError("no backup refresh without a live server");
            }
        };
    }
}
