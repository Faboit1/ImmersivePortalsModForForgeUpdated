package qouteall.imm_ptl.core.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;


public class ImmPtlNetworkConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static record ModVersion(
        int major, int minor, int patch
    ) {
        // for dev env
        public static final ModVersion OTHER = new ModVersion(0, 0, 0);
        
        public static ModVersion read(FriendlyByteBuf buf) {
            int major = buf.readVarInt();
            int minor = buf.readVarInt();
            int patch = buf.readVarInt();
            return new ModVersion(major, minor, patch);
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(major);
            buf.writeVarInt(minor);
            buf.writeVarInt(patch);
        }
        
        @Override
        public String toString() {
            return "%d.%d.%d".formatted(major, minor, patch);
        }
        
        public boolean isNormalVersion() {
            return !OTHER.equals(this);
        }
        
        public boolean isCompatibleWith(ModVersion another) {
            return major == another.major && minor == another.minor;
        }
    }
    
    public static ModVersion immPtlVersion;
    
    /**
     * The version of the ImmPtl payload channels. NeoForge disconnects when both sides have the channels
     * with different versions. Only the major and minor version have to match.
     */
    public static String getNetworkVersion() {
        return immPtlVersion.major() + "." + immPtlVersion.minor();
    }
    
    public static void init(IEventBus modBus) {
        immPtlVersion = O_O.getImmPtlVersion();
        
        LOGGER.info("Immersive Portals Core version {}", immPtlVersion);
        
        modBus.addListener(RegisterPayloadHandlersEvent.class, IPPayloadRegistry::register);
        
        // the channels are optional, so check whether the client has ImmPtl
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (player.connection.hasChannel(PacketRedirection.Payload.TYPE)) {
                return;
            }
            if (IPConfig.getConfig().serverRejectClientWithoutImmPtl) {
                // cannot use translation key here
                // because the translation does not exist on client without the mod
                player.connection.disconnect(Component.literal(
                    """
                        The server detected that client does not install Immersive Portals mod.
                        A server with Immersive Portals mod only works with the clients that have it.
                        
                        (If the detection is wrong, the server can disable this check by changing `serverRejectClientWithoutImmPtl` to `false` in the server's config file `config/immersive_portals.json` and restart.)
                        """
                ));
            }
            else {
                LOGGER.warn("Accepting a client without Immersive Portals (serverRejectClientWithoutImmPtl is false) {}", player);
            }
        });
    }
    
    public static void initClient() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            if (event.getPlayer().connection.hasChannel(PacketRedirection.Payload.TYPE)) {
                // NeoForge rejects the connection when the channel versions differ
                serverVersion = immPtlVersion;
            }
            else {
                serverVersion = null;
            }
            onClientJoin();
        });
        
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> serverVersion = null);
    }
    
    private static void onClientJoin() {
        if (serverVersion == null) {
            warnServerMissingImmPtl();
        }
        else {
            if (serverVersion.isNormalVersion() &&
                immPtlVersion.isNormalVersion() &&
                !serverVersion.equals(immPtlVersion)
            ) {
                if (IPConfig.getConfig().shouldDisplayWarning("mod_version_mismatch")) {
                    MutableComponent text =
                        Component.translatable(
                            "imm_ptl.mod_patch_version_mismatch",
                            Component.literal(serverVersion.toString())
                                .withStyle(ChatFormatting.GOLD),
                            Component.literal(immPtlVersion.toString())
                                .withStyle(ChatFormatting.GOLD)
                        ).append(
                            IPMcHelper.getDisableWarningText("mod_version_mismatch")
                        );
                    CHelper.printChat(text);
                }
            }
        }
    }
    
    // used on client
    private static @Nullable ImmPtlNetworkConfig.ModVersion serverVersion = null;
    
    // should be called from client
    public static boolean doesServerHaveImmPtl() {
        return serverVersion != null;
    }
    
    private static void warnServerMissingImmPtl() {
        Minecraft.getInstance().execute(() -> {
            CHelper.printChat(Component.translatable("imm_ptl.server_missing_immptl"));
        });
    }
}
