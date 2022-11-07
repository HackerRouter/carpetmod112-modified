package net.minecraft.tileentity;

import carpet.CarpetSettings;

import net.minecraft.nbt.NBTTagCompound;

import redstone.multimeter.helper.WorldHelper;

public class TileEntityComparator extends TileEntity
{
    private int outputSignal;
    // CM: instant comparator logger, stored in world time modulo 3.
    // This is to allow for further tile tick scheduling in the same tick before the tile tick is processed
    public int[] scheduledOutputSignal = new int[3];
    public boolean[] buggy = new boolean[3];

    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        compound.setInteger("OutputSignal", this.outputSignal);
        return compound;
    }

    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        this.outputSignal = compound.getInteger("OutputSignal");
    }

    public int getOutputSignal()
    {
        return this.outputSignal;
    }

    public void setOutputSignal(int outputSignalIn)
    {
        // RSMM start
        if (CarpetSettings.redstoneMultimeter && !world.isRemote) {
            WorldHelper.getMultimeter().logPowerChange(world, pos, outputSignal, outputSignalIn);
        }
        // RSMM end
        this.outputSignal = outputSignalIn;
    }
}