package net.minecraft.world.gen;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.NBTTagList;
import carpet.CarpetSettings;
import carpet.carpetclient.CarpetClientChunkLogger;
import carpet.utils.TickingArea;
import carpet.utils.UnloadOrder;
import net.minecraft.entity.Entity;
import net.minecraft.server.management.PlayerChunkMapEntry;

public class ChunkProviderServer implements IChunkProvider
{
    private static final Logger LOGGER = LogManager.getLogger();
    public final Set<Long> droppedChunks = Sets.<Long>newHashSet();
    public final IChunkGenerator chunkGenerator;
    public final IChunkLoader chunkLoader; // CM changed to public for ticking areas
    /** map of chunk Id's to Chunk instances */
    public final Long2ObjectMap<Chunk> loadedChunks = new Long2ObjectOpenHashMap<Chunk>(8192); // CM changed to public for commandUnload
    private final WorldServer world;

    // CM: simulatePermaloader
    public boolean fakePermaloaderProtected = false;

    public ChunkProviderServer(WorldServer worldObjIn, IChunkLoader chunkLoaderIn, IChunkGenerator chunkGeneratorIn)
    {
        this.world = worldObjIn;
        this.chunkLoader = chunkLoaderIn;
        this.chunkGenerator = chunkGeneratorIn;
    }

    public Collection<Chunk> getLoadedChunks()
    {
        return this.loadedChunks.values();
    }

    /**
     * Marks the chunk for unload if the {@link WorldProvider} allows it.
     *  
     * Queueing a chunk for unload does <b>not</b> guarantee that it will be unloaded, as any request for the chunk will
     * unqueue the chunk.
     */
    public void queueUnload(Chunk chunkIn)
    {
        boolean canDrop = world.provider.canDropChunk(chunkIn.x, chunkIn.z);
        if (CarpetSettings.disableSpawnChunks)
            canDrop = true;
        if (CarpetSettings.tickingAreas)
            canDrop &= !TickingArea.isTickingChunk(world, chunkIn.x, chunkIn.z);
        if (canDrop)
        {
        	// ChunkLogger - 0x-CARPET
            if(CarpetClientChunkLogger.logger.enabled) {
            	CarpetClientChunkLogger.logger.log(this.world,chunkIn.x,chunkIn.z,CarpetClientChunkLogger.Event.QUEUE_UNLOAD);
            }
        	
            this.droppedChunks.add(Long.valueOf(ChunkPos.asLong(chunkIn.x, chunkIn.z)));
            chunkIn.unloadQueued = true;
        }
    }

    /**
     * Marks all chunks for unload
     *  
     * @see #queueUnload(Chunk)
     */
    public void queueUnloadAll()
    {
        if (CarpetSettings.simulatePermaloader) this.fakePermaloaderProtected = true;
        ObjectIterator objectiterator = this.loadedChunks.values().iterator();

        while (objectiterator.hasNext())
        {
            Chunk chunk = (Chunk)objectiterator.next();
            this.queueUnload(chunk);
        }
    }

    @Nullable
    public Chunk getLoadedChunk(int x, int z)
    {
        long i = ChunkPos.asLong(x, z);
        Chunk chunk = (Chunk)this.loadedChunks.get(i);

        if (chunk != null)
        {
        	// ChunkLogger - 0x-CARPET
        	if(CarpetClientChunkLogger.logger.enabled && chunk.unloadQueued) {
        		CarpetClientChunkLogger.logger.log(this.world,x,z,CarpetClientChunkLogger.Event.CANCEL_UNLOAD);
        	}
        	
            chunk.unloadQueued = false;
        }

        return chunk;
    }

