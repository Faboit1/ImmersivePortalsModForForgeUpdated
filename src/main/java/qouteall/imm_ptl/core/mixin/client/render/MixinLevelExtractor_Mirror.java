package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.PortalViewRendering;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor_Mirror {
    @Shadow
    private @Nullable ClientLevel level;
    
    @Shadow
    @Final
    private LevelRenderer levelRenderer;
    
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    
    // all block and light updates mark sections dirty through this method,
    // forward them to the level renderer that renders the same-dimension portal views
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void onSetSectionDirty(
        int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci
    ) {
        LevelExtractor mirror = PortalViewRendering.getMirrorExtractor((LevelExtractor) (Object) this);
        if (mirror != null) {
            mirror.setSectionDirty(sectionX, sectionY, sectionZ);
        }
    }
    
    // the global portals are not in the level's entity list, extract them for drawing their surfaces
    @Inject(method = "extract", at = @At("RETURN"))
    private void onExtractFinished(DeltaTracker deltaTracker, Camera camera, float deltaPartialTick, CallbackInfo ci) {
        if (level != null) {
            PortalViewRendering.extractGlobalPortals(
                level, levelRenderer.entityRenderDispatcher(), levelRenderState, deltaPartialTick
            );
        }
    }
}
