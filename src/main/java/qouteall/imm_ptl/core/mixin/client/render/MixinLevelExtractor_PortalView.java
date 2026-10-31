package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
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
public class MixinLevelExtractor_PortalView {
    @Shadow
    private @Nullable ClientLevel level;
    
    @Shadow
    @Final
    private LevelRenderer levelRenderer;
    
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    
    // all block and light updates mark sections dirty through this method,
    // forward them to the extra level renderers that render the portal views
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void onSetSectionDirty(
        int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci
    ) {
        for (LevelExtractor extra : PortalViewRendering.getExtraExtractors((LevelExtractor) (Object) this)) {
            extra.setSectionDirty(sectionX, sectionY, sectionZ);
        }
    }
    
    // see MixinLevelRenderer_PortalView
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"
        )
    )
    private void wrapRepositionSectionUpdateTracker(
        SectionUpdateTracker tracker, SectionPos cameraSectionPos, Operation<Void> original
    ) {
        if (PortalViewRendering.isSharingRenderer()) {
            return;
        }
        original.call(tracker, cameraSectionPos);
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
