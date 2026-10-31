package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin(ServerboundMovePlayerPacket.PosRot.class)
public class MixinServerboundMovePlayerPacketPosRot {
    @Inject(method = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$PosRot;read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$PosRot;", at = @At("RETURN"), cancellable = true)
    private static void onRead(
        FriendlyByteBuf buf, CallbackInfoReturnable<ServerboundMovePlayerPacket.PosRot> cir
    ) {
        // a client without ImmPtl does not send the dimension
        if (!buf.isReadable()) {
            return;
        }
        ResourceKey<Level> playerDim = buf.readResourceKey(Registries.DIMENSION);
        ((IEPlayerMoveC2SPacket) cir.getReturnValue()).ip_setPlayerDimension(playerDim);
    }
}
