package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * A ring of 3 uniform buffers can only be used 3 times per frame: the 4th use would wait for
 * a buffer that is still used by the current frame, which throws.
 * Each portal view renders the level again, so the rings grow instead of waiting.
 */
@Mixin(MappableRingBuffer.class)
public class MixinMappableRingBuffer {
    @Shadow
    @Final
    @Mutable
    private GpuBuffer[] buffers;
    
    @Shadow
    @Final
    @Mutable
    private GpuFence[] fences;
    
    @Shadow
    @Final
    private int size;
    
    @Shadow
    private int current;
    
    @Unique
    private Supplier<String> ip_label;
    
    @Unique
    private int ip_usage;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Supplier<String> label, int usage, int size, CallbackInfo ci) {
        this.ip_label = label;
        this.ip_usage = usage;
    }
    
    @Inject(method = "currentBuffer", at = @At("HEAD"))
    private void onCurrentBuffer(CallbackInfoReturnable<GpuBuffer> cir) {
        GpuFence fence = fences[current];
        if (fence == null || fence.awaitCompletion(0L)) {
            return;
        }
        
        // the buffer may still be in use, insert a new buffer at the current position
        int count = buffers.length;
        int newIndex = count;
        GpuBuffer newBuffer = RenderSystem.getDevice().createBuffer(
            () -> ip_label.get() + " #" + newIndex, ip_usage, size
        );
        
        GpuBuffer[] newBuffers = new GpuBuffer[count + 1];
        GpuFence[] newFences = new GpuFence[count + 1];
        System.arraycopy(buffers, 0, newBuffers, 0, current);
        System.arraycopy(fences, 0, newFences, 0, current);
        newBuffers[current] = newBuffer;
        newFences[current] = null;
        System.arraycopy(buffers, current, newBuffers, current + 1, count - current);
        System.arraycopy(fences, current, newFences, current + 1, count - current);
        buffers = newBuffers;
        fences = newFences;
    }
    
    // the ring size is hardcoded as 3 in rotate() and close()
    @ModifyConstant(method = {"rotate", "close"}, constant = @Constant(intValue = 3))
    private int modifyBufferCount(int original) {
        return buffers.length;
    }
}
