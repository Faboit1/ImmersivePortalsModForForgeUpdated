package qouteall.q_misc_util.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.CustomTextOverlay;

@Mixin(Hud.class)
public abstract class MixinGui_Overlay {
    @Shadow
    public abstract boolean isHidden();
    
    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void onExtractRenderState(
        GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        if (!isHidden()) {
            CustomTextOverlay.render(graphics);
        }
    }
}
