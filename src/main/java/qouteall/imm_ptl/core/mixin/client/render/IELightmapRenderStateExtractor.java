package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface IELightmapRenderStateExtractor {
    @Accessor("needsUpdate")
    boolean ip_getNeedsUpdate();
    
    @Accessor("needsUpdate")
    void ip_setNeedsUpdate(boolean needsUpdate);
}
