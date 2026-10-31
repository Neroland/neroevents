package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). The 26.x renderers read the frame's packed light
// from the render state (lightCoords, added in 1.21.9). 1.21.2 - 1.21.8 extend vanilla's state with that one
// field; 1.21.1 has no entity render states at all, so this carries the few fields the renderers read.

//? if <1.21.2 {
/*public class EntityRenderState {

    public double x;
    public double y;
    public double z;
    public float ageInTicks;
    public double distanceToCameraSq;
    public int lightCoords;
}
*///?} else if <1.21.9 {
/*public class EntityRenderState extends net.minecraft.client.renderer.entity.state.EntityRenderState {

    public int lightCoords;
}
*///?}
