package net.minecraft.tileentity;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import carpet.CarpetSettings;
import carpet.utils.PistonFixes;
import net.minecraft.block.ITileEntityProvider;

public class TileEntityPiston extends TileEntity implements ITickable
{
    private IBlockState pistonState;
    private EnumFacing pistonFacing;
    /** if this piston is extending or not */
    private boolean extending;
    private boolean shouldHeadBeRendered;

    // Movable Tile entity fix CARPET-2No2Name
    private TileEntity carriedTileEntity;
    // [CM] Piston ghost blocks fix
    public long lastTicked;

    private static final ThreadLocal<EnumFacing> MOVING_ENTITY = new ThreadLocal<EnumFacing>()
    {
        protected EnumFacing initialValue()
        {
            return null;
        }
    };
    private float progress;
    /** the progress in (de)extending */
    public float lastProgress; // [CM] Change access to public for piston ghost blocks fix

    public TileEntityPiston()
    {
    }

    public TileEntityPiston(IBlockState pistonStateIn, EnumFacing pistonFacingIn, boolean extendingIn, boolean shouldHeadBeRenderedIn)
    {
        this.pistonState = pistonStateIn;
        this.pistonFacing = pistonFacingIn;
        this.extending = extendingIn;
        this.shouldHeadBeRendered = shouldHeadBeRenderedIn;
    }

        //CM
    public String cm_name() { return "Piston"; }

    public IBlockState getPistonState()
    {
        return this.pistonState;
    }

    /**
     * Get an NBT compound to sync to the client with SPacketChunkData, used for initial loading of the chunk or when
     * many blocks change at once. This compound comes back to you clientside in {@link handleUpdateTag}
     */
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    public int getBlockMetadata()
    {
        return 0;
    }

    /**
     * Returns true if a piston is extending
     */
    public boolean isExtending()
    {
        return this.extending;
    }

    public EnumFacing getFacing()
    {
        return this.pistonFacing;
    }

    public boolean shouldPistonHeadBeRendered()
    {
        return this.shouldHeadBeRendered;
    }

    private float getExtendedProgress(float p_184320_1_)
    {
        return this.extending ? p_184320_1_ - 1.0F : 1.0F - p_184320_1_;
    }

    public AxisAlignedBB getAABB(IBlockAccess p_184321_1_, BlockPos p_184321_2_)
    {
        return this.getAABB(p_184321_1_, p_184321_2_, this.progress).union(this.getAABB(p_184321_1_, p_184321_2_, this.lastProgress));
    }

    public AxisAlignedBB getAABB(IBlockAccess p_184319_1_, BlockPos p_184319_2_, float p_184319_3_)
    {
        p_184319_3_ = this.getExtendedProgress(p_184319_3_);
        IBlockState iblockstate = this.getCollisionRelatedBlockState();
        return iblockstate.getBoundingBox(p_184319_1_, p_184319_2_).offset((double)(p_184319_3_ * (float)this.pistonFacing.getXOffset()), (double)(p_184319_3_ * (float)this.pistonFacing.getYOffset()), (double)(p_184319_3_ * (float)this.pistonFacing.getZOffset()));
    }

    private IBlockState getCollisionRelatedBlockState()
    {
        return !this.isExtending() && this.shouldPistonHeadBeRendered() ? Blocks.PISTON_HEAD.getDefaultState().withProperty(BlockPistonExtension.TYPE, this.pistonState.getBlock() == Blocks.STICKY_PISTON ? BlockPistonExtension.EnumPistonType.STICKY : BlockPistonExtension.EnumPistonType.DEFAULT).withProperty(BlockPistonExtension.FACING, this.pistonState.getValue(BlockPistonBase.FACING)) : this.pistonState;
    }

    /* carpet mod moving entities selector */
    private void moveCollidedEntities(float p_184322_1_)
    {
        if (CarpetSettings.pocketPushing)
        {
            moveCollidedEntities_old();
        }
        else
        {
            moveCollidedEntities_new(p_184322_1_);
        }
    }
    //CM end