    @Nullable
    public Chunk loadChunk(int x, int z)
    {
        Chunk chunk = this.getLoadedChunk(x, z);

        if (chunk == null)
        {
            chunk = this.loadChunkFromFile(x, z);

            if (chunk != null)
            {
            	// ChunkLogger - 0x-CARPET
                if(CarpetClientChunkLogger.logger.enabled) {
                	CarpetClientChunkLogger.logger.log(this.world,x,z,CarpetClientChunkLogger.Event.LOADING);
                }

                // Fix for chunks not updating after async updates CARPET-PUNCHSTER
                if(CarpetSettings.asyncPacketUpdatesFix) {
                    PlayerChunkMapEntry entry = world.playerChunkMap.getEntry(x, z);
                    if (entry != null && entry.chunk != null) {
                        entry.chunk = chunk;
                        entry.sendToPlayers();
                    }
                }
            	
                this.loadedChunks.put(ChunkPos.asLong(x, z), chunk);
                chunk.onLoad();
                if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                    carpet.carpetclient.CarpetClientChunkLogger.setReason("Population triggering neighbouring chunks to cancel unload");
                chunk.populate(this, this.chunkGenerator);
                carpet.carpetclient.CarpetClientChunkLogger.resetToOldReason();
            }
        }

        return chunk;
    }

    public Chunk provideChunk(int x, int z)
    {
        Chunk chunk = this.loadChunk(x, z);

        if (chunk == null)
        {
            long i = ChunkPos.asLong(x, z);

            try
            {
                chunk = this.chunkGenerator.generateChunk(x, z);
                
            	// ChunkLogger - 0x-CARPET
                if(CarpetClientChunkLogger.logger.enabled) {
                	CarpetClientChunkLogger.logger.log(this.world,x,z,CarpetClientChunkLogger.Event.GENERATING);
                }
            }
            catch (Throwable throwable)
            {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception generating new chunk");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Chunk to be generated");
                crashreportcategory.addCrashSection("Location", String.format("%d,%d", x, z));
                crashreportcategory.addCrashSection("Position hash", Long.valueOf(i));
                crashreportcategory.addCrashSection("Generator", this.chunkGenerator);
                throw new ReportedException(crashreport);
            }
            
            this.loadedChunks.put(i, chunk);
            chunk.onLoad();
            chunk.populate(this, this.chunkGenerator);
        }

