package qouteall.imm_ptl.core.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;

/**
 * The loading indicator shows its text as an overlay message, so it renders nothing in the world.
 */
public class LoadingIndicatorRenderer extends EntityRenderer<LoadingIndicatorEntity, EntityRenderState> {
    public LoadingIndicatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public @NotNull EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
