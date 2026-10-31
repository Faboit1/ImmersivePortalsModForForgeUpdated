package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

@Mixin(ThrownEnderpearl.class)
public class MixinThrownEnderPearl {
    // vanilla changes the player dimension in the conventional way (with a loading screen),
    // use the seamless teleportation instead
    @WrapOperation(
        method = "onHit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"
        )
    )
    private ServerPlayer wrapTeleportOwner(
        ServerPlayer player, TeleportTransition transition, Operation<ServerPlayer> original
    ) {
        if (player.level() != transition.newLevel() && !player.isSleeping()) {
            Helper.log("Doing cross-dimensional ender pearl teleportation");
            ServerTeleportationManager.teleportEntityGeneral(
                player, transition.position(), transition.newLevel()
            );
            return player;
        }
        
        return original.call(player, transition);
    }
}
