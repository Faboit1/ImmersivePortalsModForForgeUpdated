package qouteall.q_misc_util;

import qouteall.q_misc_util.dimension.DimensionIntId;

/**
 * q_misc_util is initialized as a part of the Immersive Portals mod on NeoForge.
 */
public class MiscUtilModEntry {
    public static final String MOD_ID = "q_misc_util";
    
    public static void onInitialize() {
        DimensionIntId.init();
    }
}
