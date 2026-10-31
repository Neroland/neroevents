package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Before 1.21.9 Screen.mouseClicked took
// (mouseX, mouseY, button) instead of net.minecraft.client.input.MouseButtonEvent; this record has the same
// accessors so screen code is written once. The build rewrites
// `import net.minecraft.client.input.MouseButtonEvent;` to this class on those lines.

//? if <1.21.9 {
/*public record MouseButtonEvent(double x, double y, int button) {
}
*///?}