    private void moveCollidedEntities_new(float p_184322_1_)
    {
        EnumFacing enumfacing = this.extending ? this.pistonFacing : this.pistonFacing.getOpposite();
        double d0 = (double)(p_184322_1_ - this.progress);
        List<AxisAlignedBB> list = Lists.<AxisAlignedBB>newArrayList();
        this.getCollisionRelatedBlockState().addCollisionBoxToList(this.world, BlockPos.ORIGIN, new AxisAlignedBB(BlockPos.ORIGIN), list, (Entity)null, true);

        if (!list.isEmpty())
        {
            AxisAlignedBB axisalignedbb = this.moveByPositionAndProgress(this.getMinMaxPiecesAABB(list));
            List<Entity> list1 = this.world.getEntitiesWithinAABBExcludingEntity((Entity)null, this.getMovementArea(axisalignedbb, enumfacing, d0).union(axisalignedbb));

            if (!list1.isEmpty())
            {
                boolean flag = this.pistonState.getBlock() == Blocks.SLIME_BLOCK;

                for (int i = 0; i < list1.size(); ++i)
                {
                    Entity entity = list1.get(i);

                    if (entity.getPushReaction() != EnumPushReaction.IGNORE)
                    {
                        if (flag)
                        {
                            switch (enumfacing.getAxis())
                            {
                                case X:
                                    entity.motionX = (double)enumfacing.getXOffset();
                                    break;
                                case Y:
                                    entity.motionY = (double)enumfacing.getYOffset();
                                    break;
                                case Z:
                                    entity.motionZ = (double)enumfacing.getZOffset();
                            }
                        }

                        double d1 = 0.0D;

                        for (int j = 0; j < list.size(); ++j)
                        {
                            AxisAlignedBB axisalignedbb1 = this.getMovementArea(this.moveByPositionAndProgress(list.get(j)), enumfacing, d0);
                            AxisAlignedBB axisalignedbb2 = entity.getEntityBoundingBox();

                            if (axisalignedbb1.intersects(axisalignedbb2))
                            {
                                d1 = Math.max(d1, this.getMovement(axisalignedbb1, enumfacing, axisalignedbb2));

                                if (d1 >= d0)
                                {
                                    break;
                                }
                            }
                        }

                        if (d1 > 0.0D)
                        {
                            d1 = Math.min(d1, d0) + 0.01D;
                            MOVING_ENTITY.set(enumfacing);
                            entity.move(MoverType.PISTON, d1 * (double)enumfacing.getXOffset(), d1 * (double)enumfacing.getYOffset(), d1 * (double)enumfacing.getZOffset());
                            MOVING_ENTITY.set(null);

                            if (!this.extending && this.shouldHeadBeRendered)
                            {
                                this.fixEntityWithinPistonBase(entity, enumfacing, d0);
                            }

                            if(CarpetSettings.unloadedEntityFix)
                            {
                                world.updateEntityWithOptionalForce(entity, false);
                            }
                        }
                    }
                }
            }
        }
    }

