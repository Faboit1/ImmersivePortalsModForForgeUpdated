package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.core.registries.Registries;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
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
 * The NeoForge entry point. Immersive Portals core, the peripheral part and q_misc_util
 * are all initialized from here (they are three separate mods on Fabric).
 */
@Mod(IPModEntry.MODID)
public class IPModEntry {
    
    public static final String MODID = "immersive_portals";
    
    public IPModEntry(IEventBus modBus, ModContainer modContainer) {
        boolean isClient = FMLEnvironment.getDist().isClient();
        
        MiscUtilModEntry.onInitialize();
        
        modBus.addListener(RegisterEvent.class, registerEvent -> {
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
        
        modBus.addListener(
            FMLDedicatedServerSetupEvent.class,
            event -> new IPModEntryDedicatedServer().onInitializeServer()
        );
        
        PeripheralModEntry.init(modBus);
        
        if (isClient) {
            IPModEntryClient.registerModBusListeners(modBus);
            // NeoForge constructs the mods before the Minecraft instance exists
            modBus.addListener(
                FMLClientSetupEvent.class,
                event -> event.enqueueWork(() -> new IPModEntryClient().onInitializeClient())
            );
        }
        
        if (ModList.get().isLoaded("dimthread")) {
            O_O.isDimensionalThreadingPresent = true;
            Helper.log("Dimensional Threading is present");
        }
        
        if (ModList.get().isLoaded("gravity_changer_q")) {
            GravityChangerInterface.invoker = new GravityChangerInterface.OnGravityChangerPresent();
            Helper.log("Gravity API is present");
        }
    }
}
