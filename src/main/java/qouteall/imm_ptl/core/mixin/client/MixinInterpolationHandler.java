package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;

@Mixin(InterpolationHandler.class)
public abstract class MixinInterpolationHandler {
    @Shadow
    @Final
    private Entity entity;
    
    @Shadow
    public abstract void cancel();
    
    // avoid entity position interpolate when crossing portal to the same dimension
    @Inject(method = "interpolateTo", at = @At("RETURN"))
    private void onInterpolateTo(Vec3 position, float yRot, float xRot, CallbackInfo ci) {
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            entity.snapTo(position, yRot, xRot);
            cancel();
            return;
        }
        
        Portal collidingPortal = ((IEEntity) entity).ip_getCollidingPortal();
        if (collidingPortal != null) {
            if (entity.position().distanceToSqr(position) > 4) {
                McHelper.setPosAndLastTickPos(
                    entity,
                    position,
                    position.subtract(McHelper.getWorldVelocity(entity))
                );
                McHelper.updateBoundingBox(entity);
            }
        }
    }
}
