package qouteall.imm_ptl.core.render;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import qouteall.imm_ptl.core.mixin.client.render.IELightmapRenderStateExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import de.nick1st.imm_ptl.events.ClientCleanupEvent;
import qouteall.q_misc_util.event.IPEventBus;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderer_PortalView;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the views through the portals for the Minecraft 26 renderer.
 * <p>
 * Minecraft 26 renders a frame in two steps: the main thread "extracts" the level into a shared
 * render state, then the level renderer renders that state. Before the main view is extracted,
 * each visible portal's view is extracted and rendered into its own off-screen target, using the
 * destination dimension's level renderer and a camera transformed by the portal.
 * The portal surface is then drawn in the main view by {@link PortalEntityRenderer},
 * sampling that target in screen space.
 * <p>
 * Limitations of this renderer: one layer of portals (portals seen through portals are not drawn),
 * mirrors are not drawn, and the player is not rendered inside the portal view.
 */
public class PortalViewRendering {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Some uniform buffers are 3-buffer rings that can be used 3 times per frame
     * (for example the post effect passes and the clouds), and the main view uses one.
     */
    public static final int MAX_VIEWS_PER_FRAME = 2;

    private static final Identifier PIPELINE_ID =
        Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view");
    private static final Identifier SHADER_ID =
        Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view");

    public static final RenderPipeline PORTAL_VIEW_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withLocation(PIPELINE_ID)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader(SHADER_ID)
        .withFragmentShader(SHADER_ID)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withCull(false)
        .build();

    /**
     * An off-screen target that holds the view through one portal.
     */
    private static final class ViewSlot {
        final int index;
        final Identifier textureId;
        final ViewTexture texture;
        final RenderType renderType;
        @Nullable TextureTarget target;
        // each view has its own lightmap and fog uniform buffers, as they are rings of 3 buffers
        @Nullable Lightmap lightmap;
        @Nullable FogRenderer fogRenderer;

        ViewSlot(int index) {
            this.index = index;
            this.textureId = Identifier.fromNamespaceAndPath("immersive_portals", "portal_view/" + index);
            this.texture = new ViewTexture();
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            this.renderType = RenderType.create(
                "immersive_portals_portal_view_" + index,
                RenderSetup.builder(PORTAL_VIEW_PIPELINE)
                    .withTexture("Sampler0", textureId)
                    .createRenderSetup()
            );
        }

        RenderTarget getTarget(int width, int height) {
            if (target == null) {
                target = new TextureTarget(
                    "Immersive Portals view " + index, width, height, true, GpuFormat.RGBA8_UNORM
                );
            }
            else if (target.width != width || target.height != height) {
                target.resize(width, height);
            }
            texture.setTarget(target);
            if (lightmap == null) {
                lightmap = new Lightmap();
                fogRenderer = new FogRenderer();
            }
            return target;
        }
    }

    /**
     * Exposes a view slot's color target to the texture manager, so that a render type can bind it.
     * It does not own the GPU texture.
     */
    private static final class ViewTexture extends AbstractTexture {
        void setTarget(RenderTarget target) {
            this.texture = target.getColorTexture();
            this.textureView = target.getColorTextureView();
        }

        @Override
        public void close() {
            // the texture belongs to the render target
            this.texture = null;
            this.textureView = null;
        }
    }

    private static final List<ViewSlot> slots = new ArrayList<>();

    // the portals whose views have been rendered in the current frame
    private static final Map<Portal, ViewSlot> renderedViews = new IdentityHashMap<>();

    // a camera per portal, so that its environment attribute probe (sky color etc.) keeps its state
    private static final Map<UUID, Camera> portalCameras = new HashMap<>();
    private static final Map<UUID, Long> portalCameraLastUse = new HashMap<>();

    private static @Nullable ProjectionMatrixBuffer projectionMatrixBuffer;
    private static @Nullable GlobalSettingsUniform globalSettingsUniform;

    /**
     * The views through the portals that lead to the current dimension cannot use the main level renderer,
     * because the level renderer keeps state about its camera (the section grid around the camera and
     * the section occlusion graph), so they use a separate level renderer of the same level.
     */
    private static final class MirrorRenderer {
        final ClientLevel level;
        final LevelRenderer renderer;
        final LevelExtractor extractor;
        long lastUsedFrame;
        
