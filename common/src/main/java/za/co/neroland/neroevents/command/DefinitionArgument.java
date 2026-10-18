package za.co.neroland.neroevents.command;

import java.util.Optional;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventDefinitions;

/** The {@code <definition>} argument: an identifier with suggestions over loaded definition ids only. */
final class DefinitionArgument {

    static final String NAME = "definition";

    static final SuggestionProvider<CommandSourceStack> SUGGEST = (ctx, builder) -> {
        if (ctx.getSource().getServer() == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggestResource(
                EventDefinitions.definitionsForServer(ctx.getSource().getServer()).keySet(), builder);
    };

    private DefinitionArgument() {
    }

    static IdentifierArgument type() {
        return IdentifierArgument.id();
    }

    /** The definition named by the argument, or empty after telling the invoker it is unknown. */
    static Optional<EventDefinition> resolve(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, NAME);
        EventDefinition def = EventDefinitions.definitionsForServer(ctx.getSource().getServer()).get(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.translatable("command.neroevents.definition.unknown", id.toString()));
            return Optional.empty();
        }
        return Optional.of(def);
    }
}
