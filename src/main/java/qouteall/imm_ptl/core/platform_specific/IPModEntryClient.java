package qouteall.imm_ptl.core.platform_specific;

import qouteall.imm_ptl.core.render.ImmPtlDebugScreenEntry;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.client.event.EntityRenderersEvent;
import qouteall.imm_ptl.core.IPModMainClient;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;
import qouteall.imm_ptl.core.portal.BreakableMirror;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.render.LoadingIndicatorRenderer;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;

import java.util.Arrays;

public class IPModEntryClient {
    


    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void initPortalRenderers(EntityRenderersEvent.RegisterRenderers event) {
        
        Arrays.stream(new EntityType<?>[]{
            Portal.ENTITY_TYPE,
            NetherPortalEntity.ENTITY_TYPE,
            EndPortalEntity.ENTITY_TYPE,
            Mirror.ENTITY_TYPE,
            BreakableMirror.ENTITY_TYPE,
            GlobalTrackedPortal.ENTITY_TYPE,
            WorldWrappingPortal.ENTITY_TYPE,
            VerticalConnectingPortal.ENTITY_TYPE,
            GeneralBreakablePortal.ENTITY_TYPE
        }).forEach(
            entityType -> event.registerEntityRenderer(
                entityType,
                (EntityRendererProvider) PortalEntityRenderer::new
            )
        );
        
        event.registerEntityRenderer(
            LoadingIndicatorEntity.entityType,
            LoadingIndicatorRenderer::new
        );
        
    }

    public void onInitializeClient(BusGroup modBus) {
        IPModMainClient.init();
        
        ImmPtlDebugScreenEntry.init();
        
        IPModInfoChecking.initClient();
    }
    
}
