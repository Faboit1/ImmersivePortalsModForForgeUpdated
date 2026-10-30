package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
import qouteall.imm_ptl.core.IPModMain;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.commands.AxisArgumentType;
import qouteall.imm_ptl.core.commands.SubCommandArgumentType;
import qouteall.imm_ptl.core.commands.TimingFunctionArgumentType;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.peripheral.platform_specific.PeripheralModEntry;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscUtilModEntry;

/**
 * The Forge entry point. Immersive Portals core, the peripheral part and q_misc_util
 * are all initialized from here (they are three separate mods on Fabric).
 */
@Mod(IPModEntry.MODID)
public class IPModEntry {
    
    public static final String MODID = "immersive_portals";
    
    public IPModEntry(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        boolean isClient = FMLEnvironment.dist.isClient();
        
        MiscUtilModEntry.onInitialize();
        
        RegisterEvent.getBus(modBus).addListener(registerEvent -> {
            registerEvent.register(Registries.ENTITY_TYPE, IPModMain::registerEntityTypes);
            registerEvent.register(Registries.TICKET_TYPE, helper -> helper.register(
                McHelper.newIdentifier("immersive_portals", "portal_loading"),
                ImmPtlChunkTickets.TICKET_TYPE
            ));
        });
        
        IPModMain.init(modBus);
        
        SubCommandArgumentType.init(modBus);
        TimingFunctionArgumentType.init(modBus);
        AxisArgumentType.init(modBus);
        
        FMLDedicatedServerSetupEvent.getBus(modBus).addListener(
            event -> new IPModEntryDedicatedServer().onInitializeServer()
        );
        
        if (isClient) {
            EntityRenderersEvent.RegisterRenderers.BUS.addListener(IPModEntryClient::initPortalRenderers);
            new IPModEntryClient().onInitializeClient(modBus);
        }
        
        PeripheralModEntry.init(modBus, isClient);
        
        if (ModList.isLoaded("dimthread")) {
            O_O.isDimensionalThreadingPresent = true;
            Helper.log("Dimensional Threading is present");
        }
        
        if (ModList.isLoaded("gravity_changer_q")) {
            GravityChangerInterface.invoker = new GravityChangerInterface.OnGravityChangerPresent();
            Helper.log("Gravity API is present");
        }
    }
}
