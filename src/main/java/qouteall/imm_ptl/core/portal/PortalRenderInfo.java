package qouteall.imm_ptl.core.portal;

import de.nick1st.imm_ptl.events.ClientPortalTickEvent;
import de.nick1st.imm_ptl.events.PortalDisposeEvent;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.event.IPEventBus;

/**
 * A portal's rendering related things.
 * To access the package private field of Portal, this class is not in "render" package.
 * <p>
 * The occlusion-query based visibility prediction of the old OpenGL renderer is not used
 * with the Minecraft 26 renderer, the portals are culled by frustum.
 */
public class PortalRenderInfo {
    
    /**
     * The frame index in which the portal surface was last submitted for rendering.
     */
    public int lastRenderedFrameIndex = -1;
    
    public static void init() {
        IPEventBus.EVENT_BUS.addListener(ClientPortalTickEvent.class, event -> {
            Portal portal = event.portal;
            PortalRenderInfo presentation = getOptional(portal);
            if (presentation != null) {
                presentation.tick(portal);
            }
        });
        
        IPEventBus.EVENT_BUS.addListener(PortalDisposeEvent.class, event -> {
            Portal portal = event.portal;
            if (portal.level().isClientSide()) {
                PortalRenderInfo renderInfo = getOptional(portal);
                if (renderInfo != null) {
                    renderInfo.dispose();
                }
            }
        });
    }
    
    @Nullable
    public static PortalRenderInfo getOptional(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        
        return portal.portalRenderInfo;
    }
    
    public static PortalRenderInfo get(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        
        if (portal.portalRenderInfo == null) {
            portal.portalRenderInfo = new PortalRenderInfo();
        }
        return portal.portalRenderInfo;
    }
    
    private void tick(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
    }
    
    // disposing twice is fine
    public void dispose() {
    }
}
