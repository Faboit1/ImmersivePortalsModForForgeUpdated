package qouteall.imm_ptl.core;


//@OnlyIn(Dist.CLIENT)
public class IPCGlobal {
    
    public static int maxIdleChunkRendererNum = 500;
    
    public static boolean doUseAdvancedFrustumCulling = true;
    public static boolean useHackedChunkRenderDispatcher = true;
    public static boolean isClientRemoteTickingEnabled = true;
    public static boolean useFrontClipping = true;
    public static boolean doDisableAlphaTestWhenRenderingFrameBuffer = true;
    public static boolean lateClientLightUpdate = true;
    public static boolean earlyRemoteUpload = true;
    
    public static boolean useSuperAdvancedFrustumCulling = true;
    public static boolean earlyFrustumCullingPortal = true;
    
    public static boolean useSeparatedStencilFormat = false;
    
    public static boolean experimentalIrisPortalRenderer = false;
    
    public static boolean debugEnableStencilWithIris = false;
}
