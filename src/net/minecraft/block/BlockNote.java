package net.minecraft.block;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import redstone.multimeter.block.MeterableBlock;
import redstone.multimeter.helper.WorldHelper;
import carpet.CarpetSettings;
import net.minecraft.init.Blocks;

public class BlockNote extends BlockContainer implements MeterableBlock /* RSMM */
{
    private static final List<SoundEvent> INSTRUMENTS = Lists.newArrayList(SoundEvents.BLOCK_NOTE_HARP, SoundEvents.BLOCK_NOTE_BASEDRUM, SoundEvents.BLOCK_NOTE_SNARE, SoundEvents.BLOCK_NOTE_HAT, SoundEvents.BLOCK_NOTE_BASS, SoundEvents.BLOCK_NOTE_FLUTE, SoundEvents.BLOCK_NOTE_BELL, SoundEvents.BLOCK_NOTE_GUITAR, SoundEvents.BLOCK_NOTE_CHIME, SoundEvents.BLOCK_NOTE_XYLOPHONE);
    private SoundEvent saveSoundEvent;

    public BlockNote()
    {
        super(Material.WOOD);
        this.setCreativeTab(CreativeTabs.REDSTONE);
    }

    /**
     * Called when a neighboring block was changed and marks that this state should perform any checks during a neighbor
     * change. Cases may include when redstone power is updated, cactus blocks popping off due to a neighboring solid
     * block, etc.
     */
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos)
    {
        boolean flag = worldIn.isBlockPowered(pos);
        TileEntity tileentity = worldIn.getTileEntity(pos);

        logPowered(worldIn, pos, flag); // RSMM

        if (tileentity instanceof TileEntityNote)
        {
            TileEntityNote tileentitynote = (TileEntityNote)tileentity;
            // Get the instrument type based on the block below. CARPET-XCOM
            SoundEvent instrument = getInstrument(getID(worldIn, pos));

            if (tileentitynote.previousRedstoneState != flag)
            {
                if (flag)
                {
                    tileentitynote.triggerNote(worldIn, pos);
                }

                tileentitynote.previousRedstoneState = flag;
  
                // RSMM start
                if (!worldIn.isRemote) {
                    WorldHelper.getMultimeter().logActive(worldIn, pos, flag);
                }
                // RSMM end

                //Added note block imitation in 1.13 CARPET-XCOM
                if(CarpetSettings.noteBlockImitationOf1_13) worldIn.notifyNeighborsOfStateChange(pos, this, true);
            }else if(saveSoundEvent == null || saveSoundEvent != instrument){
	            saveSoundEvent = instrument;
	            if(CarpetSettings.noteBlockImitationOf1_13) worldIn.updateObservingBlocksAt(pos, blockIn);
            }
        }
    }

    /**
     * Called when the block is right clicked by a player.
     */
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if (worldIn.isRemote)
        {
            return true;
        }
        else
        {
            TileEntity tileentity = worldIn.getTileEntity(pos);

            if (tileentity instanceof TileEntityNote)
            {
                TileEntityNote tileentitynote = (TileEntityNote)tileentity;
                tileentitynote.changePitch();
                tileentitynote.triggerNote(worldIn, pos);
                playerIn.addStat(StatList.NOTEBLOCK_TUNED);
            }

            //Added note block imitation in 1.13 CARPET-XCOM
            if(CarpetSettings.noteBlockImitationOf1_13) worldIn.notifyNeighborsOfStateChange(pos, this, true);

            return true;
        }
    }

    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn)
    {
        if (!worldIn.isRemote)
        {
            TileEntity tileentity = worldIn.getTileEntity(pos);

            if (tileentity instanceof TileEntityNote)
            {
                ((TileEntityNote)tileentity).triggerNote(worldIn, pos);
                playerIn.addStat(StatList.NOTEBLOCK_PLAYED);
            }
        }
    }

    /**
     * Returns a new instance of a block's tile entity class. Called on placing the block.
     */
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TileEntityNote();
    }

    private SoundEvent getInstrument(int eventId)
    {
        if (eventId < 0 || eventId >= INSTRUMENTS.size())
        {
            eventId = 0;
        }

        return INSTRUMENTS.get(eventId);
    }

    /**
     * Called on server when World#addBlockEvent is called. If server returns true, then also called on the client. On
     * the Server, this may perform additional changes to the world, like pistons replacing the block with an extended
     * base. On the client, the update may involve replacing tile entities or effects such as sounds or particles
     * @deprecated call via {@link IBlockState#onBlockEventReceived(World,BlockPos,int,int)} whenever possible.
     * Implementing/overriding is fine.
     */
    public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int id, int param)
    {
        float f = (float)Math.pow(2.0D, (double)(param - 12) / 12.0D);
        worldIn.playSound((EntityPlayer)null, pos, this.getInstrument(id), SoundCategory.RECORDS, 3.0F, f);
        worldIn.spawnParticle(EnumParticleTypes.NOTE, (double)pos.getX() + 0.5D, (double)pos.getY() + 1.2D, (double)pos.getZ() + 0.5D, (double)param / 24.0D, 0.0D, 0.0D);
        return true;
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only,
     * LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     * @deprecated call via {@link IBlockState#getRenderType()} whenever possible. Implementing/overriding is fine.
     */
    public EnumBlockRenderType getRenderType(IBlockState state)
    {
        return EnumBlockRenderType.MODEL;
    }

    // Getting block id to store value of note block sound type. CARPET-XCOM
    private int getID(World worldIn, BlockPos pos){
        IBlockState iblockstate = worldIn.getBlockState(pos.down());
        Material material = iblockstate.getMaterial();
        int i = 0;

        if (material == Material.ROCK)
        {
            i = 1;
        }

        if (material == Material.SAND)
        {
            i = 2;
        }

        if (material == Material.GLASS)
        {
            i = 3;
        }

        if (material == Material.WOOD)
        {
            i = 4;
        }

        Block block = iblockstate.getBlock();

        if (block == Blocks.CLAY)
        {
            i = 5;
        }

        if (block == Blocks.GOLD_BLOCK)
        {
            i = 6;
        }

        if (block == Blocks.WOOL)
        {
            i = 7;
        }

        if (block == Blocks.PACKED_ICE)
        {
            i = 8;
        }

        if (block == Blocks.BONE_BLOCK)
        {
            i = 9;
        }

        return i;
    }

    // RSMM
    @Override
    public boolean logPoweredOnBlockUpdate() {
        return false;
    }

    // RSMM
    @Override
    public boolean isActive(World world, BlockPos pos, IBlockState state) {
        TileEntity blockEntity = world.getTileEntity(pos);

        if (blockEntity instanceof TileEntityNote) {
            return ((TileEntityNote)blockEntity).previousRedstoneState;
        }

        return false;
    }
}