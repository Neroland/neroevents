package za.co.neroland.neroevents.reward;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * One instance's unclaimed rewards for one player: reward stacks and experience. Entries stay per instance
 * (never merged across instances) and expire whole.
 *
 * <p>Stacks are stored as {@code (item id, count)}: reward stacks are NeroEvents' own plain items with no
 * components and no player content, so nothing is lost and the store needs no registry access to load or
 * back up. The item stack is built only when the player claims it.
 */
public record CrateEntry(UUID instanceId, Identifier definitionId, List<Stack> items, long xp, long at) {

    /** One reward stack ({@code count} ≤ {@link #MAX_STACK}). */
    public record Stack(Identifier item, int count) {

        public static final Codec<Stack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("item").forGetter(Stack::item),
                Codec.INT.fieldOf("count").forGetter(Stack::count)
        ).apply(inst, Stack::new));
    }

    /** The stack size reward stacks are split at. */
    public static final int MAX_STACK = 64;

    public static final Codec<CrateEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("instance").forGetter(CrateEntry::instanceId),
            Identifier.CODEC.fieldOf("definition").forGetter(CrateEntry::definitionId),
            Stack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CrateEntry::items),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(CrateEntry::xp),
            Codec.LONG.fieldOf("at").forGetter(CrateEntry::at)
    ).apply(inst, CrateEntry::new));

    public CrateEntry {
        items = List.copyOf(items);
    }

    public boolean empty() {
        return items.isEmpty() && xp <= 0L;
    }

    public int itemCount() {
        int sum = 0;
        for (Stack stack : items) {
            sum += stack.count();
        }
        return sum;
    }
}
