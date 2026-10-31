package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import de.nick1st.imm_ptl.events.ClientCleanupEvent;
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
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderer_PortalView;
import qouteall.imm_ptl.core.mixin.client.render.IELightmapRenderStateExtractor;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.event.IPEventBus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Renders the views through the portals for the Minecraft 26 renderer.
 * <p>
 * Minecraft 26 renders a frame in two steps: the main thread "extracts" the level into a shared
 * render state, then the level renderer renders that state. Before the main view is extracted,
 * each visible portal's view is extracted and rendered into its own off-screen target, using a
 * level renderer of the destination dimension and a camera transformed by the portal.
 * The portal surface is then drawn by {@link PortalEntityRenderer}, sampling that target in screen space.
 * <p>
 * The portals seen through portals are handled the same way: the views form a tree
 * (built breadth-first from the main camera, up to the max portal layer and the portal render limit),
 * and the views are rendered deepest-first, so a view is rendered after the views of the portals in it.
 * <p>
 * A mirror (or any portal transformation with a reflection) cannot be rendered with a normal camera,
 * as the reflection turns the faces inside out. Such a view is rendered with the camera
 * whose horizontal axis is negated, and the image is flipped horizontally when the surface samples it.
 */
public class PortalViewRendering {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier SHADER_ID =
        Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view");

    private static final RenderPipeline.Snippet PORTAL_VIEW_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader(SHADER_ID)
        .withFragmentShader(SHADER_ID)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withCull(false)
        .buildSnippet();

