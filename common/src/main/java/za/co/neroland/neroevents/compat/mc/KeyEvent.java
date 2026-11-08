package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Before 1.21.9 Screen.keyPressed took
// (keyCode, scanCode, modifiers) instead of net.minecraft.client.input.KeyEvent; this record has the same
// accessors and key tests (same GLFW codes as vanilla's InputWithModifiers) so screen code is written once.
// The build rewrites `import net.minecraft.client.input.KeyEvent;` to this class on those lines.

//? if <1.21.9 {
/*public record KeyEvent(int key, int scancode, int modifiers) {

    public boolean isConfirmation() {
        return this.key == 257 || this.key == 335;
    }

    public boolean isSelection() {
        return this.key == 32 || this.key == 257 || this.key == 335;
    }

    public boolean isCycleFocus() {
        return this.key == 258;
    }

    public boolean isEscape() {
        return this.key == 256;
    }

    public boolean isLeft() {
        return this.key == 263;
    }

    public boolean isRight() {
        return this.key == 262;
    }

    public boolean isUp() {
        return this.key == 265;
    }

    public boolean isDown() {
        return this.key == 264;
    }

    public boolean hasShiftDown() {
        return (this.modifiers & 1) != 0;
    }
}
*///?}
