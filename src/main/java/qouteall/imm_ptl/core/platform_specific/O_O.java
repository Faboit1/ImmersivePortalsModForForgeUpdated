package qouteall.imm_ptl.core.platform_specific;

import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class O_O {
    public static boolean isDimensionalThreadingPresent = false;
    
    public static boolean isForge() {
        return true;
    }
    
    //@OnlyIn(Dist.CLIENT)
    public static void onPlayerChangeDimensionClient(
        ResourceKey<Level> from, ResourceKey<Level> to
    ) {
    }
    
    public static void onPlayerTravelOnServer(
        ServerPlayer player,
        ServerLevel fromWorld, ServerLevel toWorld
    ) {
    }
    
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }
    
    private static final BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();
    
    public static boolean isObsidian(BlockState blockState) {
        return blockState == obsidianState;
    }
    
    public static void postClientChunkLoadEvent(LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
//        ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(
//            ((ClientLevel) chunk.getLevel()), chunk
//        );
    }
    
    public static void postClientChunkUnloadEvent(LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
//        ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(
//            ((ClientLevel) chunk.getLevel()), chunk
//        );
    }
    
    public static boolean isDedicatedServer() {
        return FMLEnvironment.getDist() == Dist.DEDICATED_SERVER;
    }
    
    public static void postPortalSpawnEventForge(PortalGenInfo info) {
    
    }

    // @Nick1st - Moved to client class
//    @OnlyIn(Dist.CLIENT)
//    public static ClientChunkCache createMyClientChunkManager(ClientLevel world, int loadDistance) {
//        return new ImmPtlClientChunkMap(world, loadDistance);
//    }
    
    public static boolean getIsPehkuiPresent() {
        return ModList.get().isLoaded("pehkui");
    }
    
    @Nullable
    public static String getImmPtlModInfoUrl() {
        // upstream does not publish version info for this port,
        // fetching it would only produce 404 errors
        return null;
    }
    
    public static boolean isModLoadedWithinVersion(String modId, @Nullable String startVersion, @Nullable String endVersion) {
        Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(modId);
        if (modContainer.isPresent()) {
            ArtifactVersion version = modContainer.get().getModInfo().getVersion();
            
            try {
                if (startVersion != null) {
                    int i = new DefaultArtifactVersion(startVersion).compareTo(version);
                    if (i > 0) {
                        return false;
                    }
                }
                
                if (endVersion != null) {
                    int i = new DefaultArtifactVersion(endVersion).compareTo(version);
                    if (i < 0) {
                        return false;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
            return true;
            
        }
        else {
            return false;
        }
    }
    
    private static Optional<ArtifactVersion> getOwnVersion() {
        return ModList.get().getModContainerById(IPModEntry.MODID)
            .map(c -> c.getModInfo().getVersion());
    }
    
    public static @NotNull ImmPtlNetworkConfig.ModVersion getImmPtlVersion() {
        ArtifactVersion version = getOwnVersion().orElse(null);
        if (version == null || version.getMajorVersion() == 0 && version.getMinorVersion() == 0) {
            // in dev env the version may not be filled
            return ImmPtlNetworkConfig.ModVersion.OTHER;
        }
        return new ImmPtlNetworkConfig.ModVersion(
            version.getMajorVersion(), version.getMinorVersion(), version.getIncrementalVersion()
        );
    }
    
    public static String getImmPtlVersionStr() {
        return getOwnVersion().map(Object::toString).orElse("");
    }
    
    public static boolean shouldUpdateImmPtl(String latestReleaseVersion) {
        if (isDevEnv()) {
            return false;
        }
        ArtifactVersion current = getOwnVersion().orElse(null);
        if (current == null) {
            return false;
        }
        try {
            return new DefaultArtifactVersion(latestReleaseVersion).compareTo(current) > 0;
        }
        catch (Exception e) {
            Helper.LOGGER.error("Failed to parse version {}", latestReleaseVersion, e);
            return false;
        }
    }
    
    public static String getModDownloadLink() {
        return "https://modrinth.com/mod/immersiveportals";
    }
    
    public static String getIssueLink() {
        return "https://github.com/iPortalTeam/ImmersivePortalsMod/discussions";
    }
    
    @Nullable
    public static Identifier getModIconLocation(String modid) {
        // TODO @Nick1st - Fix this
        return null;
//        String path = FabricLoader.getInstance().getModContainer(modid)
//            .flatMap(c -> c.getMetadata().getIconPath(512))
//            .orElse(null);
//        if (path == null) {
//            return null;
//        }
//
//        // for example, if the icon path is "assets/modid/icon.png"
//        // then the result should be modid:icon.png
//
//        if (path.startsWith("/")) {
//            path = path.substring(1);
//        }
//        if (path.startsWith("assets")) {
//            path = path.substring("assets".length());
//        }
//        if (path.startsWith("/")) {
//            path = path.substring(1);
//        }
//        String[] parts = path.split("/");
//        if (parts.length != 2) {
//            return null;
//        }
//        return McHelper.newIdentifier(parts[0], parts[1]);
    }
    
    @Nullable
    public static String getModName(String modid) {
        return ModList.get().getModContainerById(modid)
            .map(c -> c.getModInfo().getDisplayName())
            .orElse(null);
    }
    
    // most quilt installations use quilted fabric api
    public static boolean isQuilt() {
        return false;
        //return FabricLoader.getInstance().isModLoaded("quilted_fabric_api");
    }
    
    public static List<String> getLoadedModIds() {
        return ModList.get().getMods().stream().map(net.neoforged.neoforgespi.language.IModInfo::getModId).sorted().toList();
    }

    public static boolean allowTeleportingEntity(Entity entity, Portal portal) {
        // CommonHooks.onTravelToDimension() on NeoForge
        return true;
    }
    
    public static boolean isDevEnv() {
        return !FMLEnvironment.isProduction();
    }
}
