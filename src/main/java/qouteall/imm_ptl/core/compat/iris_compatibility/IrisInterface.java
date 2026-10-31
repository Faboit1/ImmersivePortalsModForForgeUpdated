package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;


public class IrisInterface {

    public static class Invoker {
        public boolean isIrisPresent() {
            return false;
        }

        public boolean isShaders() {
            return false;
        }

        public boolean isRenderingShadowMap() {
            return false;
        }

        public Object getPipeline(LevelRenderer worldRenderer) {
            return null;
        }

        public void setPipeline(LevelRenderer worldRenderer, Object pipeline) {

        }

        public void reloadPipelines() {}

        @Nullable
        public String getShaderpackName() {
            return null;
        }
    }



    public static Invoker invoker = new Invoker();

    /**
     * Installs the Iris support when Iris is loaded.
     */
    public static void init(boolean irisLoaded) {
        if (!irisLoaded) {
            return;
        }
        IrisInterfaceReflection reflection = IrisInterfaceReflection.create();
        if (reflection != null) {
            invoker = reflection;
        }
    }
}
