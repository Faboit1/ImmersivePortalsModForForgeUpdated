package qouteall.imm_ptl.core.platform_specific;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.peripheral.platform_specific.IPFeatureControl;
import qouteall.q_misc_util.Helper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/**
 * Stored as config/immersive_portals.json (the same format the Cloth Config based versions used).
 */
public class IPConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static IPConfig instance;

    // json does not allow comments...

    public String check_the_wiki_for_more_information = "https://qouteall.fun/immptl/wiki/Config-Options";
    
    // client visible configs

    public int maxPortalLayer = 5;

    public boolean lagAttackProof = true;

    public boolean enableCrossPortalSound = true;

    public boolean pureMirror = false;

    public boolean renderYourselfInPortal = true;

    public boolean correctCrossPortalEntityRendering = true;

    public boolean reducedPortalRendering = false;

    public boolean netherPortalOverlay = false;

    public boolean enableNetherPortalEffect = true;

    public boolean enableClientPerformanceAdjustment = true;

    public boolean clientTolerantVersionMismatchWithServer = false;

    public boolean compatibilityRenderMode = false;
    
    // client invisible configs

    public boolean checkModInfoFromInternet = true;

    public boolean enableUpdateNotification = true;

    public boolean sharedBlockMeshBufferOptimization = true;

    public boolean enableClippingMechanism = true;

    public boolean visibilityPrediction = true;

    public boolean useDepthClampForPortalRendering = true;

    public boolean enableCrossPortalView = true;

    public int portalRenderLimit = 200;

    public boolean doCheckGlError = false;

    public boolean shaderpackWarning = true;

    public int portalWandCursorAlignment = 2; // zero for no align

    public boolean saveMemoryInBufferPack = false;

    public boolean initialScreenShown = false;
    
    // common visible configs

    public IPGlobal.NetherPortalMode netherPortalMode =
        IPFeatureControl.enableVanillaBehaviorChangingByDefault() ?
            IPGlobal.NetherPortalMode.adaptive : IPGlobal.NetherPortalMode.vanilla;

    public IPGlobal.EndPortalMode endPortalMode =
        IPFeatureControl.enableVanillaBehaviorChangingByDefault() ?
            IPGlobal.EndPortalMode.normal : IPGlobal.EndPortalMode.vanilla;
    
    public boolean enableMirrorCreation =
        IPFeatureControl.enableVanillaBehaviorChangingByDefault();
    
    public boolean enableWarning = true;
    public boolean lightVanillaNetherPortalWhenCrouching = true;

    public boolean enableServerPerformanceAdjustment = true;
    public boolean enableDatapackPortalGen = true;

    public int indirectLoadingRadiusCap = 8;

    public int regularPortalLengthLimit = 64;

    public int scaleLimit = 30;

    public boolean easeCreativePermission = true;

    public boolean easeCommandStickPermission = false;
    public boolean portalsChangeGravityByDefault = false;
    public boolean portalWandUsableOnSurvivalMode = false;
    
    // common invisible configs

    public int portalSearchingRange = 128;

    public boolean serverSideNormalChunkLoading = true;

    public boolean teleportationDebug = false;

    public boolean looseMovementCheck = false;

    public boolean chunkPacketDebug = false;

    public boolean enableImmPtlChunkLoading = true;

    public boolean serverTolerantVersionMismatchWithClient = false;

    public boolean serverRejectClientWithoutImmPtl = true;

    public boolean serverTeleportLogging = false;

    public boolean enableCrossPortalInteraction = true;

    public HashSet<String> disabledWarnings = new HashSet<>();

    @Nullable
    public JsonObject dimStackPreset = null;
    
    public static IPConfig getConfig() {
        if (instance == null) {
            load();
        }
        return instance;
    }
    
    private static Path getConfigPath() {
        return O_O.getGameDir().resolve("config").resolve("immersive_portals.json");
    }
    
    public static void load() {
        Path path = getConfigPath();
        IPConfig config = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, IPConfig.class);
            }
            catch (Exception e) {
                Helper.LOGGER.error("Failed to read Immersive Portals config {}, using defaults", path, e);
            }
        }
        if (config == null) {
            config = new IPConfig();
        }
        if (config.disabledWarnings == null) {
            config.disabledWarnings = new HashSet<>();
        }
        instance = config;
        // write back so that newly added options appear in the file
        config.saveConfigFile();
    }
    
    public void saveConfigFile() {
        instance = this;
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        }
        catch (IOException e) {
            Helper.LOGGER.error("Failed to save Immersive Portals config {}", path, e);
        }
        onConfigChanged();
    }
    
    public void onConfigChanged() {
        indirectLoadingRadiusCap = Mth.clamp(indirectLoadingRadiusCap, 1, 32);
        regularPortalLengthLimit = Mth.clamp(regularPortalLengthLimit, 3, 64);
        scaleLimit = Mth.clamp(scaleLimit, 8, 128);
        if (netherPortalMode == null) {
            netherPortalMode = IPGlobal.NetherPortalMode.adaptive;
        }
        if (endPortalMode == null) {
            endPortalMode = IPGlobal.EndPortalMode.normal;
        }
        
        IPGlobal.renderMode = compatibilityRenderMode ? IPGlobal.RenderMode.compatibility : IPGlobal.RenderMode.normal;
        IPGlobal.enableWarning = enableWarning;
        IPGlobal.enableMirrorCreation = enableMirrorCreation;
        IPGlobal.doCheckGlError = doCheckGlError;
        IPGlobal.maxPortalLayer = maxPortalLayer;
        IPGlobal.lagAttackProof = lagAttackProof;
        IPGlobal.portalRenderLimit = portalRenderLimit;
        IPGlobal.netherPortalFindingRadius = portalSearchingRange;
        IPGlobal.renderYourselfInPortal = renderYourselfInPortal;
        IPGlobal.activeLoading = serverSideNormalChunkLoading;
        IPGlobal.teleportationDebugEnabled = teleportationDebug;
        IPGlobal.correctCrossPortalEntityRendering = correctCrossPortalEntityRendering;
        IPGlobal.looseMovementCheck = looseMovementCheck;
        IPGlobal.pureMirror = pureMirror;
        IPGlobal.indirectLoadingRadiusCap = indirectLoadingRadiusCap;
        IPGlobal.netherPortalMode = netherPortalMode;
        IPGlobal.endPortalMode = endPortalMode;
        IPGlobal.reducedPortalRendering = reducedPortalRendering;
        IPGlobal.offsetOcclusionQuery = visibilityPrediction;
        IPGlobal.netherPortalOverlay = netherPortalOverlay;
        IPGlobal.scaleLimit = scaleLimit;
        IPGlobal.easeCreativePermission = easeCreativePermission;
        IPGlobal.easeCommandStickPermission = easeCommandStickPermission;
        IPGlobal.enableSharedBlockMeshBuffers = sharedBlockMeshBufferOptimization;
        IPGlobal.enableDatapackPortalGen = enableDatapackPortalGen;
        IPGlobal.enableCrossPortalView = enableCrossPortalView;
        IPGlobal.enableClippingMechanism = enableClippingMechanism;
        IPGlobal.lightVanillaNetherPortalWhenCrouching = lightVanillaNetherPortalWhenCrouching;
        IPGlobal.enableNetherPortalEffect = enableNetherPortalEffect;
        IPGlobal.enableClientPerformanceAdjustment = enableClientPerformanceAdjustment;
        IPGlobal.enableServerPerformanceAdjustment = enableServerPerformanceAdjustment;
        IPGlobal.enableCrossPortalSound = enableCrossPortalSound;
        IPGlobal.checkModInfoFromInternet = checkModInfoFromInternet;
        IPGlobal.enableUpdateNotification = enableUpdateNotification;
        IPGlobal.enableDepthClampForPortalRendering = useDepthClampForPortalRendering;
        BlockPortalShape.defaultLengthLimit = regularPortalLengthLimit;
        IPGlobal.maxNormalPortalRadius = Math.max(regularPortalLengthLimit / 2, 16);
        IPGlobal.chunkPacketDebug = chunkPacketDebug;
        IPGlobal.saveMemoryInBufferPack = saveMemoryInBufferPack;
        
        Helper.LOGGER.info("iPortal Config Applied");
    }
    
    public boolean shouldDisplayWarning(String warningKey) {
        return enableWarning && !disabledWarnings.contains(warningKey);
    }
    
}
