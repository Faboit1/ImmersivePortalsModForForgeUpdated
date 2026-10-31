package qouteall.imm_ptl.peripheral;

import net.minecraft.resources.ResourceKey;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.peripheral.alternate_dimension.ChaosBiomeSource;
import qouteall.imm_ptl.peripheral.alternate_dimension.ErrorTerrainGenerator;
import qouteall.imm_ptl.peripheral.alternate_dimension.FormulaGenerator;
import qouteall.imm_ptl.peripheral.alternate_dimension.NormalSkylandGenerator;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;
import qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalDrag;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

import java.util.function.BiConsumer;


public class PeripheralModMain {

    // TODO @Nick1st - Rework registry (Best would be at fabrics side)

    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .icon(() -> new ItemStack(PeripheralModMain.PORTAL_WAND.get()))
            .title(Component.translatable("imm_ptl.item_group"))
            .displayItems((enabledFeatures, entries) -> {
                PortalWandItem.addIntoCreativeTag(entries);
                
                CommandStickItem.addIntoCreativeTag(entries);

                entries.accept(PeripheralModMain.PORTAL_HELPER_ITEM.get());
            })
            .build();
    
    //@OnlyIn(Dist.CLIENT)
    public static void initClient() {
        IPOuterClientMisc.initClient();
        
        PortalWandItem.initClient();
        
        ClientPortalWandPortalDrag.init();
    }
    
    public static void init() {
        FormulaGenerator.init();
        
        IntrinsicPortalGeneration.init();
        
        DimStackManagement.init();

        // TODO @Nick1st - DynDim removal
//        AlternateDimensions.init();
//
//        DimensionAPI.suppressExperimentalWarningForNamespace("immersive_portals");

        PortalWandItem.init();
        
        CommandStickItem.init();
        
        PortalWandInteraction.init();
        
        CommandStickItem.registerCommandStickTypes();

    }

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, "immersive_portals");
    
    public static final DeferredHolder<Block, Block> PORTAL_HELPER = BLOCKS.register(
        "portal_helper",
        () -> new Block(
            BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("immersive_portals", "portal_helper")))
                .noOcclusion()
                .isRedstoneConductor((a, b, c) -> false)
        )
    );
    
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, "immersive_portals");
    
    public static final DeferredHolder<Item, Item> PORTAL_HELPER_ITEM = ITEMS.register(
        "portal_helper",
        () -> new BlockItem(
            PORTAL_HELPER.get(),
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("immersive_portals", "portal_helper"))).useBlockDescriptionPrefix()
        )
    );
    
    public static final DeferredHolder<Item, CommandStickItem> COMMAND_STICK = ITEMS.register(
        "command_stick",
        () -> new CommandStickItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("immersive_portals", "command_stick"))))
    );
    
    public static final DeferredHolder<Item, PortalWandItem> PORTAL_WAND = ITEMS.register(
        "portal_wand",
        () -> new PortalWandItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("immersive_portals", "portal_wand"))))
    );
    
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "immersive_portals");
    
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "immersive_portals");
    
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_HOLDER = CREATIVE_TABS.register(
        "general", () -> PeripheralModMain.TAB
    );
    
    public static void registerDeferred(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }


    

    public static void registerChunkGenerators(
        BiConsumer<Identifier, MapCodec<? extends ChunkGenerator>> regFunc
    ) {
        regFunc.accept(
            McHelper.newIdentifier("immersive_portals:error_terrain_generator"),
            ErrorTerrainGenerator.MAP_CODEC
        );
        regFunc.accept(
            McHelper.newIdentifier("immersive_portals:normal_skyland_generator"),
            NormalSkylandGenerator.MAP_CODEC
        );
    }

    public static void registerBiomeSources(
        BiConsumer<Identifier, MapCodec<? extends BiomeSource>> regFunc
    ) {
        regFunc.accept(
            McHelper.newIdentifier("immersive_portals:chaos_biome_source"),
            ChaosBiomeSource.MAP_CODEC
        );
    }

}
