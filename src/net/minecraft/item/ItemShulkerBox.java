package net.minecraft.item;

import net.minecraft.block.Block;
import carpet.CarpetSettings;

public class ItemShulkerBox extends ItemBlock
{
    public ItemShulkerBox(Block blockInstance)
    {
        super(blockInstance);
        this.setMaxStackSize(1);
    }

    /*
     * Stack empty shulkers on the ground CARPET-XCOM
     */
    @Override
    public boolean itemGroundStacking(boolean hasTag){
    	return !hasTag && CarpetSettings.stackableEmptyShulkerBoxes;
    }
}