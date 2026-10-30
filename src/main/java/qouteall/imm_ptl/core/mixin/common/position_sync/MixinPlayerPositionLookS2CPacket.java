package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;

@Mixin(ClientboundPlayerPositionPacket.class)
public class MixinPlayerPositionLookS2CPacket implements IEPlayerPositionLookS2CPacket {
    @Mutable
    @Shadow
    @Final
    public static StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> STREAM_CODEC;
    
    @Unique
    private ResourceKey<Level> ip_playerDimension;
    
    @Override
    public ResourceKey<Level> ip_getPlayerDimension() {
        return ip_playerDimension;
    }
    
    @Override
    public void ip_setPlayerDimension(ResourceKey<Level> dimension) {
        ip_playerDimension = dimension;
    }
    
    /**
     * Append the player dimension after the vanilla fields.
     * The packet frame only contains this packet, so the decoder knows whether
     * the dimension is present by checking the remaining bytes.
     * (In singleplayer the packet object is passed directly, so the field is kept.)
     */
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void ip_wrapCodec(CallbackInfo ci) {
        StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> vanillaCodec = STREAM_CODEC;
        STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                vanillaCodec.encode(buf, packet);
                ResourceKey<Level> dim =
                    ((IEPlayerPositionLookS2CPacket) (Object) packet).ip_getPlayerDimension();
                if (dim != null) {
                    buf.writeResourceKey(dim);
                }
            },
            buf -> {
                ClientboundPlayerPositionPacket packet = vanillaCodec.decode(buf);
                if (buf.isReadable()) {
                    ((IEPlayerPositionLookS2CPacket) (Object) packet).ip_setPlayerDimension(
                        buf.readResourceKey(Registries.DIMENSION)
                    );
                }
                return packet;
            }
        );
    }
}
