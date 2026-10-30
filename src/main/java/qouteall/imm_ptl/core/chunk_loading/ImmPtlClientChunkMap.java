package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.my_util.SignalArged;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Vanilla use a 2D array to store the chunk references on client and cannot store the chunks that are far from player.
 * This use map to store the chunk references, to eliminate such limitation.
 * (Two maps, one for main thread and one for other threads)
 */
//@OnlyIn(Dist.CLIENT)
@IPVanillaCopy
public class ImmPtlClientChunkMap extends ClientChunkCache {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // the most chunk accesses are from the main thread,
    // so we use two maps to reduce synchronization.
    // the main thread accesses this map, without synchronization
    protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForMainThread =
        new Long2ObjectOpenHashMap<>();
    // other threads read this map, with synchronization
    protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForOtherThreads =
        new Long2ObjectOpenHashMap<>();
    
    public final Thread mainThread;
    
    public static final SignalArged<LevelChunk> clientChunkLoadSignal = new SignalArged<>();
    public static final SignalArged<LevelChunk> clientChunkUnloadSignal = new SignalArged<>();
    
    /**
     * In Minecraft 26 the renderer learns about loaded chunks and empty sections
     * from these sets (see {@link net.minecraft.client.renderer.extract.LevelExtractor}).
     * Vanilla stores them in its private storage, so they are maintained here.
     * Double-buffered: one is being filled while the other is consumed by the renderer.
     */
    private final LongOpenHashSet[] addedEmptySectionsSets = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] removedEmptySectionsSets = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] addedLoadedChunksSets = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] removedLoadedChunksSets = {new LongOpenHashSet(), new LongOpenHashSet()};
    private int updatingSetsIndex = 0;
    
    public ImmPtlClientChunkMap(ClientLevel clientWorld, int loadDistance) {
        super(clientWorld, 1);
        // the chunk array is unused. make it small by passing 1 as load distance to super constructor
        
        mainThread = ((IEMinecraftClient) Minecraft.getInstance()).ip_getRunningThread();
    }
    
    private void onChunkAdded(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        addedLoadedChunksSets[updatingSetsIndex].add(chunkPos.pack());
        removedLoadedChunksSets[updatingSetsIndex].remove(chunkPos.pack());
        refreshEmptySections(chunk);
    }
    
    private void onChunkRemoved(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        removedLoadedChunksSets[updatingSetsIndex].add(chunkPos.pack());
        addedLoadedChunksSets[updatingSetsIndex].remove(chunkPos.pack());
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            long sectionNode = SectionPos.asLong(
                chunkPos.x(), chunk.getSectionYFromSectionIndex(sectionIndex), chunkPos.z()
            );
            removedEmptySectionsSets[updatingSetsIndex].add(sectionNode);
            addedEmptySectionsSets[updatingSetsIndex].remove(sectionNode);
        }
    }
    
    private void refreshEmptySections(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            long sectionNode = SectionPos.asLong(
                chunkPos.x(), chunk.getSectionYFromSectionIndex(sectionIndex), chunkPos.z()
            );
            if (sections[sectionIndex].hasOnlyAir()) {
                addedEmptySectionsSets[updatingSetsIndex].add(sectionNode);
                removedEmptySectionsSets[updatingSetsIndex].remove(sectionNode);
            }
            else {
                removedEmptySectionsSets[updatingSetsIndex].add(sectionNode);
                addedEmptySectionsSets[updatingSetsIndex].remove(sectionNode);
            }
        }
    }
    
    @Override
    public LongOpenHashSet addedEmptySections() {
        return addedEmptySectionsSets[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet removedEmptySections() {
        return removedEmptySectionsSets[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet addedLoadedChunks() {
        return addedLoadedChunksSets[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet removedLoadedChunks() {
        return removedLoadedChunksSets[updatingSetsIndex];
    }
    
    @Override
    public void flipUpdateTrackingSets() {
        updatingSetsIndex = (updatingSetsIndex + 1) % 2;
        addedEmptySectionsSets[updatingSetsIndex].clear();
        removedEmptySectionsSets[updatingSetsIndex].clear();
        addedLoadedChunksSets[updatingSetsIndex].clear();
        removedLoadedChunksSets[updatingSetsIndex].clear();
    }
    
    @Override
    public void onSectionEmptinessChanged(int sectionX, int sectionY, int sectionZ, boolean empty) {
        long sectionNode = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (empty) {
            addedEmptySectionsSets[updatingSetsIndex].add(sectionNode);
            removedEmptySectionsSets[updatingSetsIndex].remove(sectionNode);
        }
        else {
            removedEmptySectionsSets[updatingSetsIndex].add(sectionNode);
            addedEmptySectionsSets[updatingSetsIndex].remove(sectionNode);
        }
    }
    
    @Override
    public void drop(ChunkPos chunkPos) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        
//        LOGGER.info("unload {} {}", level, chunkPos);
        
        LevelChunk chunk = chunkMapForMainThread.get(chunkPos.pack());
        if (chunk != null) {
            modifyChunkMap(chunkMap -> {
                chunkMap.remove(chunkPos.pack());
            });
            
            onChunkRemoved(chunk);
            O_O.postClientChunkUnloadEvent(chunk);
            this.level.unload(chunk);
            clientChunkUnloadSignal.emit(chunk);
        }
    }
    
    public <T> T readChunkMap(Function<Long2ObjectOpenHashMap<LevelChunk>, T> func) {
        if (Thread.currentThread() == mainThread) {
            return func.apply(chunkMapForMainThread);
        }
        else {
            synchronized (chunkMapForOtherThreads) {
                return func.apply(chunkMapForOtherThreads);
            }
        }
    }
    
    public void modifyChunkMap(Consumer<Long2ObjectOpenHashMap<LevelChunk>> func) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        func.accept(chunkMapForMainThread);
        synchronized (chunkMapForOtherThreads) {
            func.accept(chunkMapForOtherThreads);
        }
    }
    
    @Override
    public LevelChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean create) {
        return readChunkMap(chunkMap -> {
            LevelChunk chunk = chunkMap.get(ChunkPos.pack(x, z));
            if (chunk != null) {
                return chunk;
            }
            
            return create ? this.emptyChunk : null;
        });
    }
    
    public boolean isChunkLoaded(int x, int z) {
        return readChunkMap(chunkMap -> {
            return chunkMap.containsKey(ChunkPos.pack(x, z));
        });
    }
    
    @Override
    public void replaceBiomes(int x, int z, FriendlyByteBuf friendlyByteBuf) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        
        long chunkPosLong = ChunkPos.pack(x, z);
        
        LevelChunk worldChunk = chunkMapForMainThread.get(chunkPosLong);
        ChunkPos chunkPos = new ChunkPos(x, z);
        if (worldChunk == null) {
            LOGGER.error("Trying to replace biomes for missing chunk {} {}", x, z);
        }
        else {
            worldChunk.replaceBiomes(friendlyByteBuf);
        }
    }
    
    @Override
    public LevelChunk replaceWithPacketData(
        int x, int z,
        FriendlyByteBuf buf, Map<Heightmap.Types, long[]> nbt,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        
        long chunkPosLong = ChunkPos.pack(x, z);
        LevelChunk worldChunk = chunkMapForMainThread.get(chunkPosLong);
        if (worldChunk == null) {
            worldChunk = new LevelChunk(this.level, new ChunkPos(x, z));
            loadChunkDataFromPacket(buf, nbt, worldChunk, consumer);
            
            LevelChunk worldChunkToPut = worldChunk; // lambda can only capture effectively final variables
            modifyChunkMap(chunkMap -> {
                chunkMap.put(chunkPosLong, worldChunkToPut);
            });
            onChunkAdded(worldChunk);
        }
        else {
            loadChunkDataFromPacket(buf, nbt, worldChunk, consumer);
            refreshEmptySections(worldChunk);
        }
        
        this.level.onChunkLoaded(new ChunkPos(x, z));
        O_O.postClientChunkLoadEvent(worldChunk);
        clientChunkLoadSignal.emit(worldChunk);
        
//        LOGGER.info("load {} {} {}", level, x, z);
        
        return worldChunk;
    }
    
    /**
     * {@link net.minecraft.core.IdMap#byIdOrThrow(int)}
     * {@link net.minecraft.world.level.chunk.LinearPalette#read(FriendlyByteBuf)}
     */
    private void loadChunkDataFromPacket(
        FriendlyByteBuf buf,
        Map<Heightmap.Types, long[]> nbt,
        LevelChunk worldChunk,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        try {
            worldChunk.replaceWithPacketData(buf, nbt, consumer);
        }
        catch (Exception e) {
            LOGGER.error(
                "Error deserializing chunk packet {} {}",
                worldChunk.getLevel().dimension().identifier(),
                worldChunk.getPos(),
                e
            );
            CHelper.printChat(
                Component
                    .literal("Failed to deserialize chunk packet. %s %s %s".formatted(
                        worldChunk.getLevel().dimension().identifier(),
                        worldChunk.getPos().x(), worldChunk.getPos().z()
                    ))
                    .append(Component.literal(" Report issue:"))
                    .append(McHelper.getLinkText(O_O.getIssueLink()))
                    .withStyle(ChatFormatting.RED)
            );
            
            throw new RuntimeException(e);
        }
    }
    
    public List<LevelChunk> getCopiedChunkList() {
        return readChunkMap((chunkMap) -> {
            return Arrays.asList(chunkMap.values().toArray(new LevelChunk[0]));
        });
    }
    
    @Override
    public void updateViewCenter(int x, int z) {
        // do nothing
    }
    
    @Override
    public void updateViewRadius(int r) {
        // do nothing
    }
    
    @Override
    public String gatherStats() {
        return "Client Chunks (ImmPtl) " + getLoadedChunksCount();
    }
    
    @Override
    public int getLoadedChunksCount() {
        return readChunkMap(chunkMap -> {
            return chunkMap.size();
        });
    }
    
    @Override
    public void onLightUpdate(LightLayer lightType, SectionPos chunkSectionPos) {
        ClientWorldLoader.getLevelExtractor(level.dimension())
            .setSectionDirty(chunkSectionPos.x(), chunkSectionPos.y(), chunkSectionPos.z());
    }
    
}
