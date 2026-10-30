package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkCache implements IEServerChunkCache {
    @Shadow
    @Final
    private DistanceManager distanceManager;
    
    @Override
    public DistanceManager ip_getDistanceManager() {
        return distanceManager;
    }
    
    @Shadow
    @Final
    private net.minecraft.world.level.TicketStorage ticketStorage;
    
    @Override
    public net.minecraft.world.level.TicketStorage ip_getTicketStorage() {
        return ticketStorage;
    }
}
