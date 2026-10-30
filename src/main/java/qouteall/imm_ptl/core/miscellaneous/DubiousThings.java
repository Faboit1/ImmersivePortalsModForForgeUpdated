package qouteall.imm_ptl.core.miscellaneous;

import qouteall.q_misc_util.event.IPEventBus;
import qouteall.imm_ptl.core.IPGlobal;

public class DubiousThings {
    public static void init() {
        IPEventBus.EVENT_BUS.addListener(IPGlobal.PostClientTickEvent.class, postClientTickEvent -> DubiousThings.tick());
    }
    
    private static void tick() {
        // things removed
    }
    
}
