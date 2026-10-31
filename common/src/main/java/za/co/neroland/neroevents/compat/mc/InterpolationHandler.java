package za.co.neroland.neroevents.compat.mc;

// Minecraft 1.21.1 - 1.21.4 only (empty on every other line). 1.21.5 added Entity.getInterpolation() and the
// vanilla InterpolationHandler; before it an entity smoothed client movement itself from lerpTo(..). This
// look-alike (swapped in by an import rewrite in stonecutter.gradle) does the same linear stepping, and the
// entities feed it from lerpTo(..) in a //? block.

//? if <1.21.5 {
/*import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public final class InterpolationHandler {

    private final Entity entity;
    private int steps;
    private double x;
    private double y;
    private double z;
    private float yRot;
    private float xRot;

    public InterpolationHandler(Entity entity) {
        this.entity = entity;
    }

    public void interpolateTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.steps = Math.max(1, steps);
    }

    public boolean hasActiveInterpolation() {
        return this.steps > 0;
    }

    public void interpolate() {
        if (this.steps <= 0) {
            return;
        }
        double f = 1.0 / this.steps;
        this.entity.setPos(Mth.lerp(f, this.entity.getX(), this.x), Mth.lerp(f, this.entity.getY(), this.y),
                Mth.lerp(f, this.entity.getZ(), this.z));
        this.entity.setYRot(this.entity.getYRot() + (float) (Mth.wrapDegrees(this.yRot - this.entity.getYRot()) * f));
        this.entity.setXRot(this.entity.getXRot() + (float) ((this.xRot - this.entity.getXRot()) * f));
        this.steps--;
    }
}
*///?}
