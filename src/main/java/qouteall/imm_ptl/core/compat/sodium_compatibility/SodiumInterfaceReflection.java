package qouteall.imm_ptl.core.compat.sodium_compatibility;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Sodium support, without a compile time dependency on Sodium.
 * <p>
 * Sodium only builds the terrain of the chunks that its chunk tracker knows about.
 * The tracker is told about loaded chunks by a mixin into {@link net.minecraft.client.multiplayer.ClientChunkCache#replaceWithPacketData},
 * which {@link qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap} overrides, so the chunk map tells the tracker itself.
 * (Unloads and light data reach the tracker through Sodium's mixins into ClientLevel and ClientPacketListener.)
 */
public final class SodiumInterfaceReflection extends SodiumInterface.Invoker {
    private static final Logger LOGGER = LogUtils.getLogger();

    // net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus.FLAG_HAS_BLOCK_DATA
    private static final int FLAG_HAS_BLOCK_DATA = 1;

    // (ClientLevel) -> ChunkTracker
    private final MethodHandle getChunkTracker;
    // (ChunkTracker, int x, int z, int flags) -> void
    private final MethodHandle onChunkStatusAdded;
    // (GameRenderer) -> Matrix4fc
    private final MethodHandle getProjectionMatrix;

    private boolean reportedError = false;

    private SodiumInterfaceReflection(
        MethodHandle getChunkTracker, MethodHandle onChunkStatusAdded, MethodHandle getProjectionMatrix
    ) {
        this.getChunkTracker = getChunkTracker;
        this.onChunkStatusAdded = onChunkStatusAdded;
        this.getProjectionMatrix = getProjectionMatrix;
    }

    public static @Nullable SodiumInterfaceReflection create() {
        try {
            ClassLoader classLoader = SodiumInterfaceReflection.class.getClassLoader();
            Class<?> holderClass = Class.forName(
                "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder", false, classLoader
            );
            Class<?> trackerClass = Class.forName(
                "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", false, classLoader
            );
            Class<?> gameRendererStorageClass = Class.forName(
                "net.caffeinemc.mods.sodium.client.util.GameRendererStorage", false, classLoader
            );

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getChunkTracker = lookup.findStatic(
                holderClass, "get", MethodType.methodType(trackerClass, ClientLevel.class)
            ).asType(MethodType.methodType(Object.class, ClientLevel.class));
            MethodHandle onChunkStatusAdded = lookup.findVirtual(
                trackerClass, "onChunkStatusAdded",
                MethodType.methodType(void.class, int.class, int.class, int.class)
            ).asType(MethodType.methodType(void.class, Object.class, int.class, int.class, int.class));
            MethodHandle getProjectionMatrix = lookup.findVirtual(
                gameRendererStorageClass, "sodium$getProjectionMatrix", MethodType.methodType(Matrix4fc.class)
            ).asType(MethodType.methodType(Matrix4fc.class, Object.class));

            LOGGER.info("[ImmPtl] Sodium support enabled");
            return new SodiumInterfaceReflection(getChunkTracker, onChunkStatusAdded, getProjectionMatrix);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error(
                "[ImmPtl] Failed to set up the Sodium support. This Sodium version is not supported, the world may not render.",
                e
            );
            return null;
        }
    }

    @Override
    public boolean isSodiumPresent() {
        return true;
    }

    @Override
    public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
        try {
            Object tracker = (Object) getChunkTracker.invokeExact(world);
            onChunkStatusAdded.invokeExact(tracker, chunkX, chunkZ, FLAG_HAS_BLOCK_DATA);
        }
        catch (Throwable e) {
            reportError(e);
        }
    }

    @Override
    public void setTerrainProjectionMatrix(Matrix4fc projectionMatrix) {
        try {
            Matrix4fc stored = (Matrix4fc) getProjectionMatrix.invokeExact((Object) Minecraft.getInstance().gameRenderer);
            // it is the matrix object that Sodium keeps, not a copy
            if (stored instanceof Matrix4f mutable) {
                mutable.set(projectionMatrix);
            }
        }
        catch (Throwable e) {
            reportError(e);
        }
    }

    private void reportError(Throwable e) {
        if (!reportedError) {
            reportedError = true;
            LOGGER.error("[ImmPtl] Error in the Sodium support", e);
        }
    }
}
