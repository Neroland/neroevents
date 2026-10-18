package za.co.neroland.neroevents.testing;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.reward.RewardCrates;

/** An inventory with a fixed number of items it can still take (any item). */
public final class FakeInventoryModel implements RewardCrates.InventoryModel {

    private int capacity;
    public final Map<Identifier, Integer> received = new LinkedHashMap<>();

    public FakeInventoryModel(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int accept(Identifier item, int count) {
        int taken = Math.max(0, Math.min(capacity, count));
        capacity -= taken;
        if (taken > 0) {
            received.merge(item, taken, Integer::sum);
        }
        return taken;
    }
}
