package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Iris support through Iris's public API, without a compile time dependency on Iris.
 */
public final class IrisInterfaceReflection extends IrisInterface.Invoker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object irisApi;
    // (IrisApi) -> boolean
    private final MethodHandle isShaderPackInUse;
    // (IrisApi) -> boolean
    private final MethodHandle isRenderingShadowPass;

    private boolean reportedError = false;

    private IrisInterfaceReflection(
        Object irisApi, MethodHandle isShaderPackInUse, MethodHandle isRenderingShadowPass
    ) {
        this.irisApi = irisApi;
        this.isShaderPackInUse = isShaderPackInUse;
        this.isRenderingShadowPass = isRenderingShadowPass;
    }

    public static @Nullable IrisInterfaceReflection create() {
        try {
            Class<?> apiClass = Class.forName(
                "net.irisshaders.iris.api.v0.IrisApi", true, IrisInterfaceReflection.class.getClassLoader()
            );
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object api = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass)).invoke();
            MethodType booleanGetter = MethodType.methodType(boolean.class, Object.class);
            MethodHandle isShaderPackInUse = lookup.findVirtual(
                apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class)
            ).asType(booleanGetter);
            MethodHandle isRenderingShadowPass = lookup.findVirtual(
                apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class)
            ).asType(booleanGetter);
            LOGGER.info("[ImmPtl] Iris support enabled");
            return new IrisInterfaceReflection(api, isShaderPackInUse, isRenderingShadowPass);
        }
        catch (Throwable e) {
            LOGGER.error("[ImmPtl] Failed to set up the Iris support", e);
            return null;
        }
    }

    @Override
    public boolean isIrisPresent() {
        return true;
    }

    @Override
    public boolean isShaders() {
        try {
            return (boolean) isShaderPackInUse.invokeExact(irisApi);
        }
        catch (Throwable e) {
            reportError(e);
            return false;
        }
    }

    @Override
    public boolean isRenderingShadowMap() {
        try {
            return (boolean) isRenderingShadowPass.invokeExact(irisApi);
        }
        catch (Throwable e) {
            reportError(e);
            return false;
        }
    }

    private void reportError(Throwable e) {
        if (!reportedError) {
            reportedError = true;
            LOGGER.error("[ImmPtl] Error in the Iris support", e);
        }
    }
}
