package qouteall.imm_ptl.core;

import net.minecraftforge.event.RegisterCommandsEvent;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import qouteall.q_misc_util.event.IPEventBus;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.core.chunk_loading.EntitySync;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.ServerPerformanceMonitor;
import qouteall.imm_ptl.core.chunk_loading.WorldInfoSender;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.debug.DebugUtil;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.BreakableMirror;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.RotationAnimation;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.imm_ptl.peripheral.platform_specific.IPFeatureControl;
import qouteall.q_misc_util.Helper;

import java.io.File;
import java.nio.file.Path;

public class IPModMain {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void init(BusGroup eventBus) {
        loadConfig();
        
        Helper.LOGGER.info("Immersive Portals Mod Initializing");

        ImmPtlNetworkConfig.init();

        IPEventBus.EVENT_BUS.addListener(IPGlobal.PostClientTickEvent.class, postClientTickEvent -> IPGlobal.CLIENT_TASK_LIST.processTasks());

        IPEventBus.EVENT_BUS.addListener(IPGlobal.PreGameRenderEvent.class, preGameRenderEvent -> IPGlobal.PRE_GAME_RENDER_TASK_LIST.processTasks());

        BLOCKS.register(eventBus);

        RectangularPortalShape.init();
        SpecialFlatPortalShape.init();
        BoxPortalShape.init();
        
        ImmPtlChunkTracking.init();
        
        WorldInfoSender.init();
        
        GlobalPortalStorage.init();
        
        EntitySync.init();
        
        ServerTeleportationManager.init();
        
        CollisionHelper.init();
        
        PortalExtension.init();
        
        GcMonitor.initCommon();
        
        ServerPerformanceMonitor.init();
        
        ImmPtlChunkTickets.init();
        
        
        BlockManipulationServer.init();

        RegisterCommandsEvent.BUS.addListener(event -> {
            PortalCommand.register(event.getDispatcher(), event.getBuildContext());
        });

        // @Nick1st moved to IPModEntry (those are registry functions)
//        SubCommandArgumentType.init();
//        TimingFunctionArgumentType.init();
//        AxisArgumentType.init();

        DebugUtil.init();
        
        ServerTaskList.init();

        CustomPortalGenManager.init(eventBus);

        // intrinsic animation driver types
        RotationAnimation.init();
        NormalAnimation.init();
//        OscillationAnimation.init();

        if (!IPFeatureControl.enableVanillaBehaviorChangingByDefault()) {
            LOGGER.info("""
                iPortal is provided by jar-in-jar.
                The default value of 'nether portal mode', 'end portal mode' and 'enable mirror creation' in config will be respectively vanilla, vanilla and false.
                (The default value is only used when config file is not present of missing fields. This does not change existing config.)
                """);
        }
    }
    
    private static void loadConfig() {
        // upgrade old config
        Path gameDir = O_O.getGameDir();
        File oldConfigFile = gameDir.resolve("config").resolve("immersive_portals_fabric.json").toFile();
        if (oldConfigFile.exists()) {
            File dest = gameDir.resolve("config").resolve("immersive_portals.json").toFile();
            boolean succeeded = oldConfigFile.renameTo(dest);
            if (succeeded) {
                Helper.log("Upgraded old config file");
            }
            else {
                Helper.err("Failed to upgrade old config file");
            }
        }
        
        Helper.log("Loading Immersive Portals config");
        IPConfig.load();
    }

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, "immersive_portals");
    
    public static final RegistryObject<PortalPlaceholderBlock> NETHER_PORTAL_BLOCK = BLOCKS.register(
        "nether_portal_block",
        () -> new PortalPlaceholderBlock(
            BlockBehaviour.Properties.of()
                .setId(BLOCKS.key("nether_portal_block"))
                .noCollision()
                .sound(SoundType.GLASS)
                .strength(1.0f, 0)
                .noOcclusion()
                .noLootTable()
                .lightLevel(s -> 15)
        )
    );
    
    /**
     * Entity types must be created when the registries are unfrozen,
     * so they are created here instead of in static initializers.
     */
    public static void registerEntityTypes(RegisterEvent.RegisterHelper<EntityType<?>> helper) {
        Portal.ENTITY_TYPE = register(helper, "portal", Portal.createPortalEntityType("portal", Portal::new));
        NetherPortalEntity.ENTITY_TYPE = register(helper, "nether_portal_new",
            Portal.createPortalEntityType("nether_portal_new", NetherPortalEntity::new));
        EndPortalEntity.ENTITY_TYPE = register(helper, "end_portal",
            Portal.createPortalEntityType("end_portal", EndPortalEntity::new));
        Mirror.ENTITY_TYPE = register(helper, "mirror",
            Portal.createPortalEntityType("mirror", Mirror::new));
        BreakableMirror.ENTITY_TYPE = register(helper, "breakable_mirror",
            Portal.createPortalEntityType("breakable_mirror", BreakableMirror::new));
        GlobalTrackedPortal.ENTITY_TYPE = register(helper, "global_tracked_portal",
            Portal.createPortalEntityType("global_tracked_portal", GlobalTrackedPortal::new));
        WorldWrappingPortal.ENTITY_TYPE = register(helper, "border_portal",
            Portal.createPortalEntityType("border_portal", WorldWrappingPortal::new));
        VerticalConnectingPortal.ENTITY_TYPE = register(helper, "end_floor_portal",
            Portal.createPortalEntityType("end_floor_portal", VerticalConnectingPortal::new));
        GeneralBreakablePortal.ENTITY_TYPE = register(helper, "general_breakable_portal",
            Portal.createPortalEntityType("general_breakable_portal", GeneralBreakablePortal::new));
        LoadingIndicatorEntity.entityType = register(helper, "loading_indicator",
            LoadingIndicatorEntity.createEntityType());
    }
    
    private static <T extends EntityType<?>> T register(
        RegisterEvent.RegisterHelper<EntityType<?>> helper, String path, T entityType
    ) {
        helper.register(McHelper.newIdentifier("immersive_portals", path), entityType);
        return entityType;
    }
}
