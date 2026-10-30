package qouteall.imm_ptl.core.network;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
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
     * Forge negotiates the channel list and versions during login.
     * The channel carries no packets (see {@link IPPayloadRegistry}),
     * it's only used for detecting whether the remote side has ImmPtl and whether the versions match.
     */
    public static final Identifier HANDSHAKE_CHANNEL_ID =
        Identifier.fromNamespaceAndPath("immersive_portals", "handshake");
    
    private static Channel<?> handshakeChannel;
    
    private static int getProtocolVersion(ModVersion version) {
        // only major and minor version have to match
        return version.major() * 1000 + version.minor();
    }
    
    public static void init() {
        immPtlVersion = O_O.getImmPtlVersion();
        
        LOGGER.info("Immersive Portals Core version {}", immPtlVersion);
        
        int protocolVersion = getProtocolVersion(immPtlVersion);
        
        handshakeChannel = ChannelBuilder.named(HANDSHAKE_CHANNEL_ID)
            .networkProtocolVersion(protocolVersion)
            // checked on the client, with the server's version
            .clientAcceptedVersions((status, remoteVersion) -> {
                if (status != Channel.VersionTest.Status.PRESENT) {
                    // allow joining a server without ImmPtl, a warning will be shown
                    return true;
                }
                if (remoteVersion == protocolVersion) {
                    return true;
                }
                return IPConfig.getConfig().clientTolerantVersionMismatchWithServer;
            })
            // checked on the server, with the client's version
            .serverAcceptedVersions((status, remoteVersion) -> {
                if (status != Channel.VersionTest.Status.PRESENT) {
                    if (IPConfig.getConfig().serverRejectClientWithoutImmPtl) {
                        return false;
                    }
                    LOGGER.warn("Accepting a client without Immersive Portals (serverRejectClientWithoutImmPtl is false)");
                    return true;
                }
                if (remoteVersion == protocolVersion) {
                    return true;
                }
                LOGGER.info(
                    "Client ImmPtl protocol version {} differs from server {} (only patch version difference is tolerated)",
                    remoteVersion, protocolVersion
                );
                return IPConfig.getConfig().serverTolerantVersionMismatchWithClient;
            })
            .eventNetworkChannel();
        
        IPPayloadRegistry.init();
    }
    
    //@OnlyIn(Dist.CLIENT)
    public static void initClient() {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((event) -> {
            if (handshakeChannel != null && handshakeChannel.isRemotePresent(event.getConnection())) {
                // the version is checked by Forge, mismatch beyond the patch version is rejected
                serverVersion = immPtlVersion;
            }
            else {
                serverVersion = null;
            }
            onClientJoin();
        });
        
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((event) -> serverVersion = null);
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
