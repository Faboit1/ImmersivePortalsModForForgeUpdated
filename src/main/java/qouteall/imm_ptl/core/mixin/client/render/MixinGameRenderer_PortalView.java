package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.PortalViewRendering;

@Mixin(GameRenderer.class)
public class MixinGameRenderer_PortalView {
    @Shadow
    @Final
    private Minecraft minecraft;
    
    // the views through portals are extracted and rendered before the main view is extracted,
    // because the extracted level render state is shared
    @Inject(method = "extract", at = @At("HEAD"))
    private void onExtract(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (minecraft.isGameLoadFinished() && advanceGameTime && minecraft.level != null) {
            PortalViewRendering.renderPortalViews(deltaTracker);
        }
    }
}
