package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.5 only (empty on every other line). 1.21.6 added Commands.hasPermission(level) as a
// reusable predicate; before it a command source was asked directly. The build rewrites
// Commands.hasPermission(..) to this helper on those lines, with the same permission levels.

//? if <1.21.6 {
/*import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;

public final class PermissionCompat {

    private PermissionCompat() {
    }

    public static Predicate<CommandSourceStack> level(int level) {
        return source -> source.hasPermission(level);
    }
}
*///?}