    public static final RenderPipeline PORTAL_VIEW_PIPELINE = RenderPipeline.builder(PORTAL_VIEW_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view"))
        .build();

    // samples the view image flipped horizontally
    public static final RenderPipeline PORTAL_VIEW_FLIPPED_PIPELINE = RenderPipeline.builder(PORTAL_VIEW_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view_flipped"))
        .withShaderDefine("FLIP_X")
        .build();

    /**
     * An off-screen target that holds one portal view.
     */
    private static final class ViewSlot {
        final int index;
        final Identifier textureId;
        final ViewTexture texture;
        final RenderType renderType;
        final RenderType flippedRenderType;
        @Nullable TextureTarget target;
        // each view has its own lightmap and fog uniform buffers
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
            this.flippedRenderType = RenderType.create(
                "immersive_portals_portal_view_flipped_" + index,
                RenderSetup.builder(PORTAL_VIEW_FLIPPED_PIPELINE)
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

    /**
     * A view in the view tree of the current frame. The root is the main view.
     */
    private static final class ViewNode {
        final @Nullable ViewNode parent;
        final @Nullable Portal portal;
        final int depth;
        final ClientLevel level;
        final Vec3 cameraPos;
        // the camera basis transformed by the portals. It's left-handed when flipped.
        final Vector3f right;
        final Vector3f up;
        final Vector3f forward;
        final boolean flipped;
        final List<UUID> path;
        final Camera camera;
        final Map<Portal, ViewNode> children = new IdentityHashMap<>();
        @Nullable ViewSlot slot;
        @Nullable PooledRenderer renderer;
        // the level renderer also renders another view in this frame (at another place)
        boolean sharesRenderer;

        ViewNode(
            @Nullable ViewNode parent, @Nullable Portal portal, ClientLevel level, Vec3 cameraPos,
            Vector3f right, Vector3f up, Vector3f forward, List<UUID> path, Camera camera
        ) {
            this.parent = parent;
            this.portal = portal;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.level = level;
            this.cameraPos = cameraPos;
            this.right = right;
            this.up = up;
            this.forward = forward;
            this.flipped = right.dot(new Vector3f(forward).cross(up)) < 0;
            this.path = path;
            this.camera = camera;
        }

        /**
         * Whether the view image has to be flipped when its surface is drawn into the parent view.
         */
        boolean isFlippedRelativeToParent() {
            return parent != null && flipped != parent.flipped;
        }
    }

    /**
     * A level renderer used by the portal views.
     * A level renderer keeps state about its camera (the section grid around the camera, the section occlusion graph),
     * and the sections that leave the grid when the camera moves are recompiled. So the views at different places
     * use different level renderers, and each view uses the level renderer that was used near its camera before.
     * <p>
     * The level's own level renderer is used when the player is not in the level.
     * The others are created on demand, and receive the block updates of the level's level renderer.
     */
    private static final class PooledRenderer {
        final ClientLevel level;
        final LevelRenderer renderer;
        final LevelExtractor extractor;
        // whether it was created for the portal views (not the level's own level renderer)
        final boolean isExtra;
        @Nullable SectionPos lastCameraSection;
        long lastUsedFrame = -1;

        PooledRenderer(ClientLevel level, LevelRenderer renderer, LevelExtractor extractor, boolean isExtra) {
            this.level = level;
            this.renderer = renderer;
            this.extractor = extractor;
            this.isExtra = isExtra;
        }

        static PooledRenderer createExtra(Minecraft client, ClientLevel level) {
            LevelRenderer renderer = new LevelRenderer(
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
            LevelExtractor extractor = new LevelExtractor(
                client, client.gameRenderer.gameRenderState().levelRenderState, renderer
            );
            ClientWorldLoader.setExtractorLevel(extractor, level);
            extractor.onResourceManagerReload(client.getResourceManager());
            return new PooledRenderer(level, renderer, extractor, true);
        }

        void dispose() {
            if (!isExtra) {
                return;
            }
            ClientWorldLoader.setExtractorLevel(extractor, null);
            renderer.close();
            if (level.getChunkSource() instanceof ImmPtlClientChunkMap chunkMap) {
                chunkMap.removeTrackingConsumer(extractor);
            }
        }
    }

    // at most this many extra level renderers for one level
    private static final int MAX_EXTRA_RENDERERS_PER_LEVEL = 4;

    /**
     * Sodium's terrain renderer keeps the sections of all loaded chunks, not a grid around the camera,
     * so the views of a level can share its own level renderer.
     * Extra level renderers would also compete for the chunk load events of Sodium's chunk tracker, which is per level.
     */
    private static int getMaxExtraRenderersPerLevel() {
        return SodiumInterface.invoker.isSodiumPresent() ? 0 : MAX_EXTRA_RENDERERS_PER_LEVEL;
    }

    private static final Map<ClientLevel, List<PooledRenderer>> extraRenderers = new IdentityHashMap<>();
    // the state of the levels' own level renderers when used by the portal views
    private static final Map<LevelRenderer, PooledRenderer> primaryRenderers = new WeakHashMap<>();
    // the level's own level extractor -> the extra level extractors of the level
    private static final Map<LevelExtractor, List<LevelExtractor>> extraExtractors = new IdentityHashMap<>();

    private static final List<ViewSlot> slots = new ArrayList<>();

    // a camera per view path, so that its environment attribute probe (sky color etc.) keeps its state
    private static final Map<List<UUID>, Camera> viewCameras = new HashMap<>();
    private static final Map<List<UUID>, Long> viewCameraLastUse = new HashMap<>();

    private static @Nullable ProjectionMatrixBuffer projectionMatrixBuffer;
    private static @Nullable GlobalSettingsUniform globalSettingsUniform;

    private static final Map<LevelRenderer, Long> rendererSizes = new WeakHashMap<>();

    private static long frameIndex = 0;

    // the view tree of the current frame
    private static @Nullable ViewNode rootView;
    // the view whose level is being rendered. it is the root view when rendering the main view
    private static @Nullable ViewNode renderingView;

    private static boolean isSharingRenderer = false;

    private static boolean lastShaderPackInUse = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, event -> cleanup());
    }

    private static void cleanup() {
        extraRenderers.values().forEach(list -> list.forEach(PooledRenderer::dispose));
        extraRenderers.clear();
        extraExtractors.clear();
        primaryRenderers.clear();
        viewCameras.clear();
        viewCameraLastUse.clear();
        rendererSizes.clear();
        rootView = null;
        renderingView = null;
    }

    private static void tick() {
        viewCameras.values().forEach(camera -> ((IECamera) camera).ip_tickPortalView());

        // release the extra level renderers that have not been used for a while
        for (List<PooledRenderer> list : extraRenderers.values()) {
            list.removeIf(entry -> {
                if (frameIndex - entry.lastUsedFrame > 600) {
                    extraExtractors.values().forEach(extractors -> extractors.remove(entry.extractor));
                    entry.dispose();
                    return true;
                }
                return false;
            });
        }
        extraRenderers.values().removeIf(List::isEmpty);

        // forget the cameras of the views that have not been rendered recently
        viewCameraLastUse.entrySet().removeIf(e -> {
            if (frameIndex - e.getValue() > 200) {
                viewCameras.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * @return the extra level extractors of the level, which must receive the block updates
     * that the level's own level extractor receives
     */
    public static List<LevelExtractor> getExtraExtractors(LevelExtractor primary) {
        return extraExtractors.getOrDefault(primary, List.of());
    }

    public static boolean isRenderingView() {
        return renderingView != null && renderingView != rootView;
    }

    /**
     * Whether the level renderer being used renders another view (at another place) in this frame.
     * See {@link qouteall.imm_ptl.core.mixin.client.render.MixinLevelRenderer_PortalView}
     */
    public static boolean isSharingRenderer() {
        return isSharingRenderer;
    }

    /**
     * Called before the main view is extracted.
     */
    public static void renderPortalViews(DeltaTracker deltaTracker) {
        frameIndex++;
        rootView = null;
        renderingView = null;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        if (IPGlobal.renderMode == IPGlobal.RenderMode.none) {
            return;
        }
        // With a shader pack, Iris renders the level into its own targets with the shader pack's programs.
        // The portal views don't support that yet, so the portals show nothing.
        boolean shaderPackInUse = IrisInterface.invoker.isShaders();
        if (shaderPackInUse != lastShaderPackInUse) {
            lastShaderPackInUse = shaderPackInUse;
            if (shaderPackInUse) {
                CHelper.printChat(
                    Component.translatable("imm_ptl.portal_views_off_with_shaders").withStyle(ChatFormatting.GOLD)
                );
            }
        }
        if (shaderPackInUse) {
            return;
        }

        Camera mainCamera = client.gameRenderer.mainCamera();
        if (!mainCamera.isInitialized()) {
            return;
        }

        Quaternionf mainRotation = mainCamera.rotation();
        ViewNode root = new ViewNode(
            null, null, client.level, mainCamera.position(),
            mainRotation.transform(new Vector3f(1, 0, 0)),
            mainRotation.transform(new Vector3f(0, 1, 0)),
            mainRotation.transform(new Vector3f(0, 0, -1)),
            List.of(), mainCamera
        );

        List<ViewNode> views = buildViewTree(client, root, mainCamera);

        // the outer views are larger on screen, they get the level renderers first
        for (ViewNode view : views) {
            assignRenderer(client, view);
        }

        // render the deepest views first, a view samples the views of the portals in it
        for (int i = views.size() - 1; i >= 0; i--) {
            ViewNode view = views.get(i);
            view.slot = getSlot(i);
            try {
                renderView(client, view, deltaTracker, mainCamera);
            }
            catch (Throwable e) {
                LOGGER.error("[ImmPtl] Failed to render portal view {}", view.portal, e);
                view.slot = null;
            }
        }

        rootView = root;
        renderingView = root;
        RenderStates.portalsRenderedThisFrame = views.size();
    }

    /**
     * @return the views in breadth-first order (a view comes after its parent)
     */
    private static List<ViewNode> buildViewTree(Minecraft client, ViewNode root, Camera mainCamera) {
        int maxDepth = PortalRendering.getMaxPortalLayer();
        int budget = Math.max(0, IPGlobal.portalRenderLimit);

        List<ViewNode> result = new ArrayList<>();
        ArrayDeque<ViewNode> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty() && result.size() < budget) {
            ViewNode parent = queue.poll();
            if (parent.depth >= maxDepth) {
                continue;
            }

            for (Portal portal : collectVisiblePortals(client, parent)) {
                if (result.size() >= budget) {
                    break;
                }
                ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(portal.getDestDim());
                if (destWorld == null) {
                    continue;
                }
                ViewNode child = createChildView(client, parent, portal, destWorld, mainCamera);
                parent.children.put(portal, child);
                result.add(child);
                queue.add(child);
            }
        }

        return result;
    }

    private static List<Portal> collectVisiblePortals(Minecraft client, ViewNode view) {
        Vec3 cameraPos = view.cameraPos;
        Frustum frustum = view.camera.getCullFrustum();
        double maxDistance = client.options.getEffectiveRenderDistance() * 16;

        List<Portal> candidates = new ArrayList<>();
        for (Entity entity : view.level.entitiesForRendering()) {
            if (entity instanceof Portal portal) {
                candidates.add(portal);
            }
        }
        candidates.addAll(GlobalPortalStorage.getGlobalPortals(view.level));

        List<Portal> result = new ArrayList<>();
        for (Portal portal : candidates) {
            if (!portal.isVisible()) {
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
            if (view.portal != null) {
                // the portal at the destination of the outer portal that faces back
                if (Portal.isParallelOrientedPortal(portal, view.portal)) {
                    continue;
                }
                // clipped away by the outer portal's clipping plane
                if (isBehindPlane(portal.getBoundingBox(), view.portal.getDestPos(), view.portal.getContentDirection())) {
                    continue;
                }
            }
            result.add(portal);
        }

        result.sort(Comparator.comparingDouble(p -> p.getDistanceToNearestPointInPortal(cameraPos)));
        return result;
    }

    private static boolean isBehindPlane(AABB box, Vec3 planePoint, Vec3 planeNormal) {
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                (i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY,
                (i & 4) == 0 ? box.minZ : box.maxZ
            );
            if (corner.subtract(planePoint).dot(planeNormal) > 0.001) {
                return false;
            }
        }
        return true;
    }

    private static ViewNode createChildView(
        Minecraft client, ViewNode parent, Portal portal, ClientLevel destWorld, Camera mainCamera
    ) {
        Vec3 cameraPos = portal.transformPoint(parent.cameraPos);
        Vector3f right = transformDirection(portal, parent.right);
        Vector3f up = transformDirection(portal, parent.up);
        Vector3f forward = transformDirection(portal, parent.forward);

        List<UUID> path = new ArrayList<>(parent.path);
        path.add(portal.getUUID());
        viewCameraLastUse.put(path, frameIndex);
        Camera camera = viewCameras.computeIfAbsent(path, k -> new Camera());

        // The camera is always right-handed. When the transformation has a reflection,
        // the rendered image is the mirror image of the view, and it's flipped back when sampled
        Vector3f renderRight = new Vector3f(forward).cross(up);
        Quaternionf rotation = new Matrix3f(
            renderRight.x, renderRight.y, renderRight.z,
            up.x, up.y, up.z,
            -forward.x, -forward.y, -forward.z
        ).getNormalizedRotation(new Quaternionf());

        ((IECamera) camera).ip_setupPortalView(
            destWorld, client.player, cameraPos, rotation, mainCamera, IPGlobal.renderYourselfInPortal
        );

        return new ViewNode(parent, portal, destWorld, cameraPos, right, up, forward, path, camera);
    }

    private static Vector3f transformDirection(Portal portal, Vector3f direction) {
        Vec3 result = portal.transformLocalVecNonScale(new Vec3(direction.x, direction.y, direction.z));
        return new Vector3f((float) result.x, (float) result.y, (float) result.z).normalize();
    }

    private static ViewSlot getSlot(int index) {
        while (slots.size() <= index) {
            slots.add(new ViewSlot(slots.size()));
        }
        return slots.get(index);
    }

    /**
     * Chooses the level renderer for a view, see {@link PooledRenderer}.
     */
    private static void assignRenderer(Minecraft client, ViewNode view) {
        ClientLevel level = view.level;
        SectionPos cameraSection = SectionPos.of(view.cameraPos);

        List<PooledRenderer> candidates = new ArrayList<>(extraRenderers.getOrDefault(level, List.of()));
        // the main view uses the current level's own level renderer, except with Sodium (see getMaxExtraRenderersPerLevel)
        boolean usesMainRenderer = level == client.level && getMaxExtraRenderersPerLevel() == 0;
        if (level != client.level || usesMainRenderer) {
            LevelRenderer primary = ClientWorldLoader.getWorldRenderer(level.dimension());
            LevelExtractor primaryExtractor = ClientWorldLoader.getLevelExtractor(level.dimension());
            if (primary != null && primaryExtractor != null) {
                candidates.add(primaryRenderers.computeIfAbsent(
                    primary, r -> new PooledRenderer(level, r, primaryExtractor, false)
                ));
            }
        }

        PooledRenderer chosen = null;
        // the camera section does not change: nothing is recompiled
        for (PooledRenderer candidate : candidates) {
            if (cameraSection.equals(candidate.lastCameraSection)) {
                chosen = candidate;
                break;
            }
        }
        // the nearest one that has not been used in this frame
        if (chosen == null) {
            chosen = candidates.stream()
                .filter(c -> c.lastUsedFrame != frameIndex)
                .min(Comparator.comparingDouble(c -> sectionDistance(c.lastCameraSection, cameraSection)))
                .orElse(null);
        }
        // a new one, or the nearest one if there are too many
        if (chosen == null) {
            List<PooledRenderer> extras = extraRenderers.computeIfAbsent(level, k -> new ArrayList<>());
            if (extras.size() < getMaxExtraRenderersPerLevel() || candidates.isEmpty()) {
                chosen = PooledRenderer.createExtra(client, level);
                extras.add(chosen);
                LevelExtractor primaryExtractor = ClientWorldLoader.getLevelExtractor(level.dimension());
                if (primaryExtractor != null) {
                    extraExtractors.computeIfAbsent(primaryExtractor, k -> new ArrayList<>()).add(chosen.extractor);
                }
            }
            else {
                chosen = candidates.stream()
                    .min(Comparator.comparingDouble(c -> sectionDistance(c.lastCameraSection, cameraSection)))
                    .orElseThrow();
            }
        }

        view.renderer = chosen;
        if (usesMainRenderer) {
            // the main view renders after the portal views and keeps its own camera
            view.sharesRenderer = true;
        }
        else if (chosen.lastUsedFrame == frameIndex && !cameraSection.equals(chosen.lastCameraSection)) {
            // keep the section grid of the view that uses it first
            view.sharesRenderer = true;
        }
        else {
            chosen.lastCameraSection = cameraSection;
        }
        chosen.lastUsedFrame = frameIndex;
    }

    private static double sectionDistance(@Nullable SectionPos a, SectionPos b) {
        if (a == null) {
            return Double.MAX_VALUE / 2;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void ensureRendererSize(LevelRenderer renderer, int width, int height) {
        long size = ((long) width << 32) | (height & 0xFFFFFFFFL);
        Long oldSize = rendererSizes.put(renderer, size);
        if (oldSize == null || oldSize != size) {
            renderer.resize(width, height);
        }
    }

    private static void renderView(
        Minecraft client, ViewNode view, DeltaTracker deltaTracker, Camera mainCamera
    ) {
        Portal portal = view.portal;
        ViewSlot slot = view.slot;
        assert portal != null && slot != null;
        ClientLevel destWorld = view.level;
        Camera camera = view.camera;
        Vec3 cameraPos = view.cameraPos;

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

        float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

        RenderTarget oldMainTarget = gameRenderer.mainRenderTarget;
        Camera oldMainCamera = gameRenderer.mainCamera;
        Lightmap oldLightmap = gameRenderer.lightmap;
        FogRenderer oldFogRenderer = gameRenderer.fogRenderer;
        ClientLevel oldLevel = client.level;
        ViewNode oldRenderingView = renderingView;

        List<Portal> portalChain = new ArrayList<>();
        for (ViewNode node = view; node != null && node.portal != null; node = node.parent) {
            portalChain.add(0, node.portal);
        }

        renderingView = view;
        isSharingRenderer = view.sharesRenderer;
        portalChain.forEach(PortalRendering::pushPortalLayer);
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
        // Sodium takes the terrain fog from the game renderer's fog renderer
        gameRenderer.fogRenderer = fogRenderer;
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
                        ieGameRenderer, cameraState, portal, cameraPos, view.flipped
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
                    // the main view sets it again in GameRenderer.renderLevel
                    SodiumInterface.invoker.setTerrainProjectionMatrix(projectionMatrix);
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

            Runnable renderWithPooledRenderer = () -> {
                PooledRenderer pooled = view.renderer;
                assert pooled != null;
                LevelRenderer switchedRenderer = client.levelRenderer;
                LevelExtractor switchedExtractor = client.levelExtractor;
                client.levelRenderer = pooled.renderer;
                client.levelExtractor = pooled.extractor;
                try {
                    if (pooled.isExtra) {
                        // the chunk update tracking sets are consumed by the level extractor
                        ImmPtlClientChunkMap.withTrackingConsumer(pooled.extractor, renderTask);
                    }
                    else {
                        renderTask.run();
                    }
                }
                finally {
                    client.levelRenderer = switchedRenderer;
                    client.levelExtractor = switchedExtractor;
                }
            };

            if (destWorld == oldLevel) {
                renderWithPooledRenderer.run();
            }
            else {
                ClientWorldLoader.withSwitchedWorld(destWorld, renderWithPooledRenderer);
            }
        }
        finally {
            gameRenderer.mainRenderTarget = oldMainTarget;
            gameRenderer.mainCamera = oldMainCamera;
            gameRenderer.lightmap = oldLightmap;
            gameRenderer.fogRenderer = oldFogRenderer;
            gameRenderer.lighting().updateLevel(oldLevel.dimensionType().cardinalLightType());
            WorldRenderInfo.popRenderInfo();
            portalChain.forEach(p -> PortalRendering.popPortalLayer());
            renderingView = oldRenderingView;
            isSharingRenderer = false;
        }
    }

    /**
     * The projection matrix of the main view (with view bobbing),
     * with the near plane replaced by the destination portal plane,
     * so that the things between the camera and the portal's destination are clipped.
     */
    private static Matrix4f getProjectionMatrix(
        IEGameRenderer_PortalView gameRenderer, CameraRenderState cameraState,
        Portal portal, Vec3 cameraPos, boolean flipped
    ) {
        Matrix4f projection = new Matrix4f(cameraState.projectionMatrix);
        PoseStack bobStack = new PoseStack();
        gameRenderer.ip_bobHurt(cameraState, bobStack);
        if (Minecraft.getInstance().options.bobView().get()) {
            gameRenderer.ip_bobView(cameraState, bobStack);
        }
        projection.mul(bobStack.last().pose());

        if (flipped) {
            // The camera's horizontal axis is negated relative to the view,
            // and the image is flipped back when sampled. Negate the x of both sides
            // so that the image lines up with the screen (the view bobbing is not symmetric).
            Matrix4f flipX = new Matrix4f().scaling(-1, 1, 1);
            projection = new Matrix4f(flipX).mul(projection).mul(flipX);
        }

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

    // the global portals are not in the level's entity list
    public static void extractGlobalPortals(
        ClientLevel level, EntityRenderDispatcher entityRenderDispatcher,
        LevelRenderState levelRenderState, float partialTicks
    ) {
        ViewNode view = renderingView;
        if (view == null || view.children.isEmpty()) {
            return;
        }
        for (Portal portal : GlobalPortalStorage.getGlobalPortals(level)) {
            if (view.children.containsKey(portal)) {
                levelRenderState.entityRenderStates.add(
                    entityRenderDispatcher.extractEntity(portal, partialTicks)
                );
            }
        }
    }

    public static boolean shouldRenderPortalEntity(Portal portal, Frustum frustum) {
        return frustum.isVisible(portal.getBoundingBox());
    }

    /**
     * Called when the portal entity is submitted, in the main view or in a portal view.
     */
    public static void submitPortalSurface(
        Portal portal, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        ViewNode view = renderingView;
        if (view == null) {
            return;
        }
        ViewNode child = view.children.get(portal);
        if (child == null || child.slot == null) {
            return;
        }
        RenderType renderType = child.isFlippedRelativeToParent() ?
            child.slot.flippedRenderType : child.slot.renderType;

        // the pose stack is translated to the portal entity position
        submitNodeCollector.submitCustomGeometry(
            poseStack, renderType,
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