    //CM added this old 1.10 behaviour
    private void moveCollidedEntities_old()
    {
        AxisAlignedBB axisalignedbb = this.getAABB(this.world, this.pos).offset(this.pos);
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity((Entity)null, axisalignedbb);

        if (!list.isEmpty())
        {
            EnumFacing enumfacing = this.extending ? this.pistonFacing : this.pistonFacing.getOpposite();

            for (int i = 0; i < list.size(); ++i)
            {
                Entity entity = (Entity)list.get(i);

                if (entity.getPushReaction() != EnumPushReaction.IGNORE)
                {
                    if (this.pistonState.getBlock() == Blocks.SLIME_BLOCK)
                    {
                        switch (enumfacing.getAxis())
                        {
                            case X:
                                entity.motionX = (double)enumfacing.getXOffset();
                                break;

                            case Y:
                                entity.motionY = (double)enumfacing.getYOffset();
                                break;

                            case Z:
                                entity.motionZ = (double)enumfacing.getZOffset();
                        }
                    }

                    double d0 = 0.0D;
                    double d1 = 0.0D;
                    double d2 = 0.0D;
                    AxisAlignedBB axisalignedbb1 = entity.getEntityBoundingBox();

                    switch (enumfacing.getAxis())
                    {
                        case X:
                            if (enumfacing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE)
                            {
                                d0 = axisalignedbb.maxX - axisalignedbb1.minX;
                            }
                            else
                            {
                                d0 = axisalignedbb1.maxX - axisalignedbb.minX;
                            }

                            d0 = d0 + 0.01D;
                            break;

                        case Y:
                            if (enumfacing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE)
                            {
                                d1 = axisalignedbb.maxY - axisalignedbb1.minY;
                            }
                            else
                            {
                                d1 = axisalignedbb1.maxY - axisalignedbb.minY;
                            }

                            d1 = d1 + 0.01D;
                            break;

                        case Z:
                            if (enumfacing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE)
                            {
                                d2 = axisalignedbb.maxZ - axisalignedbb1.minZ;
                            }
                            else
                            {
                                d2 = axisalignedbb1.maxZ - axisalignedbb.minZ;
                            }

                            d2 = d2 + 0.01D;
                    }

                    entity.move(MoverType.SELF, d0 * (double)enumfacing.getXOffset(), d1 * (double)enumfacing.getYOffset(), d2 * (double)enumfacing.getZOffset());
                }
            }
        }
    }
    //CM end

    private AxisAlignedBB getMinMaxPiecesAABB(List<AxisAlignedBB> p_191515_1_)
    {
        double d0 = 0.0D;
        double d1 = 0.0D;
        double d2 = 0.0D;
        double d3 = 1.0D;
        double d4 = 1.0D;
        double d5 = 1.0D;

        for (AxisAlignedBB axisalignedbb : p_191515_1_)
        {
            d0 = Math.min(axisalignedbb.minX, d0);
            d1 = Math.min(axisalignedbb.minY, d1);
            d2 = Math.min(axisalignedbb.minZ, d2);
            d3 = Math.max(axisalignedbb.maxX, d3);
            d4 = Math.max(axisalignedbb.maxY, d4);
            d5 = Math.max(axisalignedbb.maxZ, d5);
        }

        return new AxisAlignedBB(d0, d1, d2, d3, d4, d5);
    }

    private double getMovement(AxisAlignedBB p_190612_1_, EnumFacing facing, AxisAlignedBB p_190612_3_)
    {
        switch (facing.getAxis())
        {
            case X:
                return getDeltaX(p_190612_1_, facing, p_190612_3_);
            case Y:
            default:
                return getDeltaY(p_190612_1_, facing, p_190612_3_);
            case Z:
                return getDeltaZ(p_190612_1_, facing, p_190612_3_);
        }
    }

    private AxisAlignedBB moveByPositionAndProgress(AxisAlignedBB p_190607_1_)
    {
        double d0 = (double)this.getExtendedProgress(this.progress);
        return p_190607_1_.offset((double)this.pos.getX() + d0 * (double)this.pistonFacing.getXOffset(), (double)this.pos.getY() + d0 * (double)this.pistonFacing.getYOffset(), (double)this.pos.getZ() + d0 * (double)this.pistonFacing.getZOffset());
    }

