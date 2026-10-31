package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.PortalViewRendering;

/**
 * When a level renderer renders a second portal view in the same frame, it keeps the section grid
 * and the occlusion graph of the first view. Moving them would reset the sections every frame.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_PortalView {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;repositionCamera(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        )
    )
    private void wrapRepositionCamera(
        LevelRenderer levelRenderer, CameraRenderState camera, Operation<Void> original
    ) {
        if (PortalViewRendering.isSharingRenderer()) {
            return;
        }
        original.call(levelRenderer, camera);
    }
    
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"
        )
    )
    private void wrapOcclusionGraphUpdate(
        SectionOcclusionGraph graph, CameraRenderState camera, int fov,
        ChunkLoadingRenderState chunkLoading, Operation<Void> original
    ) {
        if (PortalViewRendering.isSharingRenderer()) {
            // the chunk updates have been taken from the tracking sets, they must not be lost
            graph.updateLoadedChunks(chunkLoading.addedLoadedChunks, chunkLoading.removedLoadedChunks);
            graph.updateEmptySections(chunkLoading.addedEmptySections, chunkLoading.removedEmptySections);
            return;
        }
        original.call(graph, camera, fov, chunkLoading);
    }
}
