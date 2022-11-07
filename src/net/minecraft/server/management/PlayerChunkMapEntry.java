package net.minecraft.server.management;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketUnloadChunk;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import carpet.CarpetSettings;
import carpet.carpetclient.CarpetClientChunkLogger;
import carpet.helpers.IPlayerSensitiveTileEntity;

public class PlayerChunkMapEntry
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final PlayerChunkMap playerChunkMap;
    public final List<EntityPlayerMP> players = Lists.<EntityPlayerMP>newArrayList(); // CM: changed to public
    private final ChunkPos pos;
    private final short[] changedBlocks = new short[64];
    @Nullable
    public Chunk chunk; // CM: changed to public
    public int changes;
    private int changedSectionFilter;
    private long lastUpdateInhabitedTime;
    public boolean sentToPlayers; // CM: changed to public

    public PlayerChunkMapEntry(PlayerChunkMap mapIn, int chunkX, int chunkZ, EntityPlayerMP player)
    {
        this.playerChunkMap = mapIn;
        this.pos = new ChunkPos(chunkX, chunkZ);
        if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
            carpet.carpetclient.CarpetClientChunkLogger.setReason("Player loading chunk");
        // Added a way to remove spectators loading chunks. CARPET-XCOM
        if(!CarpetSettings.spectatorsDontLoadChunks || !player.isSpectator()) this.chunk = mapIn.getWorldServer().getChunkProvider().loadChunk(chunkX, chunkZ);
        carpet.carpetclient.CarpetClientChunkLogger.resetReason();
    }

    public ChunkPos getPos()
    {
        return this.pos;
    }

    public void addPlayer(EntityPlayerMP player)
    {
        if (this.players.contains(player))
        {
            LOGGER.debug("Failed to add player. {} already is in chunk {}, {}", player, Integer.valueOf(this.pos.x), Integer.valueOf(this.pos.z));
        }
        else
        {
            // ChunkLogger - 0x-CARPET
            if(CarpetClientChunkLogger.logger.enabled) {
                if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                    carpet.carpetclient.CarpetClientChunkLogger.setReason("Player added to chunk");
            	CarpetClientChunkLogger.logger.log(this.playerChunkMap.getWorldServer(),pos.x,pos.z,CarpetClientChunkLogger.Event.PLAYER_ENTERS);
                carpet.carpetclient.CarpetClientChunkLogger.resetReason();
            }

            if (this.players.isEmpty())
            {
                this.lastUpdateInhabitedTime = this.playerChunkMap.getWorldServer().getTotalWorldTime();
            }

            this.players.add(player);

            if (this.sentToPlayers)
            {
                this.sendToPlayer(player);
            }
        }

        // Added to enable randomticking chunk updates Carpet-XCOM
        carpet.carpetclient.CarpetClientRandomtickingIndexing.enableUpdate(player);
    }

    public void removePlayer(EntityPlayerMP player)
    {
        if (this.players.contains(player))
        {
            // Return false to prevent client unloading the chunks when attempting to use spectate entitys near unloaded chunks. CARPET-XCOM
            if (this.sentToPlayers && (!CarpetSettings.spectatorsDontLoadChunks || !player.isSpectator()))
            {
                player.connection.sendPacket(new SPacketUnloadChunk(this.pos.x, this.pos.z));
            }

            this.players.remove(player);

            if (this.players.isEmpty())
            {
                // ChunkLogger - 0x-CARPET
                if(CarpetClientChunkLogger.logger.enabled) {
                    if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                        carpet.carpetclient.CarpetClientChunkLogger.setReason("Player removed from chunk");
                	CarpetClientChunkLogger.logger.log(this.playerChunkMap.getWorldServer(),pos.x,pos.z,CarpetClientChunkLogger.Event.PLAYER_LEAVES);
                    carpet.carpetclient.CarpetClientChunkLogger.resetReason();
                }
            	
                this.playerChunkMap.removeEntry(this);
            }
        }

        // Added to enable randomticking chunk updates Carpet-XCOM
        carpet.carpetclient.CarpetClientRandomtickingIndexing.enableUpdate(player);
    }

    /**
     * Provide the chunk at the player's location. Can fail, returning false, if the player is a spectator floating
     * outside of any pre-existing chunks, and the server is not configured to allow chunk generation for spectators.
     */
    public boolean providePlayerChunk(boolean canGenerate)
    {
        if (this.chunk != null)
        {
            return true;
        }
        else
        {
            if (canGenerate)
            {
                if(carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                    carpet.carpetclient.CarpetClientChunkLogger.setReason("Player loading new chunks and generating");
                this.chunk = this.playerChunkMap.getWorldServer().getChunkProvider().provideChunk(this.pos.x, this.pos.z);
                carpet.carpetclient.CarpetClientChunkLogger.resetReason();
            }
            else
            {
                this.chunk = this.playerChunkMap.getWorldServer().getChunkProvider().loadChunk(this.pos.x, this.pos.z);
            }

            return this.chunk != null;
        }
    }

    public boolean sendToPlayers()
    {
        if (this.sentToPlayers)
        {
            return true;
        }
        else if (this.chunk == null)
        {
            return false;
        }
        else if (!this.chunk.isPopulated())
        {
            return false;
        }
        else
        {
            this.changes = 0;
            this.changedSectionFilter = 0;
            this.sentToPlayers = true;
            Packet<?> packet = new SPacketChunkData(this.chunk, 65535);

            for (EntityPlayerMP entityplayermp : this.players)
            {
                entityplayermp.connection.sendPacket(packet);
                this.playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(entityplayermp, this.chunk);
            }

            return true;
        }
    }

    /**
     * Fully resyncs this chunk's blocks, tile entities, and entity attachments (passengers and leashes) to all tracking
     * players
     */
    public void sendToPlayer(EntityPlayerMP player)
    {
        if (this.sentToPlayers)
        {
            player.connection.sendPacket(new SPacketChunkData(this.chunk, 65535));
            this.playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(player, this.chunk);
        }
    }

    public void updateChunkInhabitedTime()
    {
        long i = this.playerChunkMap.getWorldServer().getTotalWorldTime();

        if (this.chunk != null)
        {
            this.chunk.setInhabitedTime(this.chunk.getInhabitedTime() + i - this.lastUpdateInhabitedTime);
        }

        this.lastUpdateInhabitedTime = i;
    }

    public void blockChanged(int x, int y, int z)
    {
        if (this.sentToPlayers)
        {
            if (this.changes == 0)
            {
                this.playerChunkMap.entryChanged(this);
            }

            this.changedSectionFilter |= 1 << (y >> 4);

            if (this.changes < 64)
            {
                short short1 = (short)(x << 12 | z << 8 | y);

                for (int i = 0; i < this.changes; ++i)
                {
                    if (this.changedBlocks[i] == short1)
                    {
                        return;
                    }
                }

                this.changedBlocks[this.changes++] = short1;
            }
        }
    }

    public void sendPacket(Packet<?> packetIn)
    {
        if (this.sentToPlayers)
        {
            for (int i = 0; i < this.players.size(); ++i)
            {
                (this.players.get(i)).connection.sendPacket(packetIn);
            }
        }
    }

    public void update()
    {
        if (this.sentToPlayers && this.chunk != null)
        {
            if (this.changes != 0)
            {
                if (this.changes == 1)
                {
                    int i = (this.changedBlocks[0] >> 12 & 15) + this.pos.x * 16;
                    int j = this.changedBlocks[0] & 255;
                    int k = (this.changedBlocks[0] >> 8 & 15) + this.pos.z * 16;
                    BlockPos blockpos = new BlockPos(i, j, k);
                    this.sendPacket(new SPacketBlockChange(this.playerChunkMap.getWorldServer(), blockpos));

                    if (this.playerChunkMap.getWorldServer().getBlockState(blockpos).getBlock().hasTileEntity())
                    {
                        this.sendBlockEntity(this.playerChunkMap.getWorldServer().getTileEntity(blockpos));
                    }
                }
                else if (this.changes == 64)
                {
                    this.sendPacket(new SPacketChunkData(this.chunk, this.changedSectionFilter));
                }
                else
                {
                    this.sendPacket(new SPacketMultiBlockChange(this.changes, this.changedBlocks, this.chunk));

                    for (int l = 0; l < this.changes; ++l)
                    {
                        int i1 = (this.changedBlocks[l] >> 12 & 15) + this.pos.x * 16;
                        int j1 = this.changedBlocks[l] & 255;
                        int k1 = (this.changedBlocks[l] >> 8 & 15) + this.pos.z * 16;
                        BlockPos blockpos1 = new BlockPos(i1, j1, k1);

                        if (this.playerChunkMap.getWorldServer().getBlockState(blockpos1).getBlock().hasTileEntity())
                        {
                            this.sendBlockEntity(this.playerChunkMap.getWorldServer().getTileEntity(blockpos1));
                        }
                    }
                }

                this.changes = 0;
                this.changedSectionFilter = 0;
            }
        }
    }

    private void sendBlockEntity(@Nullable TileEntity be)
    {
        if (be != null)
        {
            if (be instanceof IPlayerSensitiveTileEntity)
            {
                if (sentToPlayers)
                {
                    for (int i = 0; i < players.size(); i++)
                    {
                        EntityPlayerMP player = players.get(i);
                        SPacketUpdateTileEntity packet = ((IPlayerSensitiveTileEntity) be).getUpdatePacketPlayerSensitive(player);
                        if (packet != null)
                            player.connection.sendPacket(packet);
                    }
                }
            }
            else
            {
            SPacketUpdateTileEntity spacketupdatetileentity = be.getUpdatePacket();

            if (spacketupdatetileentity != null)
            {
                this.sendPacket(spacketupdatetileentity);
            }
            }
        }
    }

    public boolean containsPlayer(EntityPlayerMP player)
    {
        return this.players.contains(player);
    }

    public boolean hasPlayerMatching(Predicate<EntityPlayerMP> predicate)
    {
        return Iterables.tryFind(this.players, predicate).isPresent();
    }

    public boolean hasPlayerMatchingInRange(double range, Predicate<EntityPlayerMP> predicate)
    {
        int i = 0;

        for (int j = this.players.size(); i < j; ++i)
        {
            EntityPlayerMP entityplayermp = this.players.get(i);

            if (predicate.apply(entityplayermp) && this.pos.getDistanceSq(entityplayermp) < range * range)
            {
                return true;
            }
        }

        return false;
    }

    public boolean isSentToPlayers()
    {
        return this.sentToPlayers;
    }

    @Nullable
    public Chunk getChunk()
    {
        return this.chunk;
    }

    public double getClosestPlayerDistance()
    {
        double d0 = Double.MAX_VALUE;

        for (EntityPlayerMP entityplayermp : this.players)
        {
            double d1 = this.pos.getDistanceSq(entityplayermp);

            if (d1 < d0)
            {
                d0 = d1;
            }
        }

        return d0;
    }
}