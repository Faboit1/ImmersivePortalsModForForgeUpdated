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


@Mixin(ClientboundCustomPayloadPacket.class)
public class MixinClientboundCustomPayloadPacket {
    
    @Shadow
    @Final
    private CustomPacketPayload payload;
    
    // resolve ImmPtl payload types in the gameplay codec (the first codec in the static initializer).
    // the fallback is queried when a packet is encoded/decoded, so it does not depend on class init order
    @ModifyArg(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            ordinal = 0
        ),
        index = 0
    )
    private static CustomPacketPayload.FallbackProvider<?> ip_wrapFallback(
        CustomPacketPayload.FallbackProvider<?> fallback
    ) {
        return IPPayloadRegistry.wrapClientboundFallback(fallback);
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