    private AxisAlignedBB getMovementArea(AxisAlignedBB p_190610_1_, EnumFacing p_190610_2_, double p_190610_3_)
    {
        double d0 = p_190610_3_ * (double)p_190610_2_.getAxisDirection().getOffset();
        double d1 = Math.min(d0, 0.0D);
        double d2 = Math.max(d0, 0.0D);

        switch (p_190610_2_)
        {
            case WEST:
                return new AxisAlignedBB(p_190610_1_.minX + d1, p_190610_1_.minY, p_190610_1_.minZ, p_190610_1_.minX + d2, p_190610_1_.maxY, p_190610_1_.maxZ);
            case EAST:
                return new AxisAlignedBB(p_190610_1_.maxX + d1, p_190610_1_.minY, p_190610_1_.minZ, p_190610_1_.maxX + d2, p_190610_1_.maxY, p_190610_1_.maxZ);
            case DOWN:
                return new AxisAlignedBB(p_190610_1_.minX, p_190610_1_.minY + d1, p_190610_1_.minZ, p_190610_1_.maxX, p_190610_1_.minY + d2, p_190610_1_.maxZ);
            case UP:
            default:
                return new AxisAlignedBB(p_190610_1_.minX, p_190610_1_.maxY + d1, p_190610_1_.minZ, p_190610_1_.maxX, p_190610_1_.maxY + d2, p_190610_1_.maxZ);
            case NORTH:
                return new AxisAlignedBB(p_190610_1_.minX, p_190610_1_.minY, p_190610_1_.minZ + d1, p_190610_1_.maxX, p_190610_1_.maxY, p_190610_1_.minZ + d2);
            case SOUTH:
                return new AxisAlignedBB(p_190610_1_.minX, p_190610_1_.minY, p_190610_1_.maxZ + d1, p_190610_1_.maxX, p_190610_1_.maxY, p_190610_1_.maxZ + d2);
        }
    }

    private void fixEntityWithinPistonBase(Entity p_190605_1_, EnumFacing p_190605_2_, double p_190605_3_)
    {
        AxisAlignedBB axisalignedbb = p_190605_1_.getEntityBoundingBox();
        AxisAlignedBB axisalignedbb1 = Block.FULL_BLOCK_AABB.offset(this.pos);

        if (axisalignedbb.intersects(axisalignedbb1))
        {
            EnumFacing enumfacing = p_190605_2_.getOpposite();
            double d0 = this.getMovement(axisalignedbb1, enumfacing, axisalignedbb) + 0.01D;
            double d1 = this.getMovement(axisalignedbb1, enumfacing, axisalignedbb.intersect(axisalignedbb1)) + 0.01D;

            if (Math.abs(d0 - d1) < 0.01D)
            {
                d0 = Math.min(d0, p_190605_3_) + 0.01D;
                MOVING_ENTITY.set(p_190605_2_);
                p_190605_1_.move(MoverType.PISTON, d0 * (double)enumfacing.getXOffset(), d0 * (double)enumfacing.getYOffset(), d0 * (double)enumfacing.getZOffset());
                MOVING_ENTITY.set(null);
            }
        }
    }

    private static double getDeltaX(AxisAlignedBB p_190611_0_, EnumFacing facing, AxisAlignedBB p_190611_2_)
    {
        return facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? p_190611_0_.maxX - p_190611_2_.minX : p_190611_2_.maxX - p_190611_0_.minX;
    }

    private static double getDeltaY(AxisAlignedBB p_190608_0_, EnumFacing facing, AxisAlignedBB p_190608_2_)
    {
        return facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? p_190608_0_.maxY - p_190608_2_.minY : p_190608_2_.maxY - p_190608_0_.minY;
    }

    private static double getDeltaZ(AxisAlignedBB p_190604_0_, EnumFacing facing, AxisAlignedBB p_190604_2_)
    {
        return facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? p_190604_0_.maxZ - p_190604_2_.minZ : p_190604_2_.maxZ - p_190604_0_.minZ;
    }

    /**
     * removes a piston's tile entity (and if the piston is moving, stops it)
     */
    public void clearPistonTileEntity()
    {
        if (this.lastProgress < 1.0F && this.world != null)
        {
            this.progress = 1.0F;
            this.lastProgress = this.progress;
            this.world.removeTileEntity(this.pos);
            this.invalidate();

            if (this.world.getBlockState(this.pos).getBlock() == Blocks.PISTON_EXTENSION)
            {
                // Movable Tile entity fix CARPET-2No2Name
                if(CarpetSettings.movableTileEntities || CarpetSettings.autocrafter)
                {
                    this.placeBlock();
                }
                else
                {
                    //vanilla behaviour
                    this.world.setBlockState(this.pos, this.pistonState, 3);
                    this.world.neighborChanged(this.pos, this.pistonState.getBlock(), this.pos);
                }
            }
            else if(CarpetSettings.movableTileEntities || CarpetSettings.autocrafter)
            {
                //Handle TNT Explosions or other ways the movingBlock is broken
                //Also /setblock will cause this to be called, and drop e.g. a moving chest's contents. This is MC-40380 (BlockEntities that aren't Inventories drop stuff when setblock is called )
                if (this.carriedTileEntity != null && this.world.getBlockState(this.pos).getBlock() == Blocks.AIR) {
                  this.placeBlock();
                  this.world.setBlockToAir(this.pos);
                }
            }
        }
    }

