package qouteall.imm_ptl.peripheral.wand;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import de.nick1st.imm_ptl.events.ClientCleanupEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import qouteall.q_misc_util.event.IPEventBus;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

import java.util.ArrayList;
import java.util.List;

public class PortalWandItem extends Item {
    public static PortalWandItem instance;

    public static final Codec<Mode> MODE_CODEC = Codec.STRING.xmap(Mode::fromStr, Mode::toStr);

    public static final RegistryObject<DataComponentType<Mode>> COMPONENT_TYPE_HOLDER =
        PeripheralModMain.DATA_COMPONENTS.register(
            "portal_wand_data",
            () -> DataComponentType.<Mode>builder().persistent(MODE_CODEC).build()
        );
    
    public static DataComponentType<Mode> componentType() {
        return COMPONENT_TYPE_HOLDER.get();
    }

    public static void init() {
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((PlayerInteractEvent.LeftClickBlock event) ->
            // cannot break block using the wand (returning true cancels the event)
            event.getEntity().getMainHandItem().getItem() == PeripheralModMain.PORTAL_WAND.get()
        );

        IPEventBus.EVENT_BUS.addListener(BlockManipulationServer.CrossPortalInteractionEvent.class, crossPortalInteractionEvent ->
            crossPortalInteractionEvent.setCanDo(crossPortalInteractionEvent.player.getMainHandItem().getItem() != PeripheralModMain.PORTAL_WAND.get()));
    }
    
    public static void initClient() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> {
            if (event.player() != null && event.player().level().isClientSide()) {
                ItemStack itemStack = event.player().getMainHandItem();
                if (itemStack.getItem() == PeripheralModMain.PORTAL_WAND.get()) {
                    updateDisplay(itemStack);
                } else {
                    ClientPortalWandPortalCreation.clearCursorPointing();
                }
                ClientPortalWandPortalDrag.tick();
            }
        });


        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, e -> ClientPortalWandPortalCreation.reset());
        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, e -> ClientPortalWandPortalDrag.reset());
        IPEventBus.EVENT_BUS.addListener(ClientCleanupEvent.class, e -> ClientPortalWandPortalCopy.reset());
    }
    
    public static void addIntoCreativeTag(CreativeModeTab.Output entries) {
        ItemStack w1 = new ItemStack(PeripheralModMain.PORTAL_WAND.get());
        w1.set(componentType(), Mode.CREATE_PORTAL);
        entries.accept(w1);
        
        ItemStack w2 = new ItemStack(PeripheralModMain.PORTAL_WAND.get());
        w2.set(componentType(), Mode.DRAG_PORTAL);
        entries.accept(w2);
        
        ItemStack w3 = new ItemStack(PeripheralModMain.PORTAL_WAND.get());
        w3.set(componentType(), Mode.COPY_PORTAL);
        entries.accept(w3);
    }
    
    public static enum Mode {
        CREATE_PORTAL,
        DRAG_PORTAL,
        COPY_PORTAL;

        public static final Mode FALLBACK = CREATE_PORTAL;

        public static Mode fromTag(CompoundTag tag) {
            String mode = tag.getStringOr("mode", "");

            return fromStr(mode);
        }

        public static Mode fromStr(String mode) {
            return switch (mode) {
                case "create_portal" -> CREATE_PORTAL;
                case "drag_portal" -> DRAG_PORTAL;
                case "copy_portal" -> COPY_PORTAL;
                default -> FALLBACK;
            };
        }
        
        public Mode next() {
            return switch (this) {
                case CREATE_PORTAL -> DRAG_PORTAL;
                case DRAG_PORTAL -> COPY_PORTAL;
                case COPY_PORTAL -> CREATE_PORTAL;
            };
        }
        
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            String modeString = toStr();
            tag.putString("mode", modeString);
            return tag;
        }

        public String toStr() {
            return switch (this) {
                case CREATE_PORTAL -> "create_portal";
                case DRAG_PORTAL -> "drag_portal";
                case COPY_PORTAL -> "copy_portal";
            };
        }
        
        public MutableComponent getText() {
            return switch (this) {
                case CREATE_PORTAL -> Component.translatable("imm_ptl.wand.mode.create_portal");
                case DRAG_PORTAL -> Component.translatable("imm_ptl.wand.mode.drag_portal");
                case COPY_PORTAL -> Component.translatable("imm_ptl.wand.mode.copy_portal");
            };
        }
        
    }

    public PortalWandItem(Properties properties) {
        super(properties);
    }

    // @Nick1st - Moved to client class