        return chunk;
    }

    @Nullable
    private Chunk loadChunkFromFile(int x, int z)
    {
        try
        {
            Chunk chunk = this.chunkLoader.loadChunk(this.world, x, z);

            if (chunk != null)
            {
                chunk.setLastSaveTime(this.world.getTotalWorldTime());
                this.chunkGenerator.recreateStructures(chunk, x, z);
            }

            return chunk;
        }
        catch (Exception exception)
        {
            LOGGER.error("Couldn't load chunk", (Throwable)exception);
            return null;
        }
    }

    private void saveChunkExtraData(Chunk chunkIn)
    {
        try
        {
            this.chunkLoader.saveExtraChunkData(this.world, chunkIn);
        }
        catch (Exception exception)
        {
            LOGGER.error("Couldn't save entities", (Throwable)exception);
        }
    }

    private void saveChunkData(Chunk chunkIn)
    {
        try
        {
            chunkIn.setLastSaveTime(this.world.getTotalWorldTime());
            this.chunkLoader.saveChunk(this.world, chunkIn);
        }
        catch (IOException ioexception)
        {
            LOGGER.error("Couldn't save chunk", (Throwable)ioexception);
        }
        catch (MinecraftException minecraftexception)
        {
            LOGGER.error("Couldn't save chunk; already in use by another instance of Minecraft?", (Throwable)minecraftexception);
        }
    }

    public boolean saveChunks(boolean all)
    {
        // NewLight PHIPRO-CARPET
        if (CarpetSettings.newLight) this.world.lightingEngine.procLightUpdates();
        int i = 0;
        List<Chunk> list = Lists.newArrayList(this.loadedChunks.values());

        for (int j = 0; j < list.size(); ++j)
        {
            Chunk chunk = list.get(j);

            if (all)
            {
                this.saveChunkExtraData(chunk);
            }

            if (chunk.needsSaving(all))
            {
                this.saveChunkData(chunk);
                chunk.setModified(false);
                ++i;

                if (i == 24 && !all)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Flushes all pending chunks fully back to disk
     */
    public void flushToDisk()
    {
        this.chunkLoader.flush();
    }

    /**
     * Unloads chunks that are marked to be unloaded. This is not guaranteed to unload every such chunk.
     */
    public boolean tick()
    {
        if (!this.world.disableLevelSaving)
        {
            if (!this.droppedChunks.isEmpty() && !this.fakePermaloaderProtected)
            {
                if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                    carpet.carpetclient.CarpetClientChunkLogger.setReason("Unloading chunk and writing to disk");
                // NewLight PHIPRO-CARPET
                if (CarpetSettings.newLight) this.world.lightingEngine.procLightUpdates();
                Iterator<Long> iterator = this.droppedChunks.iterator();

                for (int i = 0; i < 100 && iterator.hasNext(); iterator.remove())
                {
                    Long olong = iterator.next();
                    Chunk chunk = (Chunk)this.loadedChunks.get(olong);

                    if (chunk != null /*&& chunk.unloadQueued*/) // CM: moved check below
                    {
                        if (chunk.unloadQueued) {
                        chunk.onUnload();
                        this.saveChunkData(chunk);
                        this.saveChunkExtraData(chunk);
                        this.loadedChunks.remove(olong);
                        ++i;

                    	// ChunkLogger - 0x-CARPET
                        if(CarpetClientChunkLogger.logger.enabled) {
                            CarpetClientChunkLogger.logger.log(this.world,chunk.x,chunk.z,CarpetClientChunkLogger.Event.UNLOADING);
                        }
                        } else if (CarpetSettings.whereToChunkSavestate.canUnloadNearPlayers) {
                            //noinspection ConstantConditions
                            if (CarpetSettings.whereToChunkSavestate == CarpetSettings.WhereToChunkSavestate.everywhere
                                    || world.getPlayers(Entity.class, player -> player.chunkCoordX == chunk.x && player.chunkCoordZ == chunk.z).isEmpty()) {
                                // Getting the chunk size is incredibly inefficient, but it's better than unloading and reloading the chunk
                                if ((UnloadOrder.getSavedChunkSize(chunk) + 5) / 4096 + 1 >= 256) {
                                    chunk.onUnload();
                                    //this.saveChunkData(chunk); no point saving the chunk data, we know that won't work
                                    this.saveChunkExtraData(chunk);
                                    this.loadedChunks.remove(olong);
                                    //++i; don't break stuff
                                    Chunk newChunk = this.loadChunk(chunk.x, chunk.z);
                                    if (newChunk != null)
                                        newChunk.onTick(true);
                                    PlayerChunkMapEntry pcmEntry = world.playerChunkMap.getEntry(chunk.x, chunk.z);
                                    if (pcmEntry != null) {
                                        pcmEntry.chunk = newChunk;
                                        pcmEntry.sentToPlayers = false;
                                        pcmEntry.sendToPlayers();
                                    }
                                }
                            }
                        }
                    }
                }
                carpet.carpetclient.CarpetClientChunkLogger.resetReason();
            }
            this.fakePermaloaderProtected = false;

            this.chunkLoader.chunkTick();
        }

        return false;
    }

    /**
     * Returns if the IChunkProvider supports saving.
     */
    public boolean canSave()
    {
        return !this.world.disableLevelSaving;
    }

    /**
     * Converts the instance data to a readable string.
     */
    public String makeString()
    {
        return "ServerChunkCache: " + this.loadedChunks.size() + " Drop: " + this.droppedChunks.size();
    }

    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos)
    {
        return this.chunkGenerator.getPossibleCreatures(creatureType, pos);
    }

    @Nullable
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored)
    {
        return this.chunkGenerator.getNearestStructurePos(worldIn, structureName, position, findUnexplored);
    }

    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos)
    {
        return this.chunkGenerator.isInsideStructure(worldIn, structureName, pos);
    }

    public int getLoadedChunkCount()
    {
        return this.loadedChunks.size();
    }

    /**
     * Checks to see if a chunk exists at x, z
     */
    public boolean chunkExists(int x, int z)
    {
        return this.loadedChunks.containsKey(ChunkPos.asLong(x, z));
    }
    
    public boolean isChunkUnloadScheduled(int x, int z) {
    	long chunk = ChunkPos.asLong(x, z);
    	return this.droppedChunks.contains(chunk);
    }

    public boolean isChunkGeneratedAt(int x, int z)
    {
        return this.loadedChunks.containsKey(ChunkPos.asLong(x, z)) || this.chunkLoader.isChunkGeneratedAt(x, z);
    }

    // Retrieval method to get the bounding boxes CARPET-XCOM
    public NBTTagList getBoundingBoxes(Entity entity) { return this.chunkGenerator.getBoundingBoxes(entity); }
}