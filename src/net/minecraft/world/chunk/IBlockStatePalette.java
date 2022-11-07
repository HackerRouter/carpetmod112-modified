package net.minecraft.world.chunk;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;

public interface IBlockStatePalette
{
    int idFor(IBlockState state);

    /**
     * Gets the block state by the palette id.
     */
    @Nullable
    IBlockState getBlockState(int indexKey);

    void write(PacketBuffer buf);

    int getSerializedSize();
}