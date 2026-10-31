package qouteall.imm_ptl.core.mixin.common.container_gui;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ContainerOpenersCounter.class)
public abstract class MixinContainerOpenersCounter {
    // the container could be opened via portal. the player could be anywhere in any dimension
    // so the players are checked in addition to the nearby entities
    @Inject(method = "getEntitiesWithContainerOpen", at = @At("RETURN"), cancellable = true)
    private void onGetEntitiesWithContainerOpen(
        Level level, BlockPos pos, CallbackInfoReturnable<List<ContainerUser>> cir
    ) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        
        List<ContainerUser> result = null;
        ContainerOpenersCounter this_ = (ContainerOpenersCounter) (Object) this;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (cir.getReturnValue().contains(player)) {
                continue;
            }
            if (player.hasContainerOpen(this_, pos)) {
                if (result == null) {
                    result = new ArrayList<>(cir.getReturnValue());
                }
                result.add(player);
            }
        }
        
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
