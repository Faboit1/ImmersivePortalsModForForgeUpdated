package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Renders the views through the portals (Minecraft 26 renderer).
 */
public class PortalViewRendering {
    public static void init() {
    }
    
    public static boolean shouldRenderPortalEntity(Portal portal, Frustum frustum) {
        return frustum.isVisible(portal.getBoundingBox());
    }
    
    public static void submitPortalSurface(
        Portal portal, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
    }
}
