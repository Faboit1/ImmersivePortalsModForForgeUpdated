package qouteall.imm_ptl.core;

import qouteall.imm_ptl.core.render.PortalViewRendering;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import de.nick1st.imm_ptl.events.ClientCleanupEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import qouteall.q_misc_util.event.IPEventBus;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.commands.ClientDebugCommand;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.miscellaneous.DubiousThings;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.my_util.MyTaskList;

public class IPModMainClient {
    
    
    
    public static void init() {
        ClientWorldLoader.init();
        
        ClientTeleportationManager.init();
        
        PortalViewRendering.init();
        
        DubiousThings.init();
        
        
        
        CollisionHelper.initClient();
        
        PortalRenderInfo.init();
        
        
        
        GcMonitor.initClient();

        RegisterClientCommandsEvent.BUS.addListener(event -> {
            ClientDebugCommand.register(event.getDispatcher());
        });
        
//        showIntelVideoCardWarning();
        
        
        
        StableClientTimer.init();
        
        ClientPortalAnimationManagement.init();
        
        
        
    

        ImmPtlNetworkConfig.initClient();
        

        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, e -> {
            IPGlobal.CLIENT_TASK_LIST.forceClearTasks();
        });

        DimensionIntId.initClient();
    }
    
}
