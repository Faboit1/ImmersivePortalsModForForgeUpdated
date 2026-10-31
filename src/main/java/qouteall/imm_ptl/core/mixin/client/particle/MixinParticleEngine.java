package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEParticleManager;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow
    protected ClientLevel level;
    
    // ParticleEngine#setLevel clears the particles, so set the field directly when switching world
    @Override
    public void ip_setWorld(ClientLevel world_) {
        level = world_;
    }
}
