package qouteall.imm_ptl.core;

import de.nick1st.imm_ptl.events.ClientExitEvent;
import de.nick1st.imm_ptl.events.DimensionEvents;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.event.IPEventBus;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.client.accessor.IEClientLevelData;
import qouteall.imm_ptl.core.mixin.client.accessor.IEClientLevel_Accessor;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.CountDownInt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("resource")
//@OnlyIn(Dist.CLIENT)
public class ClientWorldLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientWorldLoader.class);
    
    private static final CountDownInt LOG_LIMIT = new CountDownInt(20);

    private static final Map<ResourceKey<Level>, ClientLevel> CLIENT_WORLD_MAP =
        new Object2ObjectOpenHashMap<>();
    public static final Map<ResourceKey<Level>, LevelRenderer> WORLD_RENDERER_MAP =
        new Object2ObjectOpenHashMap<>();
    /**
     * In 26.x the client level holds a {@link LevelExtractor} (the main-thread side that collects render states),
     * and the {@link LevelRenderer} is the render-thread side. Each dimension gets a pair of them.
     */
    public static final Map<ResourceKey<Level>, LevelExtractor> LEVEL_EXTRACTOR_MAP =
        new Object2ObjectOpenHashMap<>();
    
    public static @Nullable Map<ResourceKey<Level>, ResourceKey<DimensionType>> dimIdToDimTypeId;

    private static final Minecraft CLIENT = Minecraft.getInstance();
    
    private static boolean isInitialized = false;
    
    private static boolean isCreatingClientWorld = false;
    
    public static boolean isClientRemoteTicking = false;
    
    private static boolean isWorldSwitched = false;
    
    public static void init() {
        IPEventBus.EVENT_BUS.addListener(DimensionEvents.CLIENT_DIMENSION_UPDATE_EVENT.class, (event -> {
            if (getIsInitialized()) {
                List<ResourceKey<Level>> dimensionsToRemove =
                        CLIENT_WORLD_MAP.keySet().stream()
                                .filter(dim -> !event.dimensions.contains(dim)).toList();

                for (ResourceKey<Level> dim : dimensionsToRemove) {
                    disposeDimensionDynamically(dim);
                }

            }
        }));

        IPEventBus.EVENT_BUS.addListener(ClientExitEvent.class, (e) -> dimIdToDimTypeId = null);
    }
    
    public static boolean getIsInitialized() {
        return isInitialized;
    }
    
    public static boolean getIsCreatingClientWorld() {
        return isCreatingClientWorld;
    }
    
    public static void tick() {
        if (IPCGlobal.isClientRemoteTickingEnabled) {
            isClientRemoteTicking = true;
            CLIENT_WORLD_MAP.values().forEach(world -> {
                if (CLIENT.level != world) {
                    tickRemoteWorld(world);
                }
            });
            isClientRemoteTicking = false;
        }
    }
    
    private static void tickRemoteWorld(ClientLevel newWorld) {
        List<Portal> nearbyPortals = CHelper.getClientNearbyPortals(10).collect(Collectors.toList());
        
        withSwitchedWorld(newWorld, () -> {
            try {
                newWorld.tickEntities();
                newWorld.tick(() -> true);
                
                if (!CLIENT.isPaused()) {
                    tickRemoteWorldRandomTicksClient(newWorld, nearbyPortals);
                }
                
                newWorld.pollLightUpdates();
            }
            catch (Throwable e) {
                if (LOG_LIMIT.tryDecrement()) {
                    LOGGER.error("", e);
                }
            }
        });
    }
    
    // show nether particles through portal
    // TODO optimize it in future version?
    private static void tickRemoteWorldRandomTicksClient(
        ClientLevel newWorld, List<Portal> nearbyPortals
    ) {
        nearbyPortals.stream().filter(
            portal -> portal.getDestDim() == newWorld.dimension()
        ).findFirst().ifPresent(portal -> {
            assert CLIENT.player != null;
            Vec3 playerPos = CLIENT.player.position();
            Vec3 center = portal.transformPoint(playerPos);
            
            Camera camera = CLIENT.gameRenderer.mainCamera();
            Vec3 oldCameraPos = camera.position();
            
            ((IECamera) camera).portal_setPos(center);
            
            if (newWorld.getGameTime() % 2 == 0) {
                // it costs some CPU time
                newWorld.animateTick(
                    (int) center.x, (int) center.y, (int) center.z
                );
            }
            
            CLIENT.particleEngine.tick();
            
            ((IECamera) camera).portal_setPos(oldCameraPos);
        });
        
        
    }
    
    public static void cleanUp() {
        // unbind the other dimensions' level renderers from their levels (Sodium frees its terrain buffers then)
        LEVEL_EXTRACTOR_MAP.values().forEach(levelExtractor -> {
            if (levelExtractor != CLIENT.levelExtractor && levelExtractor.levelRenderer != CLIENT.levelRenderer) {
                setExtractorLevel(levelExtractor, null);
            }
        });
        
        WORLD_RENDERER_MAP.values().forEach(
            ClientWorldLoader::disposeWorldRenderer
        );
        
        for (ClientLevel clientWorld : CLIENT_WORLD_MAP.values()) {
            ((IEClientWorld) clientWorld).ip_resetWorldRendererRef();
        }
        
        CLIENT_WORLD_MAP.clear();
        WORLD_RENDERER_MAP.clear();
        LEVEL_EXTRACTOR_MAP.clear();
        
        isInitialized = false;
    }
    
    /**
     * Sets the level of a level extractor that is not the current one.
     * Sodium binds its terrain renderer to the level in {@link LevelExtractor#setLevel},
     * and takes the terrain renderer of {@link Minecraft#levelRenderer}, not of the extractor's level renderer.
     * Without switching it, creating another dimension's level renderer would make
     * the current dimension's terrain renderer switch to the other dimension.
     */
    public static void setExtractorLevel(LevelExtractor levelExtractor, @Nullable ClientLevel level) {
        LevelRenderer originalLevelRenderer = CLIENT.levelRenderer;
        // the fields are made accessible by access transformer
        CLIENT.levelRenderer = levelExtractor.levelRenderer;
        try {
            levelExtractor.setLevel(level);
        }
        finally {
            CLIENT.levelRenderer = originalLevelRenderer;
        }
    }
    
    private static void disposeWorldRenderer(LevelRenderer worldRenderer) {
        if (worldRenderer != CLIENT.levelRenderer) {
            worldRenderer.close();
        }
    }
    
    private static void disposeDimensionDynamically(ResourceKey<Level> dimension) {
        Validate.notNull(CLIENT.player, "player is null");
        Validate.notNull(CLIENT.level, "level is null");
        Validate.isTrue(
            CLIENT.level.dimension() != dimension,
            "Cannot dispose current dimension"
        );
        Validate.isTrue(
            CLIENT.player.level().dimension() != dimension,
            "Cannot dispose current dimension"
        );
        Validate.isTrue(CLIENT.isSameThread(), "not on client thread");
        
        LevelRenderer worldRenderer = WORLD_RENDERER_MAP.get(dimension);
        LevelExtractor levelExtractor = LEVEL_EXTRACTOR_MAP.remove(dimension);
        if (levelExtractor != null) {
            setExtractorLevel(levelExtractor, null);
        }
        disposeWorldRenderer(worldRenderer);
        WORLD_RENDERER_MAP.remove(dimension);
        
        Validate.isTrue(CLIENT.levelRenderer != worldRenderer);
        
        ClientLevel clientWorld = CLIENT_WORLD_MAP.get(dimension);
        ((IEClientWorld) clientWorld).ip_resetWorldRendererRef();
        CLIENT_WORLD_MAP.remove(dimension);
        
        LOGGER.info("Client Dynamically Removed Dimension {}", dimension.identifier());
        
        if (clientWorld.getChunkSource().getLoadedChunksCount() > 0) {
            LOGGER.error("The chunks of that dimension was not cleared before removal");
        }
        
        if (clientWorld.getEntityCount() > 0) {
            LOGGER.error("The entities of that dimension was not cleared before removal");
        }
        
        CLIENT.gameRenderer.resetData();

        IPEventBus.EVENT_BUS.post(new DimensionEvents.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT(dimension));
    }
    
    @NotNull
    public static LevelRenderer getWorldRenderer(ResourceKey<Level> dimension) {
        initializeIfNeeded();
        
        LevelRenderer result = WORLD_RENDERER_MAP.get(dimension);
        
        if (result == null) {
            LOGGER.warn(
                "Acquiring LevelRenderer before acquiring Level. Something is probably wrong. {}",
                dimension.identifier(), new Throwable()
            );
            
            // the world renderer is created along with the world
            // so create the world now
            getWorld(dimension);
            
            result = WORLD_RENDERER_MAP.get(dimension);
            
            if (result == null) {
                throw new RuntimeException("Unable to get LevelRenderer of " + dimension.identifier());
            }
        }
        
        return result;
    }
    
    
    /**
     * Get the client world and create if missing.
     * If the dimension intId is invalid, it will throw an error
     */
    @NotNull
    public static ClientLevel getWorld(ResourceKey<Level> dimension) {
        Validate.notNull(dimension, "dimension is null");
        Validate.isTrue(CLIENT.isSameThread());
        
        initializeIfNeeded();
        
        if (!CLIENT_WORLD_MAP.containsKey(dimension)) {
            return createSecondaryClientWorld(dimension);
        }
        
        ClientLevel result = CLIENT_WORLD_MAP.get(dimension);
        Validate.notNull(result, "null value in world map");
        return result;
    }
    
    /**
     * Get the client world and create if missing.
     * If the dimension intId is invalid, it will return null
     */
    @Nullable
    public static ClientLevel getOptionalWorld(ResourceKey<Level> dimension) {
        Validate.notNull(dimension, "dimension is null");
        Validate.isTrue(CLIENT.isSameThread(), "not on client thread");
        
        if (getServerDimensions().contains(dimension)) {
            return getWorld(dimension);
        }
        
        return null;
    }
    
    @NotNull
    public static LevelExtractor getLevelExtractor(ResourceKey<Level> dimension) {
        initializeIfNeeded();
        
        LevelExtractor result = LEVEL_EXTRACTOR_MAP.get(dimension);
        if (result == null) {
            getWorld(dimension);
            result = LEVEL_EXTRACTOR_MAP.get(dimension);
            if (result == null) {
                throw new RuntimeException("Unable to get LevelExtractor of " + dimension.identifier());
            }
        }
        return result;
    }
    
    @SuppressWarnings("ConstantValue")
    public static void initializeIfNeeded() {
        if (!isInitialized) {
            Validate.isTrue(
                CLIENT.level != null, "level is null"
            );
            // note: client.levelRenderer is not necessarily not null due to mixin
            Validate.isTrue(
                CLIENT.levelRenderer != null, "levelRenderer is null"
            );
            
            Validate.notNull(
                CLIENT.player,
                "player is null. This may be caused by prior initialization failure. The log may provide useful information."
            );
            Validate.isTrue(
                CLIENT.player.level() == CLIENT.level,
                "The player level is not the same as client level"
            );
            
            ResourceKey<Level> playerDimension = CLIENT.level.dimension();
            CLIENT_WORLD_MAP.put(playerDimension, CLIENT.level);
            WORLD_RENDERER_MAP.put(playerDimension, CLIENT.levelRenderer);
            LEVEL_EXTRACTOR_MAP.put(playerDimension, CLIENT.levelExtractor);
            
            isInitialized = true;
        }
    }
    
    @SuppressWarnings("DataFlowIssue")
    private static ClientLevel createSecondaryClientWorld(ResourceKey<Level> dimension) {
        Validate.notNull(CLIENT.player, "player is null");
        Validate.isTrue(CLIENT.isSameThread(), "not on client thread");
        
        Set<ResourceKey<Level>> dimIds = getServerDimensions();
        if (!dimIds.contains(dimension)) {
            throw new RuntimeException("Cannot create invalid client dimension " + dimension.identifier());
        }
        
        isCreatingClientWorld = true;
        
        Profiler.get().push("create_world");
        
        int chunkLoadDistance = 3; // my own chunk manager doesn't need it
        
        LevelRenderer worldRenderer = new LevelRenderer(
            CLIENT.getEntityRenderDispatcher(),
            CLIENT.getBlockEntityRenderDispatcher(),
            CLIENT.getModelManager(),
            CLIENT.getTextureManager(),
            CLIENT.getAtlasManager(),
            CLIENT.getShaderManager(),
            CLIENT.gameRenderer,
            CLIENT.getWindow().getWidth(),
            CLIENT.getWindow().getHeight()
        );
        // all dimensions share the level render state.
        // the portal views are extracted and rendered one after another, never interleaved
        LevelExtractor levelExtractor = new LevelExtractor(
            CLIENT, CLIENT.gameRenderer.gameRenderState().levelRenderState, worldRenderer
        );
        
        ClientLevel newWorld;
        try {
            ClientPacketListener mainNetHandler = CLIENT.player.connection;
            assert CLIENT.level != null;
            Map<String, MapItemSavedData> mapData = ((IEClientLevel_Accessor) CLIENT.level).ip_getMapData();

            Validate.notNull(
                dimIdToDimTypeId, "dimension type mapping is missing"
            );
            ResourceKey<DimensionType> dimensionTypeKey = dimIdToDimTypeId.get(dimension);
            
            if (dimensionTypeKey == null) {
                throw new IllegalStateException(
                    "Cannot find dimension type for %s in %s"
                        .formatted(dimension.identifier(), dimIdToDimTypeId)
                );
            }

            ClientLevel.ClientLevelData currentProperty =
                (ClientLevel.ClientLevelData) ((IEWorld) CLIENT.level).ip_getLevelData();
            RegistryAccess registryManager = mainNetHandler.registryAccess();
            int simulationDistance = CLIENT.level.getServerSimulationDistance();
            
            Holder<DimensionType> dimensionType = registryManager
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(dimensionTypeKey);
            
            // currently use a separated level data object
            // day time is not shared between worlds
            ClientLevel.ClientLevelData properties = new ClientLevel.ClientLevelData(
                currentProperty.getDifficulty(),
                currentProperty.isHardcore(),
                ((IEClientLevelData) currentProperty).ip_getIsFlat()
            );
            newWorld = new ClientLevel(
                mainNetHandler,
                properties,
                dimension,
                dimensionType,
                chunkLoadDistance,
                simulationDistance,// seems that client world does not use this
                levelExtractor,
                CLIENT.level.isDebug(),
                CLIENT.level.getBiomeManager().biomeZoomSeed,
                // the sea level of other dimensions is not synced. it only affects some rendering details
                CLIENT.level.getSeaLevel()
            );
            
            // all worlds share the same map data map
            ((IEClientLevel_Accessor) newWorld).ip_setMapData(mapData);

            // all worlds share the same tick rate manager
            ((IEClientWorld) newWorld).ip_setTickRateManager(CLIENT.level.tickRateManager());

            setExtractorLevel(levelExtractor, newWorld);
            
            levelExtractor.onResourceManagerReload(CLIENT.getResourceManager());
            
            CLIENT_WORLD_MAP.put(dimension, newWorld);
            WORLD_RENDERER_MAP.put(dimension, worldRenderer);
            LEVEL_EXTRACTOR_MAP.put(dimension, levelExtractor);

            LOGGER.info("Client World Created {}", dimension.identifier());
        }
        catch (Exception e) {
            throw new IllegalStateException(
                "Creating Client World " + dimension.identifier() + " " + CLIENT_WORLD_MAP.keySet(),
                e
            );
        }
        finally {
            isCreatingClientWorld = false;
            Profiler.get().pop();
        }

        IPEventBus.EVENT_BUS.post(new DimensionEvents.CLIENT_WORLD_LOAD_EVENT(newWorld));
        
        return newWorld;
    }
    
    public static Set<ResourceKey<Level>> getServerDimensions() {
        assert CLIENT.player != null;
        return CLIENT.player.connection.levels();
    }
    
    public static Collection<ClientLevel> getClientWorlds() {
        Validate.isTrue(isInitialized);
        
        return CLIENT_WORLD_MAP.values();
    }
    
    private static boolean isReloadingOtherWorldRenderers = false;
    
    @SuppressWarnings("Convert2MethodRef")
    public static void _onWorldRendererReloaded() {
        Validate.isTrue(CLIENT.isSameThread());
        if (CLIENT.level != null) {
            LOGGER.info("WorldRenderer reloaded {}", CLIENT.level.dimension().identifier());
        }
        
        if (isReloadingOtherWorldRenderers) {
            return;
        }
        if (PortalRendering.isRendering()) {
            return;
        }
        if (ClientWorldLoader.getIsCreatingClientWorld()) {
            return;
        }
        
        isReloadingOtherWorldRenderers = true;
        
        List<ResourceKey<Level>> toReload = WORLD_RENDERER_MAP.keySet().stream()
            .filter(d -> d != CLIENT.level.dimension()).toList();
        
        for (ResourceKey<Level> dim : toReload) {
            ClientLevel world = CLIENT_WORLD_MAP.get(dim);
            Validate.notNull(world, "missing client world %s", dim.identifier());
            withSwitchedWorld(
                world,
                () -> {
                    // cannot be replaced into method reference
                    // because levelRenderer field is actually mutable
                    CLIENT.levelExtractor.allChanged();
                }
            );
        }
        
        isReloadingOtherWorldRenderers = false;
    }
    
    /**
     * It will not switch the dimension of client player
     */
    @SuppressWarnings({"ReassignedVariable", "DataFlowIssue"})
    public static <T> T withSwitchedWorld(ClientLevel newWorld, Supplier<T> supplier) {
        Validate.isTrue(CLIENT.isSameThread(), "not on client thread");
        Validate.isTrue(CLIENT.player != null, "player is null");
        
        ClientPacketListener networkHandler = CLIENT.getConnection();
        assert networkHandler != null;
        
        ClientLevel originalWorld = CLIENT.level;
        LevelRenderer originalWorldRenderer = CLIENT.levelRenderer;
        LevelExtractor originalLevelExtractor = CLIENT.levelExtractor;
        ClientLevel originalNetHandlerWorld = networkHandler.getLevel();
        boolean originalIsWorldSwitched = isWorldSwitched;
        
        LevelRenderer newWorldRenderer = getWorldRenderer(newWorld.dimension());
        LevelExtractor newLevelExtractor = getLevelExtractor(newWorld.dimension());
        
        Validate.notNull(newWorldRenderer, "new world renderer is null");
        
        CLIENT.level = newWorld;
        ((IEParticleManager) CLIENT.particleEngine).ip_setWorld(newWorld);
        CLIENT.levelRenderer = newWorldRenderer;
        CLIENT.levelExtractor = newLevelExtractor;
        ((IEClientPlayNetworkHandler) networkHandler).ip_setWorld(newWorld);
        isWorldSwitched = true;
        
        try {
            return supplier.get();
        }
        finally {
            if (CLIENT.level != newWorld) {
                LOGGER.error("Respawn packet should not be redirected");
                originalWorld = CLIENT.level;
                originalWorldRenderer = CLIENT.levelRenderer;
                originalLevelExtractor = CLIENT.levelExtractor;
            }
            
            CLIENT.level = originalWorld;
            // the fields are made non-final by access transformer
            CLIENT.levelRenderer = originalWorldRenderer;
            CLIENT.levelExtractor = originalLevelExtractor;
            ((IEParticleManager) CLIENT.particleEngine).ip_setWorld(originalWorld);
            ((IEClientPlayNetworkHandler) networkHandler).ip_setWorld(originalNetHandlerWorld);
            isWorldSwitched = originalIsWorldSwitched;
        }
    }
    
    public static void withSwitchedWorld(ClientLevel newWorld, Runnable runnable) {
        withSwitchedWorld(newWorld, () -> {
            runnable.run();
            return null;
        });
    }
    
    public static void withSwitchedWorldFailSoft(ResourceKey<Level> dim, Runnable runnable) {
        ClientLevel world = getOptionalWorld(dim);

        if (runnable == null)
            LOGGER.error("Breakpoint");
        
        if (world == null) {
            LOGGER.error(
                "Ignoring redirected task of invalid dimension {}", dim.identifier(), new Throwable()
            );
            return;
        }
        
        withSwitchedWorld(world, runnable);
    }
    
    public static boolean getIsWorldSwitched() {
        return isWorldSwitched;
    }
    
    public static class RemoteCallables {
        public static void checkBiomeRegistry(
            Map<String, Integer> idMap
        ) {
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            RegistryAccess registryAccess = player.connection.registryAccess();
            Registry<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
            
            for (Map.Entry<String, Integer> entry : idMap.entrySet()) {
                Identifier id = McHelper.newIdentifier(entry.getKey());
                int expectedId = entry.getValue();
                
                if (biomes.getId(biomes.getValue(id)) != expectedId) {
                    LOGGER.error("Biome intId mismatch: {} {}", id, expectedId);
                }
            }
            
            if (idMap.size() != biomes.keySet().size()) {
                LOGGER.error("Biome intId mismatch: size not equal");
            }
            
            LOGGER.info("Biome intId check finished");
        }
    }
}