    /**
     * Like the old updateEntity(), except more generic.
     */
    public void update()
    {
        // CM START
        if (CarpetSettings.pistonClippingFix > 0) {
            PistonFixes.synchronizeClient();
        }
        // [CM] Piston ghost blocks fix
        this.lastTicked = this.world.getTotalWorldTime();
        // CM END
        this.lastProgress = this.progress;

        if (this.lastProgress >= 1.0F)
        {
            if (carpet.carpetclient.CarpetClientChunkLogger.logger.enabled)
                carpet.carpetclient.CarpetClientChunkLogger.setReason("Piston block finishes moving");
            this.world.removeTileEntity(this.pos);
            carpet.carpetclient.CarpetClientChunkLogger.resetReason();
            this.invalidate();

            if (this.world.getBlockState(this.pos).getBlock() == Blocks.PISTON_EXTENSION)
            {
                //CM piston_ghost_blocks
                if (CarpetSettings.pistonGhostBlocksFix == CarpetSettings.PistonGhostBlocksFix.serverOnly)
                {
                    IBlockState blockstate = this.world.getBlockState(this.pos);
                    this.world.notifyBlockUpdate(pos.offset(blockstate.getValue(BlockPistonExtension.FACING).getOpposite()), blockstate, blockstate, 0);
                }
                //CM end
                
                // Movable Tile entity fix CARPET-2No2Name
                if(CarpetSettings.movableTileEntities || CarpetSettings.autocrafter)
                {
                    this.placeBlock();
                }
                else //vanilla finally
                {
                    this.world.setBlockState(this.pos, this.pistonState, 3);
                    this.world.neighborChanged(this.pos, this.pistonState.getBlock(), this.pos);
                }
            }
        }
        else
        {
            float f = this.progress + 0.5F;
            this.moveCollidedEntities(f);
            this.progress = f;

            if (this.progress >= 1.0F)
            {
                this.progress = 1.0F;
            }
        }
    }

    public static void registerFixesPiston(DataFixer fixer)
    {
    }

    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        this.pistonState = Block.getBlockById(compound.getInteger("blockId")).getStateFromMeta(compound.getInteger("blockData"));
        this.pistonFacing = EnumFacing.byIndex(compound.getInteger("facing"));
        this.progress = compound.getFloat("progress");
        this.lastProgress = this.progress;
        if (CarpetSettings.pistonSerializationFix && compound.hasKey("lastProgress", 5)) // CM: piston serialization fix
            this.lastProgress = compound.getFloat("lastProgress");
        this.extending = compound.getBoolean("extending");
        this.shouldHeadBeRendered = compound.getBoolean("source");

        // Movable Tile entity fix CARPET-2No2Name
        if((CarpetSettings.movableTileEntities || CarpetSettings.autocrafter) && compound.hasKey("carriedTileEntity", 10))
        {
            if(this.pistonState.getBlock() instanceof ITileEntityProvider)
                this.carriedTileEntity = ((ITileEntityProvider)(this.pistonState.getBlock())).createNewTileEntity(this.world, this.pistonState.getBlock().getMetaFromState(this.pistonState));
            if(carriedTileEntity != null) //Can actually be null, as BlockPistonMoving.createNewTileEntity(...) returns null
                this.carriedTileEntity.readFromNBT(compound.getCompoundTag("carriedTileEntity"));
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        compound.setInteger("blockId", Block.getIdFromBlock(this.pistonState.getBlock()));
        compound.setInteger("blockData", this.pistonState.getBlock().getMetaFromState(this.pistonState));
        compound.setInteger("facing", this.pistonFacing.getIndex());
        if (CarpetSettings.pistonSerializationFix) // CM: piston serialization fix
        {
            compound.setFloat("progress", this.progress);
            compound.setFloat("lastProgress", this.lastProgress);
        }
        else
        {
        compound.setFloat("progress", this.lastProgress);
        }
        compound.setBoolean("extending", this.extending);
        compound.setBoolean("source", this.shouldHeadBeRendered);

        // Movable Tile entity fix CARPET-2No2Name
        if((CarpetSettings.movableTileEntities || CarpetSettings.autocrafter) && this.carriedTileEntity != null)
        {
            compound.setTag("carriedTileEntity", this.carriedTileEntity.writeToNBT(new NBTTagCompound()));
        }

        return compound;
    }