        MirrorRenderer(Minecraft client, ClientLevel level) {
            this.level = level;
            this.renderer = new LevelRenderer(
                client.getEntityRenderDispatcher(),
                client.getBlockEntityRenderDispatcher(),
                client.getModelManager(),
                client.getTextureManager(),
                client.getAtlasManager(),
                client.getShaderManager(),
                client.gameRenderer,
                client.getWindow().getWidth(),
                client.getWindow().getHeight()
            );
            this.extractor = new LevelExtractor(
                client, client.gameRenderer.gameRenderState().levelRenderState, renderer
            );
            extractor.setLevel(level);
            extractor.onResourceManagerReload(client.getResourceManager());
        }
        
        void dispose() {
            extractor.setLevel(null);
            renderer.close();
            if (level.getChunkSource() instanceof ImmPtlClientChunkMap chunkMap) {
                chunkMap.removeTrackingConsumer(extractor);
            }
        }
    }
    
    private static final Map<ClientLevel, MirrorRenderer> mirrorRenderers = new IdentityHashMap<>();
    // primary level extractor -> the mirror's level extractor
    private static final Map<LevelExtractor, LevelExtractor> mirrorExtractors = new IdentityHashMap<>();
    
    private static final Map<LevelRenderer, Long> rendererSizes = new WeakHashMap<>();
    
