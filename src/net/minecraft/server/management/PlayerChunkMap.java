package net.minecraft.server.management;

import carpet.CarpetSettings;
import com.google.common.base.Predicate;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

public class PlayerChunkMap
{
    private static final Predicate<EntityPlayerMP> NOT_SPECTATOR = new Predicate<EntityPlayerMP>()
    {
        public boolean apply(@Nullable EntityPlayerMP p_apply_1_)
        {
            return p_apply_1_ != null && !p_apply_1_.isSpectator();
        }
    };
    private static final Predicate<EntityPlayerMP> CAN_GENERATE_CHUNKS = new Predicate<EntityPlayerMP>()
    {
        public boolean apply(@Nullable EntityPlayerMP p_apply_1_)
        {
            return p_apply_1_ != null && (!p_apply_1_.isSpectator() || p_apply_1_.getServerWorld().getGameRules().getBoolean("spectatorsGenerateChunks"));
        }
    };
    private final WorldServer world;
    /** players in the current instance */
    private final List<EntityPlayerMP> players = Lists.<EntityPlayerMP>newArrayList();
    /** the hash of all playerInstances created */
    private final Long2ObjectMap<PlayerChunkMapEntry> entryMap = new Long2ObjectOpenHashMap<PlayerChunkMapEntry>(4096);
    /** the playerInstances(chunks) that need to be updated */
    private final Set<PlayerChunkMapEntry> dirtyEntries = Sets.<PlayerChunkMapEntry>newHashSet();
    private final List<PlayerChunkMapEntry> pendingSendToPlayers = Lists.<PlayerChunkMapEntry>newLinkedList();
    /** List of player instances whose chunk field is unassigned, and need the chunk at their pos to be loaded. */
    private final List<PlayerChunkMapEntry> entriesWithoutChunks = Lists.<PlayerChunkMapEntry>newLinkedList();
    /** This field is using when chunk should be processed (every 8000 ticks) */
    private final List<PlayerChunkMapEntry> entries = Lists.<PlayerChunkMapEntry>newArrayList();
    /** Player view distance, in chunks. */
    private int playerViewRadius;
    /** time what is using to check if InhabitedTime should be calculated */
    private long previousTotalWorldTime;
    private boolean sortMissingChunks = true;
    private boolean sortSendToPlayers = true;

    public PlayerChunkMap(WorldServer serverWorld)
    {
        this.world = serverWorld;
        this.setPlayerViewRadius(serverWorld.getMinecraftServer().getPlayerList().getViewDistance());
    }

    /**
     * Returns the WorldServer associated with this PlayerManager
     */
    public WorldServer getWorldServer()
    {
        return this.world;
    }

    public Iterator<Chunk> getChunkIterator()
    {
        final Iterator<PlayerChunkMapEntry> iterator = this.entries.iterator();
        return new AbstractIterator<Chunk>()
        {
            protected Chunk computeNext()
            {
                while (true)
                {
                    if (iterator.hasNext())
                    {
                        PlayerChunkMapEntry playerchunkmapentry = iterator.next();
                        Chunk chunk = playerchunkmapentry.getChunk();

                        if (chunk == null)
                        {
                            continue;
                        }

                        if (!chunk.isLightPopulated() && chunk.isTerrainPopulated())
                        {
                            return chunk;
                        }

                        if (!chunk.wasTicked())
                        {
                            return chunk;
                        }

                        if (!playerchunkmapentry.hasPlayerMatchingInRange(128.0D, PlayerChunkMap.NOT_SPECTATOR))
                        {
                            continue;
                        }

                        return chunk;
                    }

                    return (Chunk)this.endOfData();
                }
            }
        };
    }

