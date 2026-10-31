package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). 1.21.9 replaced immediate-mode block-entity and
// entity rendering with render states + a SubmitNodeCollector. These look-alikes (same simple names, swapped in
// by an import rewrite in stonecutter.gradle on those lines) let the 26.x renderers compile and run unchanged:
// each frame the old render(..) call builds the state, then runs submit(..) straight into the buffer source.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

public final class ModelFeatureRenderer {

    private ModelFeatureRenderer() {
    }

    public record CrumblingOverlay(int progress, PoseStack.Pose cameraPose) {
    }
}
*///?}
