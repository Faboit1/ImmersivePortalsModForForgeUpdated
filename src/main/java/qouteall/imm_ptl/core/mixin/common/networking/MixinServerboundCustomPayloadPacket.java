package qouteall.imm_ptl.core.mixin.common.networking;

import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.network.IPPayloadRegistry;

import java.util.List;

@Mixin(ServerboundCustomPayloadPacket.class)
public class MixinServerboundCustomPayloadPacket {
    
    @Shadow
    @Final
    private CustomPacketPayload payload;
    
    @ModifyArg(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            ordinal = 0
        ),
        index = 1
    )
    private static List<?> ip_addPayloadTypes(List<?> types) {
        return IPPayloadRegistry.withServerboundTypes(types);
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
        method = "handle(Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHandle(ServerCommonPacketListener listener, CallbackInfo ci) {
        if (IPPayloadRegistry.handleServerbound(
            (net.minecraft.network.protocol.Packet) (Object) this, payload, listener
        )) {
            ci.cancel();
        }
    }
}
