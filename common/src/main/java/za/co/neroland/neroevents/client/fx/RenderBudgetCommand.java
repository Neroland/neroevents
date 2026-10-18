package za.co.neroland.neroevents.client.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Client-only debug command {@code /nsevrender}: prints the last {@value RenderBudget#WINDOW}-frame average of the
 * payload renderers (meteors, pods, debris, war beacons drawn and culled, their milliseconds, the frame time and
 * the payload particles spawned) to chat and to the log at DEBUG; {@code /nsevrender reset} clears it.
 *
 * <p>The root is deliberately not {@code /neroevents}: a client-dispatcher root that shares its name with a server
 * command shadows the server subtree. The bodies never read the command source, so the registration is generic
 * in the brigadier source type and one call serves all three loaders. Output holds counts and timings only.
 */
public final class RenderBudgetCommand {

    private RenderBudgetCommand() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("nsevrender")
                .executes(ctx -> print())
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
                    RenderBudget.reset();
                    say("NeroEvents renderer stats reset.");
                    return 1;
                })));
    }

    private static int print() {
        String record = RenderBudget.record();
        NeroEventsCommon.LOGGER.debug("[NeroEvents] {}", record);
        for (String line : record.split("\\R")) {
            say(line);
        }
        return 1;
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}
