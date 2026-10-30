package qouteall.imm_ptl.core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mixin.common.networking.MixinClientboundCustomPayloadPacket;
import qouteall.imm_ptl.core.mixin.common.networking.MixinServerboundCustomPayloadPacket;
import qouteall.q_misc_util.ImplRemoteProcedureCall;
import qouteall.q_misc_util.MiscNetworking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All Immersive Portals payloads.
 * <p>
 * The payload types are added directly into the vanilla custom payload codecs
 * ({@link MixinClientboundCustomPayloadPacket}, {@link MixinServerboundCustomPayloadPacket}),
 * the same way Fabric API does it. Forge's payload channels would cap clientbound payloads at 1 MiB,
 * which the redirected chunk packets can exceed, and they run handlers on the networking thread.
 * Here the handlers are queued into the vanilla packet processor so that they keep their order
 * relative to the vanilla packets.
 * <p>
 * Mod presence and version checking is done by Forge's channel negotiation, see {@link ImmPtlNetworkConfig}.
 */
public final class IPPayloadRegistry {
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

    /**
     * Used by the mixins into the vanilla custom payload codecs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <L extends List> L withClientboundTypes(L vanillaTypes) {
        List result = new ArrayList(vanillaTypes);
        for (S2CEntry<?> entry : S2C.values()) {
            result.add(new CustomPacketPayload.TypeAndCodec(entry.type(), entry.codec()));
        }
        return (L) result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <L extends List> L withServerboundTypes(L vanillaTypes) {
        List result = new ArrayList(vanillaTypes);
        for (C2SEntry<?> entry : C2S.values()) {
            result.add(new CustomPacketPayload.TypeAndCodec(entry.type(), entry.codec()));
        }
        return (L) result;
    }

    /**
     * Called when a serverbound custom payload packet is handled (initially on the networking thread).
     * @return whether the payload belongs to ImmPtl
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean handleServerbound(
        Packet<ServerCommonPacketListener> packet,
        CustomPacketPayload payload,
        ServerCommonPacketListener listener
    ) {
        C2SEntry entry = C2S.get(payload.type().id());
        if (entry == null) {
            return false;
        }

        if (!(listener instanceof ServerGamePacketListenerImpl gameListener)) {
            // ImmPtl payloads are only valid in the play phase
            return true;
        }

        var packetProcessor = gameListener.player.level().getServer().packetProcessor();
        if (!packetProcessor.isSameThread()) {
            // re-handled on the server thread in the same order as the vanilla packets
            packetProcessor.scheduleIfPossible(listener, packet);
            return true;
        }

        entry.handler().handle(payload, gameListener.player);
        return true;
    }

    /**
     * Turn a payload into a vanilla packet that can be sent to a client.
     */
    public static Packet<ClientGamePacketListener> createS2CPacket(CustomPacketPayload payload) {
        return PacketRedirection.asGamePacket(
            new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload)
        );
    }

    /**
     * Required for the static initializer to run before the vanilla codecs are built.
     */
    public static void init() {}

}
