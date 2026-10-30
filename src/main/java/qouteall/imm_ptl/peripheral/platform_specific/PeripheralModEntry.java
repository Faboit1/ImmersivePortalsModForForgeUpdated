package qouteall.imm_ptl.peripheral.platform_specific;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegisterEvent;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

/**
 * The peripheral part (dimension stack, alternate dimensions, portal wand, vanilla portal integration).
 * Initialized by {@link qouteall.imm_ptl.core.platform_specific.IPModEntry}.
 */
public class PeripheralModEntry {
    public static final String MODID = "imm_ptl";
    
    public static void init(BusGroup modBus, boolean isClient) {
        PeripheralModMain.registerDeferred(modBus);
        
        RegisterEvent.getBus(modBus).addListener(registerEvent -> {
            registerEvent.register(Registries.CHUNK_GENERATOR, helper ->
                PeripheralModMain.registerChunkGenerators(helper::register));
            registerEvent.register(Registries.BIOME_SOURCE, helper ->
                PeripheralModMain.registerBiomeSources(helper::register));
        });
        
        PeripheralModMain.init();
        
        if (isClient) {
            new PeripheralModEntryClient().onInitializeClient();
        }
    }
}
