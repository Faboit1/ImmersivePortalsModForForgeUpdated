package qouteall.imm_ptl.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public final class IPPayloadRegistryClient {
    /**
     * Called when a clientbound custom payload packet is handled (initially on the networking thread).
     * @return whether the payload belongs to ImmPtl
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean handleClientbound(
        Packet<ClientCommonPacketListener> packet,
        CustomPacketPayload payload,
        ClientCommonPacketListener listener
    ) {
        IPPayloadRegistry.S2CEntry entry = IPPayloadRegistry.getS2C(payload.type().id());
        if (entry == null) {
            return false;
        }
        
        if (!(listener instanceof ClientGamePacketListener gameListener)) {
            return true;
        }
        
        if (!entry.handleOnNetworkThread()) {
            var packetProcessor = Minecraft.getInstance().packetProcessor();
            if (!packetProcessor.isSameThread()) {
                // re-handled on the client thread, in the same order as vanilla packets
                packetProcessor.scheduleIfPossible(listener, packet);
                return true;
            }
        }
        
        entry.handler().handle(payload, gameListener);
        return true;
    }
}