    /**
     * updates all the player instances that need to be updated
     */
    public void tick()
    {
        long i = this.world.getTotalWorldTime();

        if (i - this.previousTotalWorldTime > 8000L)
        {
            this.previousTotalWorldTime = i;

            for (int j = 0; j < this.entries.size(); ++j)
            {
                PlayerChunkMapEntry playerchunkmapentry = this.entries.get(j);
                playerchunkmapentry.update();
                playerchunkmapentry.updateChunkInhabitedTime();
            }
        }

        if (!this.dirtyEntries.isEmpty())
        {
            for (PlayerChunkMapEntry playerchunkmapentry2 : this.dirtyEntries)
            {
                playerchunkmapentry2.update();
            }

            this.dirtyEntries.clear();
        }

        // Fix for chunks not updating after async updates CARPET-PUNCHSTER
        if(CarpetSettings.asyncPacketUpdatesFix) {
            for(PlayerChunkMapEntry entry : entries){
                entry.changes = 0;
            }
        }

        if (this.sortMissingChunks && i % 4L == 0L)
        {
            this.sortMissingChunks = false;
            Collections.sort(this.entriesWithoutChunks, new Comparator<PlayerChunkMapEntry>()
            {
                public int compare(PlayerChunkMapEntry p_compare_1_, PlayerChunkMapEntry p_compare_2_)
                {
                    return ComparisonChain.start().compare(p_compare_1_.getClosestPlayerDistance(), p_compare_2_.getClosestPlayerDistance()).result();
                }
            });
        }

        if (this.sortSendToPlayers && i % 4L == 2L)
        {
            this.sortSendToPlayers = false;
            Collections.sort(this.pendingSendToPlayers, new Comparator<PlayerChunkMapEntry>()
            {
                public int compare(PlayerChunkMapEntry p_compare_1_, PlayerChunkMapEntry p_compare_2_)
                {
                    return ComparisonChain.start().compare(p_compare_1_.getClosestPlayerDistance(), p_compare_2_.getClosestPlayerDistance()).result();
                }
            });
        }

        if (!this.entriesWithoutChunks.isEmpty())
        {
            long l = System.nanoTime() + 50000000L;
            int k = 49;
            Iterator<PlayerChunkMapEntry> iterator = this.entriesWithoutChunks.iterator();

            while (iterator.hasNext())
            {
                PlayerChunkMapEntry playerchunkmapentry1 = iterator.next();

                if (playerchunkmapentry1.getChunk() == null)
                {
                    boolean flag = playerchunkmapentry1.hasPlayerMatching(CAN_GENERATE_CHUNKS);

                    if (playerchunkmapentry1.providePlayerChunk(flag))
                    {
                        iterator.remove();

                        if (playerchunkmapentry1.sendToPlayers())
                        {
                            this.pendingSendToPlayers.remove(playerchunkmapentry1);
                        }

                        --k;

                        if (k < 0 || System.nanoTime() > l)
                        {
                            break;
                        }
                    }
                }
            }
        }

        if (!this.pendingSendToPlayers.isEmpty())
        {
            int i1 = 81;
            Iterator<PlayerChunkMapEntry> iterator1 = this.pendingSendToPlayers.iterator();

            while (iterator1.hasNext())
            {
                PlayerChunkMapEntry playerchunkmapentry3 = iterator1.next();

                if (playerchunkmapentry3.sendToPlayers())
                {
                    iterator1.remove();
                    --i1;

                    if (i1 < 0)
                    {
                        break;
                    }
                }
            }
        }

        if (this.players.isEmpty())
        {
            WorldProvider worldprovider = this.world.provider;

            if (!worldprovider.canRespawnHere())
            {
                if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                    carpet.carpetclient.CarpetClientChunkLogger.setReason("Dimensional unloading due to no players");
                this.world.getChunkProvider().queueUnloadAll();
                carpet.carpetclient.CarpetClientChunkLogger.resetReason();
            }
        }

        // Sends updates to all subscribed players that want to get indexing of chunks Carpet-XCOM
        if(carpet.carpetclient.CarpetClientRandomtickingIndexing.sendUpdates(world)) {
            carpet.carpetclient.CarpetClientRandomtickingIndexing.sendRandomtickingChunkOrder(world, this);
        }
    }

    public boolean contains(int chunkX, int chunkZ)
    {
        long i = getIndex(chunkX, chunkZ);
        return this.entryMap.get(i) != null;
    }

    @Nullable
    public PlayerChunkMapEntry getEntry(int x, int z)
    {
        return (PlayerChunkMapEntry)this.entryMap.get(getIndex(x, z));
    }

    private PlayerChunkMapEntry getOrCreateEntry(int chunkX, int chunkZ, EntityPlayerMP player)
    {
        long i = getIndex(chunkX, chunkZ);
        PlayerChunkMapEntry playerchunkmapentry = (PlayerChunkMapEntry)this.entryMap.get(i);

        if (playerchunkmapentry == null)
        {
            playerchunkmapentry = new PlayerChunkMapEntry(this, chunkX, chunkZ, player);
            // Added a way to remove spectators loading chunks. CARPET-XCOM
            if(!CarpetSettings.spectatorsDontLoadChunks || !player.isSpectator()) this.entryMap.put(i, playerchunkmapentry);
            this.entries.add(playerchunkmapentry);

            if (playerchunkmapentry.getChunk() == null)
            {
                // Added a way to remove spectators loading chunks. CARPET-XCOM
                if(!CarpetSettings.spectatorsDontLoadChunks || !player.isSpectator()) this.entriesWithoutChunks.add(playerchunkmapentry);
            }

            if (!playerchunkmapentry.sendToPlayers())
            {
                this.pendingSendToPlayers.add(playerchunkmapentry);
            }
        }

        return playerchunkmapentry;
    }