    public void addCollissionAABBs(World p_190609_1_, BlockPos p_190609_2_, AxisAlignedBB p_190609_3_, List<AxisAlignedBB> p_190609_4_, @Nullable Entity p_190609_5_)
    {
        if (!this.extending && this.shouldHeadBeRendered)
        {
            this.pistonState.withProperty(BlockPistonBase.EXTENDED, Boolean.valueOf(true)).addCollisionBoxToList(p_190609_1_, p_190609_2_, p_190609_3_, p_190609_4_, p_190609_5_, false);
        }

        EnumFacing enumfacing = MOVING_ENTITY.get();

        if ((double)this.progress >= 1.0D || enumfacing != (this.extending ? this.pistonFacing : this.pistonFacing.getOpposite()))
        {
            int i = p_190609_4_.size();
            IBlockState iblockstate;

            if (this.shouldPistonHeadBeRendered())
            {
                iblockstate = Blocks.PISTON_HEAD.getDefaultState().withProperty(BlockPistonExtension.FACING, this.pistonFacing).withProperty(BlockPistonExtension.SHORT, Boolean.valueOf(this.extending != 1.0F - this.progress < 0.25F));
            }
            else
            {
                iblockstate = this.pistonState;
            }

            float f = this.getExtendedProgress(this.progress);
            double d0 = (double)((float)this.pistonFacing.getXOffset() * f);
            double d1 = (double)((float)this.pistonFacing.getYOffset() * f);
            double d2 = (double)((float)this.pistonFacing.getZOffset() * f);
            iblockstate.addCollisionBoxToList(p_190609_1_, p_190609_2_, p_190609_3_.offset(-d0, -d1, -d2), p_190609_4_, p_190609_5_, true);

            for (int j = i; j < p_190609_4_.size(); ++j)
            {
                p_190609_4_.set(j, ((AxisAlignedBB)p_190609_4_.get(j)).offset(d0, d1, d2));
            }
        }
    }

    // Movable Tile entity fix CARPET-2No2Name
    public void setCarriedTileEntity(TileEntity tileEntity)
    {
        this.carriedTileEntity = tileEntity;
    }
    // Movable Tile entity fix CARPET-2No2Name
    private void placeBlock() {
        this.world.setBlockState(this.pos, this.pistonState, 18); //Flag 18 => No block updates, TileEntity has to be placed first

        if(!this.world.isRemote)
        {
        if(carriedTileEntity != null) {
            this.world.removeTileEntity(this.pos);
            carriedTileEntity.validate();
            this.world.setTileEntity(this.pos, carriedTileEntity);
        }

        //Update neighbors, comparators and observers now (same order as setBlockState would have if flag was set to 3 (default))
        //This should not change piston behavior for vanilla-pushable blocks at all

        this.world.notifyNeighborsRespectDebug(pos, Blocks.PISTON_EXTENSION, true);
        if (this.pistonState.hasComparatorInputOverride())
        {
        this.world.updateComparatorOutputLevel(pos, this.pistonState.getBlock());
        }
        this.world.updateObservingBlocksAt(pos, this.pistonState.getBlock());
        }
        this.world.neighborChanged(this.pos, this.pistonState.getBlock(), this.pos);
    }
}