//    @OnlyIn(Dist.CLIENT)
//    public static void onClientLeftClick(LocalPlayer player, ItemStack itemStack) {
//        if (player.isShiftKeyDown()) {
//            showSettings(player);
//        }
//        else {
//            Mode mode = Mode.fromTag(itemStack.getOrCreateTag());
//
//            switch (mode) {
//                case CREATE_PORTAL -> {
//                    ClientPortalWandPortalCreation.onLeftClick();
//                }
//                case DRAG_PORTAL -> {
//                    ClientPortalWandPortalDrag.onLeftClick();
//                }
//                case COPY_PORTAL -> {
//                    ClientPortalWandPortalCopy.onLeftClick();
//                }
//            }
//        }
//    }
    
    @Override
    public @NotNull InteractionResult use(@NotNull Level world, Player player, @NotNull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        Mode mode = itemStack.getOrDefault(componentType(), Mode.FALLBACK);
        
        if (player.isShiftKeyDown()) {
            if (!world.isClientSide()) {
                if (!PortalWandInteraction.isDragging(((ServerPlayer) player))) {
                    Mode nextMode = mode.next();
                    itemStack.set(componentType(), nextMode);
                    return new InteractionResult.Success(InteractionResult.SwingSource.NONE, new InteractionResult.ItemContext(true, itemStack));
                }
            }
        }
        
        if (!player.isShiftKeyDown()) {
            if (world.isClientSide()) {
                onUseClient(mode);
            }
        }
        
        return super.use(world, player, hand);
    }
    
    //@OnlyIn(Dist.CLIENT)
    private void onUseClient(Mode mode) {
        switch (mode) {
            case CREATE_PORTAL -> {
                ClientPortalWandPortalCreation.onRightClick();
            }
            case DRAG_PORTAL -> {
                ClientPortalWandPortalDrag.onRightClick();
            }
            case COPY_PORTAL -> {
                ClientPortalWandPortalCopy.onRightClick();
            }
        }
    }
    
    //@OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(
        ItemStack stack, Item.TooltipContext tooltipContext,
        net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
        java.util.function.Consumer<Component> tooltip, TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, tooltipContext, tooltipDisplay, tooltip, tooltipFlag);
        
        tooltip.accept(Component.translatable(
            "imm_ptl.wand.item_desc_1",
            Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage(),
            Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage()
        ));
        tooltip.accept(Component.translatable(
            "imm_ptl.wand.item_desc_2",
            Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage(),
            Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()
        ));
    }
    
    @Override
    public Component getName(ItemStack stack) {
        Mode mode = stack.getOrDefault(componentType(), Mode.FALLBACK);
        
        MutableComponent baseText = Component.translatable("item.immersive_portals.portal_wand");
        
        return baseText
            .append(Component.literal(" : "))
            .append(mode.getText().withStyle(ChatFormatting.GOLD));
    }
    
    public static void showSettings(Player player) {
        player.sendSystemMessage(Component.translatable("imm_ptl.wand.settings_1"));
        player.sendSystemMessage(Component.translatable("imm_ptl.wand.settings_alignment"));
        
        int[] alignments = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 32, 64};
        
        List<MutableComponent> alignmentSettingTexts = new ArrayList<>();
        for (int alignment : alignments) {
            MutableComponent textWithCommand = IPMcHelper.getTextWithCommand(
                Component.literal("1/" + alignment),
                "/imm_ptl_client_debug wand set_cursor_alignment " + alignment
            );
            alignmentSettingTexts.add(textWithCommand);
        }
        
        alignmentSettingTexts.add(IPMcHelper.getTextWithCommand(
            Component.translatable("imm_ptl.wand.no_alignment"),
            "/imm_ptl_client_debug wand set_cursor_alignment 0"
        ));
        
        player.sendSystemMessage(alignmentSettingTexts.stream().reduce(Component.literal(""), (a, b) -> a.append(" ").append(b)));
        
        player.sendSystemMessage(Component.translatable(
            "imm_ptl.wand.settings_2", Minecraft.getInstance().options.keyChat.getTranslatedKeyMessage()
        ));
    }
    
    private static boolean instructionInformed = false;
    
    //@OnlyIn(Dist.CLIENT)
    private static void updateDisplay(ItemStack itemStack) {
        Mode mode = itemStack.getOrDefault(componentType(), Mode.FALLBACK);
        
        switch (mode) {
            case CREATE_PORTAL -> ClientPortalWandPortalCreation.updateDisplay();
            case DRAG_PORTAL -> ClientPortalWandPortalDrag.updateDisplay();
            case COPY_PORTAL -> ClientPortalWandPortalCopy.updateDisplay();
        }
    }
    
    //@OnlyIn(Dist.CLIENT)

    
}