    public void markBlockForUpdate(BlockPos pos)
    {
        int i = pos.getX() >> 4;
        int j = pos.getZ() >> 4;
        PlayerChunkMapEntry playerchunkmapentry = this.getEntry(i, j);

        if (playerchunkmapentry != null)
        {
            playerchunkmapentry.blockChanged(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        }
    }

    /**
     * Adds an EntityPlayerMP to the PlayerManager and to all player instances within player visibility
     */
    public void addPlayer(EntityPlayerMP player)
    {
        int i;
        int j;
        // Fix the player chunk map trunkation in negative coords causing offsets in chunk loading CARPET-XCOM
        if(!CarpetSettings.playerChunkLoadingFix) {
            i = (int)player.posX >> 4;
            j = (int)player.posZ >> 4;
        } else {
            i = MathHelper.floor(player.posX) >> 4;
            j = MathHelper.floor(player.posZ) >> 4;
        }
        player.managedPosX = player.posX;
        player.managedPosZ = player.posZ;

        for (int k = i - this.playerViewRadius; k <= i + this.playerViewRadius; ++k)
        {
            for (int l = j - this.playerViewRadius; l <= j + this.playerViewRadius; ++l)
            {
                this.getOrCreateEntry(k, l, player).addPlayer(player);
            }
        }

        this.players.add(player);
        this.markSortPending();
    }

    /**
     * Removes an EntityPlayerMP from the PlayerManager.
     */
    public void removePlayer(EntityPlayerMP player)
    {
        int i;
        int j;
        // Fix the player chunk map trunkation in negative coords causing offsets in chunk loading CARPET-XCOM
        if(!CarpetSettings.playerChunkLoadingFix) {
            i = (int)player.managedPosX >> 4;
            j = (int)player.managedPosZ >> 4;
        } else {
            i = MathHelper.floor(player.managedPosX) >> 4;
            j = MathHelper.floor(player.managedPosZ) >> 4;
        }

        for (int k = i - this.playerViewRadius; k <= i + this.playerViewRadius; ++k)
        {
            for (int l = j - this.playerViewRadius; l <= j + this.playerViewRadius; ++l)
            {
                PlayerChunkMapEntry playerchunkmapentry = this.getEntry(k, l);

                if (playerchunkmapentry != null)
                {
                    playerchunkmapentry.removePlayer(player);
                }
            }
        }

        this.players.remove(player);
        this.markSortPending();
    }

    /**
     * Determine if two rectangles centered at the given points overlap for the provided radius. Arguments: x1, z1, x2,
     * z2, radius.
     */
    private boolean overlaps(int x1, int z1, int x2, int z2, int radius)
    {
        int i = x1 - x2;
        int j = z1 - z2;

        if (i >= -radius && i <= radius)
        {
            return j >= -radius && j <= radius;
        }
        else
        {
            return false;
        }
    }

    /**
     * Update chunks around a player that moved
     */
    public void updateMovingPlayer(EntityPlayerMP player)
    {
        int i;
        int j;
        // Fix the player chunk map trunkation in negative coords causing offsets in chunk loading CARPET-XCOM
        if(!CarpetSettings.playerChunkLoadingFix) {
            i = (int)player.posX >> 4;
            j = (int)player.posZ >> 4;
        } else {
            i = MathHelper.floor(player.posX) >> 4;
            j = MathHelper.floor(player.posZ) >> 4;
        }
        double d0 = player.managedPosX - player.posX;
        double d1 = player.managedPosZ - player.posZ;
        double d2 = d0 * d0 + d1 * d1;

        if (d2 >= 64.0D)
        {
            int k;
            int l;
            // Fix the player chunk map trunkation in negative coords causing offsets in chunk loading CARPET-XCOM
            if(!CarpetSettings.playerChunkLoadingFix) {
                k = (int)player.managedPosX >> 4;
                l = (int)player.managedPosZ >> 4;
            } else {
                k = MathHelper.floor(player.managedPosX) >> 4;
                l = MathHelper.floor(player.managedPosZ) >> 4;
            }
            int i1 = this.playerViewRadius;
            int j1 = i - k;
            int k1 = j - l;

            if (j1 != 0 || k1 != 0)
            {
                for (int l1 = i - i1; l1 <= i + i1; ++l1)
                {
                    for (int i2 = j - i1; i2 <= j + i1; ++i2)
                    {
                        if (!this.overlaps(l1, i2, k, l, i1))
                        {
                            this.getOrCreateEntry(l1, i2, player).addPlayer(player);
                        }

                        if (!this.overlaps(l1 - j1, i2 - k1, i, j, i1))
                        {
                            PlayerChunkMapEntry playerchunkmapentry = this.getEntry(l1 - j1, i2 - k1);

                            if (playerchunkmapentry != null)
                            {
                                playerchunkmapentry.removePlayer(player);
                            }
                        }
                    }
                }

                player.managedPosX = player.posX;
                player.managedPosZ = player.posZ;
                this.markSortPending();
            }
        }
    }

    public boolean isPlayerWatchingChunk(EntityPlayerMP player, int chunkX, int chunkZ)
    {
        PlayerChunkMapEntry playerchunkmapentry = this.getEntry(chunkX, chunkZ);
        return playerchunkmapentry != null && playerchunkmapentry.containsPlayer(player) && playerchunkmapentry.isSentToPlayers();
    }

    /**
     * Called when the server's view distance changes, sending or rescinding chunks as needed.
     *  
     * @param radius Radius in chunks
     */
    public void setPlayerViewRadius(int radius)
    {
        radius = MathHelper.clamp(radius, 3, 32);

        if (radius != this.playerViewRadius)
        {
            int i = radius - this.playerViewRadius;

            for (EntityPlayerMP entityplayermp : Lists.newArrayList(this.players))
            {
                int j = (int)entityplayermp.posX >> 4;
                int k = (int)entityplayermp.posZ >> 4;

                if (i > 0)
                {
                    for (int j1 = j - radius; j1 <= j + radius; ++j1)
                    {
                        for (int k1 = k - radius; k1 <= k + radius; ++k1)
                        {
                            PlayerChunkMapEntry playerchunkmapentry = this.getOrCreateEntry(j1, k1, entityplayermp);

                            if (!playerchunkmapentry.containsPlayer(entityplayermp))
                            {
                                playerchunkmapentry.addPlayer(entityplayermp);
                            }
                        }
                    }
                }
                else
                {
                    for (int l = j - this.playerViewRadius; l <= j + this.playerViewRadius; ++l)
                    {
                        for (int i1 = k - this.playerViewRadius; i1 <= k + this.playerViewRadius; ++i1)
                        {
                            if (!this.overlaps(l, i1, j, k, radius))
                            {
                                this.getOrCreateEntry(l, i1, entityplayermp).removePlayer(entityplayermp);
                            }
                        }
                    }
                }
            }

            this.playerViewRadius = radius;
            this.markSortPending();
        }
    }

    private void markSortPending()
    {
        this.sortMissingChunks = true;
        this.sortSendToPlayers = true;
    }

    /**
     * Gets the max entity track distance (in blocks) for the given view distance.
     *  
     * @param distance The view distance in chunks
     */
    public static int getFurthestViewableBlock(int distance)
    {
        return distance * 16 - 16;
    }

    private static long getIndex(int chunkX, int chunkZ)
    {
        return (long)chunkX + 2147483647L | (long)chunkZ + 2147483647L << 32;
    }

    /**
     * Marks an entry as dirty
     */
    public void entryChanged(PlayerChunkMapEntry entry)
    {
        this.dirtyEntries.add(entry);
    }

    public void removeEntry(PlayerChunkMapEntry entry)
    {
        ChunkPos chunkpos = entry.getPos();
        long i = getIndex(chunkpos.x, chunkpos.z);
        entry.updateChunkInhabitedTime();
        this.entryMap.remove(i);
        this.entries.remove(entry);
        this.dirtyEntries.remove(entry);
        this.pendingSendToPlayers.remove(entry);
        this.entriesWithoutChunks.remove(entry);
        Chunk chunk = entry.getChunk();

        if (chunk != null)
        {
            if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                carpet.carpetclient.CarpetClientChunkLogger.setReason("Player leaving chunk, queuing unload");
            this.getWorldServer().getChunkProvider().queueUnload(chunk);
            carpet.carpetclient.CarpetClientChunkLogger.resetReason();
        }
    }
    
    /*
     * 0x Chunk Logger - Gets the coordinates of all chunks
     */
    public Iterator<ChunkPos> carpetGetAllChunkCoordinates(){
    	return new AbstractIterator<ChunkPos>() {
    		Iterator<PlayerChunkMapEntry> allChunks = Iterators.concat(entries.iterator(),entriesWithoutChunks.iterator());
			@Override
			protected ChunkPos computeNext() {
				if(allChunks.hasNext()) {
					return allChunks.next().getPos();
				}
				else {
					return (ChunkPos) this.endOfData();
				}
			}
    	};
    }
}