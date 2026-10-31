package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import qouteall.imm_ptl.core.render.FrustumCuller;

//@OnlyIn(Dist.CLIENT)
public class SodiumInterface {

    @Nullable
    public static FrustumCuller frustumCuller = null;

    public static class Invoker {
        public boolean isSodiumPresent() {
            return false;
        }

        public Object createNewContext(int renderDistance) {
            return null;
        }

        public void switchContextWithCurrentWorldRenderer(Object context) {

        }

        public void markSpriteActive(TextureAtlasSprite sprite) {

        }

        /**
         * Called after a chunk's block data is loaded on the client.
         */
        public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {

        }

        public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {

        }

        /**
         * Sets the projection matrix that Sodium draws the terrain with.
         * Sodium takes it from the game renderer instead of {@link com.mojang.blaze3d.systems.RenderSystem}.
         */
        public void setTerrainProjectionMatrix(Matrix4fc projectionMatrix) {

        }
    }

    public static Invoker invoker = new Invoker();

    /**
     * Installs the Sodium support when Sodium is loaded.
     */
    public static void init(boolean sodiumLoaded) {
        if (!sodiumLoaded) {
            return;
        }
        SodiumInterfaceReflection reflection = SodiumInterfaceReflection.create();
        if (reflection != null) {
            invoker = reflection;
        }
    }

}
