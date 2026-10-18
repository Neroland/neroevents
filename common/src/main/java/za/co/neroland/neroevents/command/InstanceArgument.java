package za.co.neroland.neroevents.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.EventInstance;

/**
 * The {@code <instance>} argument: an instance's 8-character short id (any unambiguous prefix), with
 * suggestions over live and recent instances. Ids are random; they name no player.
 */
final class InstanceArgument {

    static final String NAME = "instance";

    static final SuggestionProvider<CommandSourceStack> SUGGEST = (ctx, builder) -> {
        EventEngine engine = EventEngine.current();
        List<String> ids = new ArrayList<>();
        if (engine != null) {
            engine.live().forEach(i -> ids.add(i.shortId()));
            engine.recent().forEach(i -> ids.add(i.shortId()));
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private InstanceArgument() {
    }

    static StringArgumentType type() {
        return StringArgumentType.word();
    }

    /** The one instance the argument names, or empty after telling the invoker why. */
    static Optional<EventInstance> resolve(CommandContext<CommandSourceStack> ctx, EventEngine engine) {
        String prefix = StringArgumentType.getString(ctx, NAME);
        List<EventInstance> found = engine.matching(prefix);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.neroevents.instance.unknown", prefix));
            return Optional.empty();
        }
        if (found.size() > 1) {
            ctx.getSource().sendFailure(Component.translatable("command.neroevents.instance.ambiguous", prefix));
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }
}
