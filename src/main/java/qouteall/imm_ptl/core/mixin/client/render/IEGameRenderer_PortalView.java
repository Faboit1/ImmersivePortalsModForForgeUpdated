package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface IEGameRenderer_PortalView {
    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor ip_getLightmapRenderStateExtractor();
    
    @Accessor("resourcePool")
    CrossFrameResourcePool ip_getResourcePool();
    
    @Invoker("bobHurt")
    void ip_bobHurt(CameraRenderState cameraState, PoseStack poseStack);
    
    @Invoker("bobView")
    void ip_bobView(CameraRenderState cameraState, PoseStack poseStack);
}
