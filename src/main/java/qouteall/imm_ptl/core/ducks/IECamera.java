package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public interface IECamera {
    void ip_resetState(Vec3 pos, ClientLevel currWorld);
    
    void portal_setPos(Vec3 pos);
    
    float ip_getCameraY();
    
    float ip_getLastCameraY();
    
    void ip_setCameraY(float cameraY, float lastCameraY);
    
    void portal_setFocusedEntity(Entity arg);
    
    /**
     * Sets up this camera as the camera looking through a portal.
     * The projection parameters are copied from the main camera.
     * @param detached whether the camera entity (the player) is rendered
     */
    void ip_setupPortalView(
        ClientLevel level, Entity entity, Vec3 pos, Quaternionf rotation, Camera mainCamera, boolean detached
    );
    
    void ip_tickPortalView();
}
