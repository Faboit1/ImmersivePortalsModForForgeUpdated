package qouteall.imm_ptl.peripheral.platform_specific;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

/**
 * The peripheral part (dimension stack, alternate dimensions, portal wand, vanilla portal integration).
 * Initialized by {@link qouteall.imm_ptl.core.platform_specific.IPModEntry}.
 */
public class PeripheralModEntry {
    public static final String MODID = "imm_ptl";
    
    public static void init(IEventBus modBus) {
        PeripheralModMain.registerDeferred(modBus);
        
        modBus.addListener(RegisterEvent.class, registerEvent -> {
            registerEvent.register(Registries.CHUNK_GENERATOR, helper ->
                PeripheralModMain.registerChunkGenerators(helper::register));
            registerEvent.register(Registries.BIOME_SOURCE, helper ->
                PeripheralModMain.registerBiomeSources(helper::register));
        });
        
        PeripheralModMain.init();
    }
}
