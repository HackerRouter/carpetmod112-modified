package net.minecraft.item;

import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import carpet.CarpetSettings;
import carpet.helpers.GhastHelper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.util.ActionResult;

public class ItemFireball extends Item
{
    public ItemFireball()
    {
        this.setCreativeTab(CreativeTabs.MISC);
    }

    /**
     * Called when a Block is right-clicked with this Item
     */
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if (worldIn.isRemote)
        {
            return EnumActionResult.SUCCESS;
        }
        else
        {
            pos = pos.offset(facing);
            ItemStack itemstack = player.getHeldItem(hand);

            if (!player.canPlayerEdit(pos, facing, itemstack))
            {
                return EnumActionResult.FAIL;
            }
            else
            {
                if (worldIn.getBlockState(pos).getMaterial() == Material.AIR)
                {
                    worldIn.playSound((EntityPlayer)null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1.0F, (itemRand.nextFloat() - itemRand.nextFloat()) * 0.2F + 1.0F);
                    worldIn.setBlockState(pos, Blocks.FIRE.getDefaultState());
                }

                if (!player.capabilities.isCreativeMode)
                {
                    itemstack.shrink(1);
                }

                return EnumActionResult.SUCCESS;
            }
        }
    }


    /**
     * Called when the equipped item is right clicked.
     */
    public ActionResult<ItemStack> onItemRightClick(World itemStackIn, EntityPlayer worldIn, EnumHand playerIn)
    {
        if (!(CarpetSettings.rideableGhasts && worldIn.getRidingEntity() instanceof EntityGhast))
        {
            return super.onItemRightClick(itemStackIn, worldIn, playerIn);
        }
        ItemStack itemstack = worldIn.getHeldItem(playerIn);
        EntityGhast ghast = (EntityGhast)worldIn.getRidingEntity();
        worldIn.getCooldownTracker().setCooldown(this, 40);
        GhastHelper.set_off_fball(ghast, itemStackIn, worldIn);
        if (!worldIn.capabilities.isCreativeMode)
        {
            itemstack.shrink(1);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
    }
}