package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IECamera;

@Mixin(Camera.class)
public abstract class MixinCamera implements IECamera {
    @Shadow
    @Final
    private static Vector3fc FORWARDS;
    @Shadow
    @Final
    private static Vector3fc UP;
    @Shadow
    @Final
    private static Vector3fc LEFT;
    
    @Shadow
    private boolean initialized;
    @Shadow
    private @Nullable Level level;
    @Shadow
    private @Nullable Entity entity;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    private boolean detached;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;
    @Shadow
    @Final
    private Matrix4f cachedViewRotMatrix;
    @Shadow
    private int matrixPropertiesDirty;
    @Shadow
    private float fov;
    @Shadow
    private float hudFov;
    @Shadow
    private float depthFar;
    @Shadow
    @Final
    private EnvironmentAttributeProbe attributeProbe;
    
    @Shadow
    protected abstract void setPosition(Vec3 position);
    
    @Shadow
    public abstract Matrix4f getViewRotationMatrix(Matrix4f dest);
    
    @Shadow
    protected abstract void prepareCullFrustum(
        Matrix4fc modelViewMatrix, Matrix4f projectionMatrixForCulling, Vec3 cameraPos
    );
    
    @Shadow
    protected abstract Matrix4f createProjectionMatrixForCulling();
    
    @Shadow
    protected abstract void setupPerspective(float zNear, float zFar, float fov, float width, float height);
    
    @Unique
    private boolean ip_isPortalCamera = false;
    
    // the view through a portal should not use the fluid fog of the virtual camera position
    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void onGetFluidInCamera(CallbackInfoReturnable<FogType> cir) {
        if (ip_isPortalCamera) {
            cir.setReturnValue(FogType.NONE);
        }
    }
    
    @Override
    public void ip_resetState(Vec3 pos, ClientLevel currWorld) {
        setPosition(pos);
        level = currWorld;
    }
    
    @Override
    public float ip_getCameraY() {
        return eyeHeight;
    }
    
    @Override
    public float ip_getLastCameraY() {
        return eyeHeightOld;
    }
    
    @Override
    public void ip_setCameraY(float cameraY_, float lastCameraY_) {
        eyeHeight = cameraY_;
        eyeHeightOld = lastCameraY_;
    }
    
    @Override
    public void portal_setPos(Vec3 pos) {
        setPosition(pos);
    }
    
    @Override
    public void portal_setFocusedEntity(Entity arg) {
        entity = arg;
    }
    
    @Override
    public void ip_setupPortalView(
        ClientLevel level, Entity entity, Vec3 pos, Quaternionf rotation, Camera mainCamera, boolean detached
    ) {
        MixinCamera main = (MixinCamera) (Object) mainCamera;
        
        boolean levelChanged = this.level != level;
        ip_isPortalCamera = true;
        this.level = level;
        this.entity = entity;
        // a detached camera renders its entity, so the player is visible in the portal view (and mirrors)
        this.detached = detached;
        setPosition(pos);
        
        this.rotation.set(rotation);
        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
        // the inverse of Camera.setRotation
        this.yRot = (float) Math.toDegrees(Math.atan2(-this.forwards.x, this.forwards.z));
        this.xRot = (float) Math.toDegrees(-Math.asin(Math.clamp(this.forwards.y, -1.0f, 1.0f)));
        this.matrixPropertiesDirty |= 3;
        
        this.eyeHeight = main.eyeHeight;
        this.eyeHeightOld = main.eyeHeightOld;
        this.fov = main.fov;
        this.hudFov = main.hudFov;
        this.depthFar = main.depthFar;
        
        prepareCullFrustum(
            getViewRotationMatrix(this.cachedViewRotMatrix), createProjectionMatrixForCulling(), this.position
        );
        Minecraft client = Minecraft.getInstance();
        setupPerspective(
            0.05F, this.depthFar, this.fov,
            client.getWindow().getWidth(), client.getWindow().getHeight()
        );
        this.initialized = true;
        
        // the environment attributes (sky color, fog color etc.) are sampled when ticking
        if (levelChanged) {
            attributeProbe.reset();
            attributeProbe.tick(level, position);
        }
    }
    
    @Override
    public void ip_tickPortalView() {
        if (level != null) {
            attributeProbe.tick(level, position);
        }
    }
}
