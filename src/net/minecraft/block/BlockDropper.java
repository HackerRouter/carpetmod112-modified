package net.minecraft.block;

import carpet.CarpetSettings;
import carpet.helpers.AutoCraftingDropperHelper;
import carpet.utils.VoidContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class BlockDropper extends BlockDispenser
{
    private final IBehaviorDispenseItem dropBehavior = new BehaviorDefaultDispenseItem();

    protected IBehaviorDispenseItem getBehavior(ItemStack stack)
    {
        return this.dropBehavior;
    }

    /**
     * Returns a new instance of a block's tile entity class. Called on placing the block.
     */
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TileEntityDropper();
    }

    protected void dispense(World worldIn, BlockPos pos)
    {
        // [CM] Auto crafting table - start
        if (CarpetSettings.autoCraftingDropper)
        {
            if (this.autoCraftingDispense(worldIn, pos))
                return;
        }
        // [CM] Auto crafting table - end
        BlockSourceImpl blocksourceimpl = new BlockSourceImpl(worldIn, pos);
        TileEntityDispenser tileentitydispenser = (TileEntityDispenser)blocksourceimpl.getBlockTileEntity();

        if (tileentitydispenser != null)
        {
            int i = tileentitydispenser.getDispenseSlot();

            if (i < 0)
            {
                worldIn.playEvent(1001, pos, 0);
            }
            else
            {
                ItemStack itemstack = tileentitydispenser.getStackInSlot(i);

                if (!itemstack.isEmpty())
                {
                    EnumFacing enumfacing = (EnumFacing)worldIn.getBlockState(pos).getValue(FACING);
                    BlockPos blockpos = pos.offset(enumfacing);
                    IInventory iinventory = TileEntityHopper.getInventoryAtPosition(worldIn, (double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ());
                    ItemStack itemstack1;

                    if (iinventory == null)
                    {
                        itemstack1 = this.dropBehavior.dispense(blocksourceimpl, itemstack);
                    }
                    else
                    {
                        itemstack1 = TileEntityHopper.putStackInInventoryAllSlots(tileentitydispenser, iinventory, itemstack.copy().splitStack(1), enumfacing.getOpposite());

                        if (itemstack1.isEmpty())
                        {
                            itemstack1 = itemstack.copy();
                            itemstack1.shrink(1);
                        }
                        else
                        {
                            itemstack1 = itemstack.copy();
                        }
                    }

                    tileentitydispenser.setInventorySlotContents(i, itemstack1);
                }
            }
        }
    }
    
    // [CM] Auto crafting dropper
    /**
     * @deprecated call via {@link IBlockState#getComparatorInputOverride(World,BlockPos)} whenever possible.
     * Implementing/overriding is fine.
     */
    @Override
    public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos)
    {
        if (CarpetSettings.autoCraftingDropper)
        {
            BlockPos front = pos.offset(worldIn.getBlockState(pos).getValue(BlockDispenser.FACING));
            if (worldIn.getBlockState(front).getBlock() == Blocks.CRAFTING_TABLE)
            {
                TileEntityDispenser dispenserTE = (TileEntityDispenser) worldIn.getTileEntity(pos);
                if (dispenserTE != null)
                {
                    int filled = 0;
                    for (ItemStack stack : dispenserTE.getItems())
                    {
                        if (!stack.isEmpty()) filled++;
                    }
                    return (filled * 15) / 9;
                }
            }
        }
        return super.getComparatorInputOverride(blockState, worldIn, pos);
    }
    
    private boolean autoCraftingDispense(World worldIn, BlockPos pos)
    {
        BlockPos front = pos.offset(worldIn.getBlockState(pos).getValue(BlockDispenser.FACING));
        if (worldIn.getBlockState(front).getBlock() != Blocks.CRAFTING_TABLE)
            return false;
        TileEntityDispenser dispenserTE = (TileEntityDispenser) worldIn.getTileEntity(pos);
        if (dispenserTE == null)
            return false;
        InventoryCrafting craftingInventory = new InventoryCrafting(new VoidContainer(), 3, 3);
        for (int i = 0; i < 9; i++)
            craftingInventory.setInventorySlotContents(i, dispenserTE.getStackInSlot(i));
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftingInventory, worldIn);
        if (recipe == null)
            return false;
        // crafting it
        Vec3d target = new Vec3d(front).add(0.5, 0.2, 0.5);
        ItemStack result = recipe.getCraftingResult(craftingInventory);
        AutoCraftingDropperHelper.spawnItemStack(worldIn, target.x, target.y, target.z, result);
        
        // copied from CraftingResultSlot.onTakeItem()
        NonNullList<ItemStack> nonNullList = recipe.getRemainingItems(craftingInventory);
        for (int i = 0; i < nonNullList.size(); ++i)
        {
            ItemStack itemStack_2 = dispenserTE.getStackInSlot(i);
            ItemStack itemStack_3 = nonNullList.get(i);
            if (!itemStack_2.isEmpty())
            {
                dispenserTE.decrStackSize(i, 1);
                itemStack_2 = dispenserTE.getStackInSlot(i);
            }
            
            if (!itemStack_3.isEmpty())
            {
                if (itemStack_2.isEmpty())
                {
                    dispenserTE.setInventorySlotContents(i, itemStack_3);
                }
                else if (ItemStack.areItemsEqualIgnoreDurability(itemStack_2, itemStack_3) && ItemStack.areItemStackTagsEqual(itemStack_2, itemStack_3))
                {
                    itemStack_3.grow(itemStack_2.getCount());
                    dispenserTE.setInventorySlotContents(i, itemStack_3);
                }
                else
                {
                    AutoCraftingDropperHelper.spawnItemStack(worldIn, target.x, target.y, target.z, itemStack_3);
                }
            }
        }
        return true;
    }
}