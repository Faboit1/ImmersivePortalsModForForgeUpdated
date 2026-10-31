package qouteall.imm_ptl.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.q_misc_util.ImplRemoteProcedureCall;
import qouteall.q_misc_util.MiscNetworking;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All Immersive Portals payloads. They are registered as NeoForge payload channels.
 * <p>
 * The handlers run on the main thread through NeoForge, which queues them into the vanilla packet processor,
 * so they keep their order relative to the vanilla packets. NeoForge also splits the payloads larger than
 * the vanilla limit (the redirected chunk packets can be).
 * <p>
 * The channels are optional, so a client with ImmPtl can join a server without it.
 * See {@link ImmPtlNetworkConfig} for the checks of the remote side.
 */
public final class IPPayloadRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @FunctionalInterface
    public interface ClientHandler<T> {
        void handle(T payload, ClientGamePacketListener listener);
    }

    @FunctionalInterface
    public interface ServerHandler<T> {
        void handle(T payload, ServerPlayer player);
    }

    public record S2CEntry<T extends CustomPacketPayload>(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        boolean handleOnNetworkThread,
        ClientHandler<T> handler
    ) {}

    public record C2SEntry<T extends CustomPacketPayload>(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        ServerHandler<T> handler
    ) {}

    private static final Map<Identifier, S2CEntry<?>> S2C = new LinkedHashMap<>();
    private static final Map<Identifier, C2SEntry<?>> C2S = new LinkedHashMap<>();

    static {
        // server to client
        s2c(
            MiscNetworking.DimIdSyncPacket.TYPE, MiscNetworking.DimIdSyncPacket.CODEC, true,
            (p, listener) -> p.handleOnNetworkingThread()
        );
        s2c(
            ImplRemoteProcedureCall.S2CRPCPayload.TYPE, ImplRemoteProcedureCall.S2CRPCPayload.CODEC, false,
            (p, listener) -> p.handle()
        );
        s2c(
            ImmPtlNetworking.GlobalPortalSyncPacket.TYPE, ImmPtlNetworking.GlobalPortalSyncPacket.CODEC, false,
            (p, listener) -> p.handle()
        );
        s2c(
            ImmPtlNetworking.PortalSyncPacket.TYPE, ImmPtlNetworking.PortalSyncPacket.CODEC, false,
            (p, listener) -> p.handle()
        );
        s2c(
            PacketRedirection.Payload.TYPE, PacketRedirection.Payload.CODEC, false,
            PacketRedirection.Payload::handle
        );

        // client to server
        c2s(
            ImplRemoteProcedureCall.C2SRPCPayload.TYPE, ImplRemoteProcedureCall.C2SRPCPayload.CODEC,
            ImplRemoteProcedureCall.C2SRPCPayload::handle
        );
        c2s(
            ImmPtlNetworking.TeleportPacket.TYPE, ImmPtlNetworking.TeleportPacket.CODEC,
            ImmPtlNetworking.TeleportPacket::handle
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends CustomPacketPayload> void s2c(
        CustomPacketPayload.Type<T> type, StreamCodec<?, T> codec,
        boolean handleOnNetworkThread, ClientHandler<T> handler
    ) {
        S2C.put(type.id(), new S2CEntry<>(type, (StreamCodec) codec, handleOnNetworkThread, handler));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends CustomPacketPayload> void c2s(
        CustomPacketPayload.Type<T> type, StreamCodec<?, T> codec, ServerHandler<T> handler
    ) {
        C2S.put(type.id(), new C2SEntry<>(type, (StreamCodec) codec, handler));
    }

    @Nullable
    public static S2CEntry<?> getS2C(Identifier id) {
        return S2C.get(id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ImmPtlNetworkConfig.getNetworkVersion()).optional();
        PayloadRegistrar networkThreadRegistrar = registrar.executesOn(HandlerThread.NETWORK);
        
        for (S2CEntry entry : S2C.values()) {
            ClientHandler handler = entry.handler();
            (entry.handleOnNetworkThread() ? networkThreadRegistrar : registrar).playToClient(
                entry.type(), entry.codec(),
                (payload, context) -> handler.handle(payload, (ClientGamePacketListener) context.listener())
            );
        }
        
        for (C2SEntry entry : C2S.values()) {
            ServerHandler handler = entry.handler();
            registrar.playToServer(
                entry.type(), entry.codec(),
                (payload, context) -> handler.handle(payload, (ServerPlayer) context.player())
            );
        }
    }
    
    /**
     * Turn a payload into a vanilla packet that can be sent to a client.
     */
    public static Packet<ClientGamePacketListener> createS2CPacket(CustomPacketPayload payload) {
        return PacketRedirection.asGamePacket(
            new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload)
        );
    }


}
