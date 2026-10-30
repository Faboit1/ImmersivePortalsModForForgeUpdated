package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Submits the portal surface. The view through the portal has been rendered into an
 * off-screen target by {@link PortalViewRendering} before the level is rendered.
 */
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.PortalRenderState> {
    
    public static class PortalRenderState extends EntityRenderState {
        public @Nullable Portal portal;
    }
    
    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public @NotNull PortalRenderState createRenderState() {
        return new PortalRenderState();
    }
    
    @Override
    public void extractRenderState(Portal portal, PortalRenderState state, float partialTicks) {
        super.extractRenderState(portal, state, partialTicks);
        state.portal = portal;
    }
    
    @Override
    public boolean shouldRender(Portal portal, Frustum frustum, double camX, double camY, double camZ) {
        return PortalViewRendering.shouldRenderPortalEntity(portal, frustum);
    }
    
    @Override
    public void submit(
        PortalRenderState state, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (state.portal != null) {
            PortalViewRendering.submitPortalSurface(state.portal, poseStack, submitNodeCollector, camera);
        }
    }
}