    private static long frameIndex = 0;
    private static boolean isRenderingView = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, event -> cleanup());
    }
    
    private static void cleanup() {
        mirrorRenderers.values().forEach(MirrorRenderer::dispose);
        mirrorRenderers.clear();
        mirrorExtractors.clear();
        portalCameras.clear();
        portalCameraLastUse.clear();
        renderedViews.clear();
        rendererSizes.clear();
    }
    
    /**
     * @return the level extractor of the mirror renderer, which must receive the block updates
     * that the primary level extractor receives
     */
    public static @Nullable LevelExtractor getMirrorExtractor(LevelExtractor primary) {
        return mirrorExtractors.get(primary);
    }
    
    private static MirrorRenderer getMirrorRenderer(Minecraft client, ClientLevel level) {
        MirrorRenderer mirror = mirrorRenderers.computeIfAbsent(level, k -> {
            MirrorRenderer newMirror = new MirrorRenderer(client, level);
            LevelExtractor primary = ClientWorldLoader.getLevelExtractor(level.dimension());
            if (primary != null) {
                mirrorExtractors.put(primary, newMirror.extractor);
            }
            return newMirror;
        });
        mirror.lastUsedFrame = frameIndex;
        return mirror;
    }
    
    private static void ensureRendererSize(LevelRenderer renderer, int width, int height) {
        long size = ((long) width << 32) | (height & 0xFFFFFFFFL);
        Long oldSize = rendererSizes.put(renderer, size);
        if (oldSize == null || oldSize != size) {
            renderer.resize(width, height);
        }
    }

    private static void tick() {
        portalCameras.values().forEach(camera -> ((IECamera) camera).ip_tickPortalView());
        
        // release the mirror renderers that have not been used for a while
        mirrorRenderers.values().removeIf(mirror -> {
            if (frameIndex - mirror.lastUsedFrame > 600) {
                mirrorExtractors.values().remove(mirror.extractor);
                mirror.dispose();
                return true;
            }
            return false;
        });

        // forget the cameras of the portals that have not been rendered recently
        portalCameraLastUse.entrySet().removeIf(e -> {
            if (frameIndex - e.getValue() > 200) {
                portalCameras.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public static boolean isRenderingView() {
        return isRenderingView;
    }

    /**
     * Called before the main view is extracted.
     */
    public static void renderPortalViews(DeltaTracker deltaTracker) {
        frameIndex++;
        renderedViews.clear();

        Minecraft client = Minecraft.getInstance();
        if (isRenderingView || client.level == null || client.player == null) {
            return;
        }
        if (IPGlobal.renderMode == IPGlobal.RenderMode.none) {
            return;
        }

        Camera mainCamera = client.gameRenderer.mainCamera();
        if (!mainCamera.isInitialized()) {
            return;
        }

        List<Portal> portals = collectPortalsToRender(client.level, mainCamera);

        for (int i = 0; i < portals.size(); i++) {
            Portal portal = portals.get(i);
            ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(portal.getDestDim());
            if (destWorld == null) {
                continue;
            }

            ViewSlot slot = getSlot(i);
            try {
                renderView(client, portal, destWorld, slot, deltaTracker, mainCamera);
                renderedViews.put(portal, slot);
            }
            catch (Throwable e) {
                LOGGER.error("[ImmPtl] Failed to render portal view {}", portal, e);
            }
        }

        RenderStates.portalsRenderedThisFrame = renderedViews.size();
    }

    private static List<Portal> collectPortalsToRender(ClientLevel level, Camera mainCamera) {
        Vec3 cameraPos = mainCamera.position();
        Frustum frustum = mainCamera.getCullFrustum();
        double maxDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;

        List<Portal> candidates = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Portal portal) {
                candidates.add(portal);
            }
        }
        candidates.addAll(GlobalPortalStorage.getGlobalPortals(level));

        List<Portal> result = new ArrayList<>();
        for (Portal portal : candidates) {
            if (!portal.isVisible() || portal instanceof Mirror) {
                continue;
            }
            if (!portal.isInFrontOfPortal(cameraPos)) {
                continue;
            }
            if (portal.getDistanceToNearestPointInPortal(cameraPos) > maxDistance) {
                continue;
            }
            if (!frustum.isVisible(portal.getBoundingBox())) {
                continue;
            }
            result.add(portal);
        }

        result.sort(Comparator.comparingDouble(p -> p.getDistanceToNearestPointInPortal(cameraPos)));
        int limit = Math.min(MAX_VIEWS_PER_FRAME, IPGlobal.portalRenderLimit);
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private static ViewSlot getSlot(int index) {
        while (slots.size() <= index) {
            slots.add(new ViewSlot(slots.size()));
        }
        return slots.get(index);
    }

    private static Camera getPortalCamera(Portal portal) {
        portalCameraLastUse.put(portal.getUUID(), frameIndex);
        return portalCameras.computeIfAbsent(portal.getUUID(), k -> new Camera());
    }

    private static void renderView(
        Minecraft client, Portal portal, ClientLevel destWorld, ViewSlot slot,
        DeltaTracker deltaTracker, Camera mainCamera
    ) {
        GameRenderer gameRenderer = client.gameRenderer;
        IEGameRenderer_PortalView ieGameRenderer = (IEGameRenderer_PortalView) gameRenderer;

        if (projectionMatrixBuffer == null) {
            projectionMatrixBuffer = new ProjectionMatrixBuffer("immersive_portals_portal_view");
            globalSettingsUniform = new GlobalSettingsUniform();
        }

        int width = client.getWindow().getWidth();
        int height = client.getWindow().getHeight();
        RenderTarget target = slot.getTarget(width, height);
        FogRenderer fogRenderer = slot.fogRenderer;

        // set up the camera that looks through the portal
        Vec3 cameraPos = portal.transformPoint(mainCamera.position());
        Quaternionf cameraRotation = portal.getRotationD().toMcQuaternion().mul(mainCamera.rotation());
        Camera camera = getPortalCamera(portal);
        ((IECamera) camera).ip_setupPortalView(destWorld, client.player, cameraPos, cameraRotation, mainCamera);

        float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

        RenderTarget oldMainTarget = gameRenderer.mainRenderTarget;
        Camera oldMainCamera = gameRenderer.mainCamera;
        Lightmap oldLightmap = gameRenderer.lightmap;
        ClientLevel oldLevel = client.level;

        isRenderingView = true;
        PortalRendering.pushPortalLayer(portal);
        WorldRenderInfo.pushRenderInfo(
            new WorldRenderInfo.Builder()
                .setWorld(destWorld)
                .setCameraPos(cameraPos)
                .setDescription(portal.getUUID())
                .build()
        );
        gameRenderer.mainRenderTarget = target;
        gameRenderer.mainCamera = camera;
        // the level renderer binds gameRenderer.levelLightmap()
        gameRenderer.lightmap = slot.lightmap;
        try {
            Runnable renderTask = () -> {
                gameRenderer.lighting().updateLevel(destWorld.dimensionType().cardinalLightType());
                ensureRendererSize(client.levelRenderer, width, height);

                // extract
                var gameRenderState = gameRenderer.gameRenderState();
                // the lightmap extractor updates once per tick, and the main view must still get its update
                LightmapRenderStateExtractor lightmapExtractor = ieGameRenderer.ip_getLightmapRenderStateExtractor();
                IELightmapRenderStateExtractor ieLightmapExtractor = (IELightmapRenderStateExtractor) lightmapExtractor;
                boolean lightmapNeedsUpdate = ieLightmapExtractor.ip_getNeedsUpdate();
                ieLightmapExtractor.ip_setNeedsUpdate(true);
                lightmapExtractor.extract(gameRenderState.lightmapRenderState, 1.0F);
                ieLightmapExtractor.ip_setNeedsUpdate(lightmapNeedsUpdate);
                // The section occlusion graph reads the camera render state on a background thread
                // after the frame, so each view has its own camera render state,
                // and the main view's camera render state is not touched.
                CameraRenderState mainCameraState = gameRenderState.levelRenderState.cameraRenderState;
                CameraRenderState cameraState = new CameraRenderState();
                gameRenderState.levelRenderState.cameraRenderState = cameraState;
                try {
                    camera.extractRenderState(cameraState, worldPartialTicks);
                    // The occlusion culling searches the visible sections from the camera position,
                    // but the camera of a portal view is behind the portal's destination
                    // (often inside the blocks behind it), so it would cull the view
                    cameraState.smartCull = false;
                    cameraState.fogType = camera.getFluidInCamera();
                    cameraState.fogData = fogRenderer.setupFog(
                        camera, client.options.getEffectiveRenderDistance(),
                        deltaTracker, 0, destWorld
                    );
                    client.levelExtractor.extract(deltaTracker, camera, worldPartialTicks);

                    // render
                    Matrix4f projectionMatrix = getProjectionMatrix(
                        ieGameRenderer, cameraState, portal, cameraPos
                    );
                    // cameraState.projectionMatrix is kept unchanged, the oblique projection only goes to the GPU

                    RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                        target.getColorTexture(), new Vector4f(0, 0, 0, 1),
                        target.getDepthTexture(), 0.0
                    );
                    globalSettingsUniform.update(
                        width, height,
                        gameRenderState.optionsRenderState.glintStrength,
                        destWorld.getGameTime(),
                        deltaTracker,
                        gameRenderState.optionsRenderState.menuBackgroundBlurriness,
                        cameraState.pos,
                        false
                    );
                    slot.lightmap.render(gameRenderState.lightmapRenderState);
                    RenderSystem.setProjectionMatrix(
                        projectionMatrixBuffer.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE
                    );
                    fogRenderer.updateBuffer(cameraState.fogData);
                    GpuBufferSlice terrainFog = fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
                    client.levelRenderer.render(
                        ieGameRenderer.ip_getResourcePool(), deltaTracker, false,
                        cameraState, cameraState.viewRotationMatrix,
                        terrainFog, cameraState.fogData.color, true
                    );
                    // rotates the per-frame uniform buffers of this level renderer (clouds)
                    client.levelRenderer.endFrame();
                    fogRenderer.endFrame();
                }
                finally {
                    gameRenderState.levelRenderState.cameraRenderState = mainCameraState;
                }
            };
            
            if (destWorld == oldLevel) {
                MirrorRenderer mirror = getMirrorRenderer(client, destWorld);
                LevelRenderer oldRenderer = client.levelRenderer;
                LevelExtractor oldExtractor = client.levelExtractor;
                client.levelRenderer = mirror.renderer;
                client.levelExtractor = mirror.extractor;
                try {
                    // the chunk update tracking sets are consumed by the level extractor
                    ImmPtlClientChunkMap.withTrackingConsumer(mirror.extractor, renderTask);
                }
                finally {
                    client.levelRenderer = oldRenderer;
                    client.levelExtractor = oldExtractor;
                }
            }
            else {
                ClientWorldLoader.withSwitchedWorld(destWorld, renderTask);
            }
        }
        finally {
            gameRenderer.mainRenderTarget = oldMainTarget;
            gameRenderer.mainCamera = oldMainCamera;
            gameRenderer.lightmap = oldLightmap;
            gameRenderer.lighting().updateLevel(oldLevel.dimensionType().cardinalLightType());
            WorldRenderInfo.popRenderInfo();
            PortalRendering.popPortalLayer();
            isRenderingView = false;
        }
    }

    /**
     * The projection matrix of the main view (with view bobbing),
     * with the near plane replaced by the destination portal plane,
     * so that the things between the camera and the portal's destination are clipped.
     */
    private static Matrix4f getProjectionMatrix(
        IEGameRenderer_PortalView gameRenderer, CameraRenderState cameraState,
        Portal portal, Vec3 cameraPos
    ) {
        Matrix4f projection = new Matrix4f(cameraState.projectionMatrix);
        PoseStack bobStack = new PoseStack();
        gameRenderer.ip_bobHurt(cameraState, bobStack);
        if (Minecraft.getInstance().options.bobView().get()) {
            gameRenderer.ip_bobView(cameraState, bobStack);
        }
        projection.mul(bobStack.last().pose());

        // the clipping plane in view space (camera-relative and rotated by the view rotation)
        Vec3 planePoint = portal.getDestPos().subtract(cameraPos);
        Vec3 planeNormal = portal.getContentDirection();
        Vector3f normalView = cameraState.viewRotationMatrix.transformDirection(
            new Vector3f((float) planeNormal.x, (float) planeNormal.y, (float) planeNormal.z)
        );
        Vector3f pointView = cameraState.viewRotationMatrix.transformPosition(
            new Vector3f((float) planePoint.x, (float) planePoint.y, (float) planePoint.z)
        );
        Vector4f clipPlane = new Vector4f(
            normalView.x, normalView.y, normalView.z, -normalView.dot(pointView)
        );

        return makeObliqueProjection(projection, clipPlane);
    }

    /**
     * Lengyel's oblique near plane, for Minecraft's reversed-Z, zero-to-one depth projection
     * (the near plane is z_clip = w_clip, the far plane is z_clip = 0).
     * The kept side of the clip plane is where clipPlane dot (x, y, z, 1) >= 0.
     */
    public static Matrix4f makeObliqueProjection(Matrix4f projection, Vector4f clipPlane) {
        // the camera must be on the clipped side, otherwise the view would be clipped away
        if (clipPlane.w > 0) {
            return projection;
        }

        Matrix4f inverse = new Matrix4f(projection).invert();
        Vector4f clipPlaneInClipSpace = new Matrix4f(inverse).transpose().transform(new Vector4f(clipPlane));

        // the frustum corner opposite to the clip plane, on the far plane
        Vector4f cornerInClipSpace = new Vector4f(
            Math.signum(clipPlaneInClipSpace.x), Math.signum(clipPlaneInClipSpace.y), 0, 1
        );
        Vector4f corner = inverse.transform(cornerInClipSpace);

        Vector4f row3 = projection.getRow(3, new Vector4f());
        float planeDotCorner = clipPlane.dot(corner);
        if (planeDotCorner < 1e-7f) {
            // the kept side does not intersect the view frustum, nothing behind the portal is visible
            return projection;
        }
        float scale = row3.dot(corner) / planeDotCorner;

        Vector4f newRow2 = new Vector4f(row3).sub(new Vector4f(clipPlane).mul(scale));
        return new Matrix4f(projection).setRow(2, newRow2);
    }

    public static void extractGlobalPortals(
        ClientLevel level, EntityRenderDispatcher entityRenderDispatcher,
        LevelRenderState levelRenderState, float partialTicks
    ) {
        if (isRenderingView || renderedViews.isEmpty()) {
            return;
        }
        for (Portal portal : GlobalPortalStorage.getGlobalPortals(level)) {
            if (renderedViews.containsKey(portal)) {
                levelRenderState.entityRenderStates.add(
                    entityRenderDispatcher.extractEntity(portal, partialTicks)
                );
            }
        }
    }
    
    /**
     * Called when the portal entity is submitted in the main view.
     */
    public static boolean shouldRenderPortalEntity(Portal portal, Frustum frustum) {
        return frustum.isVisible(portal.getBoundingBox());
    }

    public static void submitPortalSurface(
        Portal portal, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (isRenderingView) {
            return;
        }
        ViewSlot slot = renderedViews.get(portal);
        if (slot == null) {
            return;
        }

        // the pose stack is translated to the portal entity position
        submitNodeCollector.submitCustomGeometry(
            poseStack, slot.renderType,
            (pose, buffer) -> portal.renderViewAreaMesh(
                Vec3.ZERO,
                (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
                    vertex(buffer, pose, p0x, p0y, p0z);
                    vertex(buffer, pose, p1x, p1y, p1z);
                    vertex(buffer, pose, p2x, p2y, p2z);
                }
            )
        );
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z);
    }
}
