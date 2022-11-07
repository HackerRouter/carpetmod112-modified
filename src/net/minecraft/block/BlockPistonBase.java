package net.minecraft.block;

import com.google.common.collect.Lists;

import java.util.HashSet;
import java.util.List;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.booleans.BooleanStack;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import narcolepticfrog.rsmm.events.PistonPushEventDispatcher;
import carpet.CarpetSettings;
import carpet.utils.PistonFixes;
import net.minecraft.world.WorldServer;

import redstone.multimeter.block.MeterableBlock;
import redstone.multimeter.helper.WorldHelper;
import redstone.multimeter.server.Multimeter;

public class BlockPistonBase extends BlockDirectional implements MeterableBlock /* RSMM */
{
    public static final PropertyBool EXTENDED = PropertyBool.create("extended");
    protected static final AxisAlignedBB PISTON_BASE_EAST_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.75D, 1.0D, 1.0D);
    protected static final AxisAlignedBB PISTON_BASE_WEST_AABB = new AxisAlignedBB(0.25D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected static final AxisAlignedBB PISTON_BASE_SOUTH_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.75D);
    protected static final AxisAlignedBB PISTON_BASE_NORTH_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.25D, 1.0D, 1.0D, 1.0D);
    protected static final AxisAlignedBB PISTON_BASE_UP_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D);
    protected static final AxisAlignedBB PISTON_BASE_DOWN_AABB = new AxisAlignedBB(0.0D, 0.25D, 0.0D, 1.0D, 1.0D, 1.0D);
    /** This piston is the sticky one? */
    private final boolean isSticky;
    private static HashSet<BlockPos> dupeFixLocations = new HashSet<>();

    public BlockPistonBase(boolean isSticky)
    {
        super(Material.PISTON);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(EXTENDED, Boolean.valueOf(false)));
        this.isSticky = isSticky;
        this.setSoundType(SoundType.STONE);
        this.setHardness(0.5F);
        this.setCreativeTab(CreativeTabs.REDSTONE);
    }

    /**
     * @deprecated call via {@link IBlockState#causesSuffocation()} whenever possible. Implementing/overriding is fine.
     */
    public boolean causesSuffocation(IBlockState state)
    {
        return !((Boolean)state.getValue(EXTENDED)).booleanValue();
    }

    /**
     * @deprecated call via {@link IBlockState#getBoundingBox(IBlockAccess,BlockPos)} whenever possible.
     * Implementing/overriding is fine.
     */
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos)
    {
        if (((Boolean)state.getValue(EXTENDED)).booleanValue())
        {
            switch ((EnumFacing)state.getValue(FACING))
            {
                case DOWN:
                    return PISTON_BASE_DOWN_AABB;
                case UP:
                default:
                    return PISTON_BASE_UP_AABB;
                case NORTH:
                    return PISTON_BASE_NORTH_AABB;
                case SOUTH:
                    return PISTON_BASE_SOUTH_AABB;
                case WEST:
                    return PISTON_BASE_WEST_AABB;
                case EAST:
                    return PISTON_BASE_EAST_AABB;
            }
        }
        else
        {
            return FULL_BLOCK_AABB;
        }
    }

    /**
     * Determines if the block is solid enough on the top side to support other blocks, like redstone components.
     * @deprecated prefer calling {@link IBlockState#isTopSolid()} wherever possible
     */
    public boolean isTopSolid(IBlockState state)
    {
        return !((Boolean)state.getValue(EXTENDED)).booleanValue() || state.getValue(FACING) == EnumFacing.DOWN;
    }

    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState)
    {
        addCollisionBoxToList(pos, entityBox, collidingBoxes, state.getBoundingBox(worldIn, pos));
    }

    /**
     * Used to determine ambient occlusion and culling when rebuilding chunks for render
     * @deprecated call via {@link IBlockState#isOpaqueCube()} whenever possible. Implementing/overriding is fine.
     */
    public boolean isOpaqueCube(IBlockState state)
    {
        return false;
    }

    /**
     * Called by ItemBlocks after a block is set in the world, to allow post-place logic
     */
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        if (!CarpetSettings.accurateBlockPlacement)
            worldIn.setBlockState(pos, state.withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer)), 2);

        if (!worldIn.isRemote)
        {
            this.checkForMove(worldIn, pos, state);
        }
    }

    /**
     * Called when a neighboring block was changed and marks that this state should perform any checks during a neighbor
     * change. Cases may include when redstone power is updated, cactus blocks popping off due to a neighboring solid
     * block, etc.
     */
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos)
    {
        if (!worldIn.isRemote)
        {
            this.checkForMove(worldIn, pos, state);
        }
    }

    /**
     * Called after the block is set in the Chunk data, but before the Tile Entity is set
     */
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state)
    {
        if (!worldIn.isRemote && worldIn.getTileEntity(pos) == null)
        {
            this.checkForMove(worldIn, pos, state);
        }
    }

    /**
     * Called by ItemBlocks just before a block is actually set in the world, to allow for adjustments to the
     * IBlockstate
     */
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer)
    {
        return this.getDefaultState().withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer)).withProperty(EXTENDED, Boolean.valueOf(false));
    }

    private void checkForMove(World worldIn, BlockPos pos, IBlockState state)
    {
        if (carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
            carpet.carpetclient.CarpetClientChunkLogger.setReason("Piston scheduled by power source");

        EnumFacing enumfacing = (EnumFacing)state.getValue(FACING);
        boolean flag = this.shouldBeExtended(worldIn, pos, enumfacing);

        boolean extended = state.getValue(EXTENDED); // CM only get blockstate value once

        // CM START
        if (CarpetSettings.pistonClippingFix > 0 && flag != extended)
            PistonFixes.synchronizeClient();
        // CM END

        if (flag && !extended)
        {
            if ((new BlockPistonStructureHelper(worldIn, pos, enumfacing, true)).canMove())
            {
                worldIn.addBlockEvent(pos, this, 0, enumfacing.getIndex());
            }
        }
        else if (!flag && extended)
        {
            // Double retraction for pistons CARPET-XCOM
            if(CarpetSettings.doubleRetraction)
            {
                worldIn.setBlockState(pos,  state.withProperty(EXTENDED,  Boolean.valueOf(false)), 2);
            }
            
            // [CM] Piston ghost blocks fix - start
            int suppress_move = 0;
            if (CarpetSettings.pistonGhostBlocksFix == CarpetSettings.PistonGhostBlocksFix.clientAndServer)
            {
                final EnumFacing facing = state.getValue(FACING);
    
                final BlockPos blockpos = new BlockPos(pos).offset(facing, 2);
                final IBlockState iblockstate = worldIn.getBlockState(blockpos);
    
                if (iblockstate.getBlock() == Blocks.PISTON_EXTENSION)
                {
                    final TileEntity tileentity = worldIn.getTileEntity(blockpos);
        
                    if (tileentity instanceof TileEntityPiston)
                    {
                        final TileEntityPiston tileentitypiston = (TileEntityPiston) tileentity;
                        if (tileentitypiston.getFacing() == facing && tileentitypiston.isExtending()
                                    && (tileentitypiston.lastProgress < 0.5F
                                                || tileentitypiston.getWorld().getTotalWorldTime() == tileentitypiston.lastTicked
                                                || !((WorldServer) worldIn).haveBlockActionsProcessed()))
                        {
                            suppress_move = 16;
                        }
                    }
                }
            }
            worldIn.addBlockEvent(pos, this, 1,  enumfacing.getIndex() | suppress_move); // [CM] Piston ghost blocks fix - | suppress_move
            // [CM] Piston ghost blocks fix - end
        }

        carpet.carpetclient.CarpetClientChunkLogger.resetReason();
    }

    /*
     * This if statement checks if the the pulling block (block that is 2 blocks infront of the extended piston)
     * is a non-moving block and returns a meta value of 16 so it can tell the client to ignore pulling blocks 
     * even if the client can pull them. CARPET-XCOM
     */
    private int ignoreMovingBlockMeta(World worldIn, BlockPos pos, EnumFacing enumfacing) {
        BlockPos blockpos = pos.add(enumfacing.getXOffset() * 2, enumfacing.getYOffset() * 2, enumfacing.getZOffset() * 2);
        IBlockState iblockstate = worldIn.getBlockState(blockpos);
        Block block = iblockstate.getBlock();
        
        if (block == Blocks.PISTON_EXTENSION) return 16;
        
        return 0;
    }

    // RSMM - wrapped method
    public boolean shouldBeExtended(World worldIn, BlockPos pos, EnumFacing facing)
    {
        boolean powered = _shouldBeExtended(worldIn, pos, facing);
        logPowered(worldIn, pos, powered); // RSMM
        return powered;
    }

    private boolean _shouldBeExtended(World worldIn, BlockPos pos, EnumFacing facing)
    {
        for (EnumFacing enumfacing : EnumFacing.values())
        {
            if (enumfacing != facing && worldIn.isSidePowered(pos.offset(enumfacing), enumfacing))
            {
                return true;
            }
        }

        if (worldIn.isSidePowered(pos, EnumFacing.DOWN))
        {
            return true;
        }
        else
        {
            /* carpet mod */
            if (!CarpetSettings.quasiConnectivity)
            {
                return false;
            }
            /* end */
            BlockPos blockpos = pos.up();

            for (EnumFacing enumfacing1 : EnumFacing.values())
            {
                if (enumfacing1 != EnumFacing.DOWN && worldIn.isSidePowered(blockpos.offset(enumfacing1), enumfacing1))
                {
                    return true;
                }
            }

            return false;
        }
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
        EnumFacing enumfacing = (EnumFacing)state.getValue(FACING);

        if (!worldIn.isRemote)
        {
            boolean flag = this.shouldBeExtended(worldIn, pos, enumfacing);

            if (flag && id == 1)
            {
                worldIn.setBlockState(pos, state.withProperty(EXTENDED, Boolean.valueOf(true)), 2);
                return false;
            }

            if (!flag && id == 0)
            {
                return false;
            }
        }

        if (id == 0)
        {
            if (!this.doMove(worldIn, pos, enumfacing, true))
            {
                return false;
            }

            worldIn.setBlockState(pos, state.withProperty(EXTENDED, Boolean.valueOf(true)), 3);
            worldIn.playSound((EntityPlayer)null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.5F, worldIn.rand.nextFloat() * 0.25F + 0.6F);
        }
        else if (id == 1)
        {
            TileEntity tileentity1 = worldIn.getTileEntity(pos.offset(enumfacing));

            if (tileentity1 instanceof TileEntityPiston)
            {
                ((TileEntityPiston)tileentity1).clearPistonTileEntity();
            }

            worldIn.setBlockState(pos, Blocks.PISTON_EXTENSION.getDefaultState().withProperty(BlockPistonMoving.FACING, enumfacing).withProperty(BlockPistonMoving.TYPE, this.isSticky ? BlockPistonExtension.EnumPistonType.STICKY : BlockPistonExtension.EnumPistonType.DEFAULT), 3);
            worldIn.setTileEntity(pos, BlockPistonMoving.createTilePiston(this.getStateFromMeta(param), enumfacing, false, true));

            if (this.isSticky)
            {
                BlockPos blockpos = pos.add(enumfacing.getXOffset() * 2, enumfacing.getYOffset() * 2, enumfacing.getZOffset() * 2);
                IBlockState iblockstate = worldIn.getBlockState(blockpos);
                Block block = iblockstate.getBlock();
                boolean flag1 = false;

                if (block == Blocks.PISTON_EXTENSION)
                {
                    TileEntity tileentity = worldIn.getTileEntity(blockpos);

                    if (tileentity instanceof TileEntityPiston)
                    {
                        TileEntityPiston tileentitypiston = (TileEntityPiston)tileentity;

                        if (tileentitypiston.getFacing() == enumfacing && tileentitypiston.isExtending())
                        {
                            tileentitypiston.clearPistonTileEntity();
                            flag1 = true;
                        }
                    }
                }
                
                // [CM] Piston ghost blocks fix
                if ((param & 16) == 16 && CarpetSettings.pistonGhostBlocksFix == CarpetSettings.PistonGhostBlocksFix.clientAndServer)
                    flag1 = true;
                
                if (!flag1 && iblockstate.getMaterial() != Material.AIR && canPush(iblockstate, worldIn, blockpos, enumfacing.getOpposite(), false, enumfacing) && (iblockstate.getPushReaction() == EnumPushReaction.NORMAL || block == Blocks.PISTON || block == Blocks.STICKY_PISTON))
                {
                    this.doMove(worldIn, pos, enumfacing, false);
                }
            }
            else
            {
                worldIn.setBlockToAir(pos.offset(enumfacing));
            }

            worldIn.playSound((EntityPlayer)null, pos, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.5F, worldIn.rand.nextFloat() * 0.15F + 0.6F);
        }

        return true;
    }

    /**
     * @deprecated call via {@link IBlockState#isFullCube()} whenever possible. Implementing/overriding is fine.
     */
    public boolean isFullCube(IBlockState state)
    {
        return false;
    }

    @Nullable
    public static EnumFacing getFacing(int meta)
    {
        int i = meta & 7;
        return i > 5 ? null : EnumFacing.byIndex(i);
    }

    /**
     * Checks if the piston can push the given BlockState.
     */
    public static boolean canPush(IBlockState blockStateIn, World worldIn, BlockPos pos, EnumFacing facing, boolean destroyBlocks, EnumFacing p_185646_5_)
    {
        Block block = blockStateIn.getBlock();

        if (block == Blocks.OBSIDIAN)
        {
            return false;
        }
        else if (!worldIn.getWorldBorder().contains(pos))
        {
            return false;
        }
        else if (pos.getY() >= 0 && (facing != EnumFacing.DOWN || pos.getY() != 0))
        {
            if (pos.getY() <= worldIn.getHeight() - 1 && (facing != EnumFacing.UP || pos.getY() != worldIn.getHeight() - 1))
            {
                if (block != Blocks.PISTON && block != Blocks.STICKY_PISTON)
                {
                    if (blockStateIn.getBlockHardness(worldIn, pos) == -1.0F)
                    {
                        return false;
                    }

                    switch (blockStateIn.getPushReaction())
                    {
                        case BLOCK:
                            return false;
                        case DESTROY:
                            return destroyBlocks;
                        case PUSH_ONLY:
                            return facing == p_185646_5_;
                    }
                }
                else if (((Boolean)blockStateIn.getValue(EXTENDED)).booleanValue())
                {
                    return false;
                }

                // Movable Tile entity fix CARPET-2No2Name
                if(block instanceof BlockWorkbench) // auto crafting CAPRET-XCOM
                    return true;
                if(!block.hasTileEntity())
                    return true;
                else if(CarpetSettings.movableTileEntities)
                    return isPushableTileEntityBlock(block);
                else
                    return false;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return false;
        }
    }
    
    // Movable Tile entity fix CARPET-2No2Name
    private static boolean isPushableTileEntityBlock(Block block)
    {
        //Making PISTON_EXTENSION (BlockPistonMoving) pushable would not work as its createNewTileEntity()-method returns null
        return block != Blocks.ENDER_CHEST && block != Blocks.ENCHANTING_TABLE && block != Blocks.END_GATEWAY
        && block != Blocks.END_PORTAL && block != Blocks.MOB_SPAWNER && block != Blocks.PISTON_EXTENSION;
    }

    private boolean doMove(World worldIn, BlockPos pos, EnumFacing direction, boolean extending)
    {
        if (!extending)
        {
            worldIn.setBlockToAir(pos.offset(direction));
        }

        BlockPistonStructureHelper blockpistonstructurehelper = new BlockPistonStructureHelper(worldIn, pos, direction, extending);

        if (!blockpistonstructurehelper.canMove())
        {
            return false;
        }
        else
        {
            List<BlockPos> list = blockpistonstructurehelper.getBlocksToMove();
            List<IBlockState> list1 = Lists.<IBlockState>newArrayList();

            for (int i = 0; i < list.size(); ++i)
            {
                BlockPos blockpos = list.get(i);
                list1.add(worldIn.getBlockState(blockpos).getActualState(worldIn, blockpos));
                dupeFixLocations.add(blockpos); // Added for duping fix CARPET-XCOM
            }

            List<BlockPos> list2 = blockpistonstructurehelper.getBlocksToDestroy();
            int k = list.size() + list2.size();
            IBlockState[] aiblockstate = new IBlockState[k];
            EnumFacing enumfacing = extending ? direction : direction.getOpposite();

            for (int j = list2.size() - 1; j >= 0; --j)
            {
                BlockPos blockpos1 = list2.get(j);
                IBlockState iblockstate = worldIn.getBlockState(blockpos1);
                iblockstate.getBlock().dropBlockAsItem(worldIn, blockpos1, iblockstate, 0);
                worldIn.setBlockState(blockpos1, Blocks.AIR.getDefaultState(), 4);
                --k;
                aiblockstate[k] = iblockstate;
            }
            
         // Movable Tile entity fix CARPET-2No2Name
            List<TileEntity> list1_TileEntities = Lists.<TileEntity>newArrayList();
            if(CarpetSettings.movableTileEntities || CarpetSettings.autocrafter){
                for (int i = 0; i < list.size(); ++i)
                {
                    BlockPos blockpos = list.get(i);
                    TileEntity tileentity = worldIn.getTileEntity(blockpos);
                    list1_TileEntities.add(tileentity);

                    if(tileentity != null)
                    {
                        worldIn.removeTileEntity(blockpos);
                        tileentity.markDirty();
                    }
                }
            }
            
            for (int l = list.size() - 1; l >= 0; --l)
            {
                BlockPos blockpos3 = list.get(l);
                // ----- RSMM Start ------ //
                if (CarpetSettings.redstoneMultimeterLegacy) {
                    PistonPushEventDispatcher.dispatchEvent(worldIn, blockpos3, enumfacing);
                }
                if (CarpetSettings.redstoneMultimeter && !worldIn.isRemote) {
                    Multimeter multimeter = WorldHelper.getMultimeter();

                    multimeter.logMoved(worldIn, blockpos3, enumfacing);
                    multimeter.moveMeters(worldIn, blockpos3, enumfacing);
                }
                // ----- RSMM End ----- //
                IBlockState iblockstate2 = worldIn.getBlockState(blockpos3);
                worldIn.setBlockState(blockpos3, Blocks.AIR.getDefaultState(), 2);
                blockpos3 = blockpos3.offset(enumfacing);
                worldIn.setBlockState(blockpos3, Blocks.PISTON_EXTENSION.getDefaultState().withProperty(FACING, direction), 4);

                // Movable Tile entity fix CARPET-2No2Name
                if(CarpetSettings.autocrafter && iblockstate2 instanceof BlockWorkbench){
                    TileEntity tilePiston = BlockPistonMoving.createTilePiston(list1.get(l), direction, extending, false);
                    ((TileEntityPiston)tilePiston).setCarriedTileEntity(list1_TileEntities.get(l));
                    worldIn.setTileEntity(blockpos3, tilePiston);
                }else if(CarpetSettings.movableTileEntities && isPushableTileEntityBlock(iblockstate2.getBlock())){
                    TileEntity tilePiston = BlockPistonMoving.createTilePiston(list1.get(l), direction, extending, false);
                    ((TileEntityPiston)tilePiston).setCarriedTileEntity(list1_TileEntities.get(l));
                    worldIn.setTileEntity(blockpos3, tilePiston);
                }else{
                    worldIn.setTileEntity(blockpos3, BlockPistonMoving.createTilePiston(list1.get(l), direction, extending, false));
                }

                --k;
                aiblockstate[k] = iblockstate2;
            }

            BlockPos blockpos2 = pos.offset(direction);

            if (extending)
            {
                BlockPistonExtension.EnumPistonType blockpistonextension$enumpistontype = this.isSticky ? BlockPistonExtension.EnumPistonType.STICKY : BlockPistonExtension.EnumPistonType.DEFAULT;
                IBlockState iblockstate3 = Blocks.PISTON_HEAD.getDefaultState().withProperty(BlockPistonExtension.FACING, direction).withProperty(BlockPistonExtension.TYPE, blockpistonextension$enumpistontype);
                IBlockState iblockstate1 = Blocks.PISTON_EXTENSION.getDefaultState().withProperty(BlockPistonMoving.FACING, direction).withProperty(BlockPistonMoving.TYPE, this.isSticky ? BlockPistonExtension.EnumPistonType.STICKY : BlockPistonExtension.EnumPistonType.DEFAULT);
                worldIn.setBlockState(blockpos2, iblockstate1, 4);
                worldIn.setTileEntity(blockpos2, BlockPistonMoving.createTilePiston(iblockstate3, direction, true, true));
            }

            for (int i1 = list2.size() - 1; i1 >= 0; --i1)
            {
                worldIn.notifyNeighborsOfStateChange(list2.get(i1), aiblockstate[k++].getBlock(), false);
            }

            for (int j1 = list.size() - 1; j1 >= 0; --j1)
            {
                worldIn.notifyNeighborsOfStateChange(list.get(j1), aiblockstate[k++].getBlock(), false);
            }

            if (extending)
            {
                worldIn.notifyNeighborsOfStateChange(blockpos2, Blocks.PISTON_HEAD, false);
            }

            dupeFixLocations.clear(); // Added for duping fix CARPET-XCOM

            return true;
        }
    }

    /**
     * Convert the given metadata into a BlockState for this Block
     */
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(FACING, getFacing(meta)).withProperty(EXTENDED, Boolean.valueOf((meta & 8) > 0));
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    public int getMetaFromState(IBlockState state)
    {
        int i = 0;
        i = i | ((EnumFacing)state.getValue(FACING)).getIndex();

        if (((Boolean)state.getValue(EXTENDED)).booleanValue())
        {
            i |= 8;
        }

        return i;
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed
     * blockstate.
     * @deprecated call via {@link IBlockState#withRotation(Rotation)} whenever possible. Implementing/overriding is
     * fine.
     */
    public IBlockState withRotation(IBlockState state, Rotation rot)
    {
        return state.withProperty(FACING, rot.rotate((EnumFacing)state.getValue(FACING)));
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed
     * blockstate.
     * @deprecated call via {@link IBlockState#withMirror(Mirror)} whenever possible. Implementing/overriding is fine.
     */
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn)
    {
        return state.withRotation(mirrorIn.toRotation((EnumFacing)state.getValue(FACING)));
    }

    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, new IProperty[] {FACING, EXTENDED});
    }

    /**
     * Get the geometry of the queried face at the given position and state. This is used to decide whether things like
     * buttons are allowed to be placed on the face, or how glass panes connect to the face, among other things.
     * <p>
     * Common values are {@code SOLID}, which is the default, and {@code UNDEFINED}, which represents something that
     * does not fit the other descriptions and will generally cause other things not to connect to the face.
     * 
     * @return an approximation of the form of the given face
     * @deprecated call via {@link IBlockState#getBlockFaceShape(IBlockAccess,BlockPos,EnumFacing)} whenever possible.
     * Implementing/overriding is fine.
     */
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face)
    {
        state = this.getActualState(state, worldIn, pos);
        return state.getValue(FACING) != face.getOpposite() && ((Boolean)state.getValue(EXTENDED)).booleanValue() ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
    }

    // RSMM
    @Override
    public boolean logPoweredOnBlockUpdate() {
        return false;
    }

    // RSMM
    @Override
    public boolean isPowered(World world, BlockPos pos, IBlockState state) {
        return shouldBeExtended(world, pos, state.getValue(FACING));
    }

    // RSMM
    @Override
    public boolean isActive(World world, BlockPos pos, IBlockState state) {
        return state.getValue(EXTENDED);
    }

    // Added method for checking if block is being pushed for duping fixes CARPET-XCOM
    public static boolean isBeingPushed(BlockPos pos){
        return dupeFixLocations.contains(pos);
    }
}