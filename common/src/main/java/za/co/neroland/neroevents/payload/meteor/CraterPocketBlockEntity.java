package za.co.neroland.neroevents.payload.meteor;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.payload.registry.CorePayloadBlockEntities;

/**
 * The crater pocket's block entity: which instance placed it, which loot table it rolls, the VU budget and
 * the seed — so the loot is fixed when the crater forms, not when it is broken. Holds no player data.
 */
public class CraterPocketBlockEntity extends BlockEntity {

    private static final String KEY_INSTANCE = "NeroEventsInstance";
    private static final String KEY_TABLE = "LootTable";
    private static final String KEY_BUDGET = "LootBudget";
    private static final String KEY_SEED = "Seed";
    private static final String KEY_ROLLED = "Rolled";

    private @Nullable UUID instanceId;
    private @Nullable Identifier lootTable;
    private long lootBudget;
    private long seed;
    private boolean rolled;

    public CraterPocketBlockEntity(BlockPos pos, BlockState state) {
        super(CorePayloadBlockEntities.CRATER_POCKET.get(), pos, state);
    }

    /** Set once by {@link CraterBuilder} right after the pocket is placed. */
    public void configure(@Nullable UUID instance, Identifier table, long budget, long lootSeed) {
        this.instanceId = instance;
        this.lootTable = table;
        this.lootBudget = budget;
        this.seed = lootSeed;
        this.rolled = false;
        setChanged();
    }

    public @Nullable UUID instanceId() {
        return instanceId;
    }

    public @Nullable Identifier lootTable() {
        return lootTable;
    }

    public long lootBudget() {
        return lootBudget;
    }

    public long seed() {
        return seed;
    }

    /** Marks the loot as rolled; returns false when it already was (a pocket pays out once). */
    public boolean markRolled() {
        if (rolled) {
            return false;
        }
        rolled = true;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        UUID instance = instanceId;
        if (instance != null) {
            output.putString(KEY_INSTANCE, instance.toString());
        }
        Identifier table = lootTable;
        if (table != null) {
            output.putString(KEY_TABLE, table.toString());
        }
        output.putLong(KEY_BUDGET, lootBudget);
        output.putLong(KEY_SEED, seed);
        output.putBoolean(KEY_ROLLED, rolled);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        instanceId = parseUuid(input.getStringOr(KEY_INSTANCE, ""));
        String table = input.getStringOr(KEY_TABLE, "");
        lootTable = table.isEmpty() ? null : Identifier.tryParse(table);
        lootBudget = input.getLongOr(KEY_BUDGET, 0L);
        seed = input.getLongOr(KEY_SEED, 0L);
        rolled = input.getBooleanOr(KEY_ROLLED, false);
    }

    static @Nullable UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
