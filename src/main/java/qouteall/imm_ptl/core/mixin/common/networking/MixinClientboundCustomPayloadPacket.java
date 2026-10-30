package qouteall.imm_ptl.core.mixin.common.networking;

import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.network.IPPayloadRegistry;
import qouteall.imm_ptl.core.network.IPPayloadRegistryClient;

import java.util.List;

@Mixin(ClientboundCustomPayloadPacket.class)
public class MixinClientboundCustomPayloadPacket {
    
    @Shadow
    @Final
    private CustomPacketPayload payload;
    
    // add ImmPtl payload types into the gameplay codec (the first codec in the static initializer)
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
        return IPPayloadRegistry.withClientboundTypes(types);
    }
    
    // this runs before Forge's custom payload event
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
        method = "handle(Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHandle(ClientCommonPacketListener listener, CallbackInfo ci) {
        if (IPPayloadRegistryClient.handleClientbound(
            (net.minecraft.network.protocol.Packet) (Object) this, payload, listener
        )) {
            ci.cancel();
        }
    }
}
