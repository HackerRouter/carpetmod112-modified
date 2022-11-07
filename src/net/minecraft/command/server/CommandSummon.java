package net.minecraft.command.server;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.entity.player.EntityPlayerMP;
import carpet.CarpetSettings;
import carpet.worldedit.WorldEditBridge;

public class CommandSummon extends CommandBase
{
    /**
     * Gets the name of the command
     */
    public String getName()
    {
        return "summon";
    }

    /**
     * Return the required permission level for this command.
     */
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    /**
     * Gets the usage string for the command.
     */
    public String getUsage(ICommandSender sender)
    {
        return "commands.summon.usage";
    }

    /**
     * Callback for when the command is executed
     */
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 1)
        {
            throw new WrongUsageException("commands.summon.usage", new Object[0]);
        }
        else
        {
            String s = args[0];
            BlockPos blockpos = sender.getPosition();
            Vec3d vec3d = sender.getPositionVector();
            double d0 = vec3d.x;
            double d1 = vec3d.y;
            double d2 = vec3d.z;

            if (args.length >= 4)
            {
                d0 = parseDouble(d0, args[1], true);
                d1 = parseDouble(d1, args[2], false);
                d2 = parseDouble(d2, args[3], true);
                blockpos = new BlockPos(d0, d1, d2);
            }

            World world = sender.getEntityWorld();
            
            EntityPlayerMP worldEditPlayer = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;

            if (!world.isBlockLoaded(blockpos))
            {
                throw new CommandException("commands.summon.outOfWorld", new Object[0]);
            }
            else if (EntityList.LIGHTNING_BOLT.equals(new ResourceLocation(s)))
            {
                if(!CarpetSettings.summonNaturalLightning)
                {
                    world.addWeatherEffect(new EntityLightningBolt(world, d0, d1, d2, false));
                }
                else
                {
                    BlockPos bp = ((WorldServer)world).adjustPosToNearbyEntity(new BlockPos(d0, 0, d2));
                    if(world.isRainingAt(bp)){
                        DifficultyInstance difficultyinstance = world.getDifficultyForLocation(bp);

                        if (world.getGameRules().getBoolean("doMobSpawning") && world.rand.nextDouble() < (double)difficultyinstance.getAdditionalDifficulty() * 0.01D)
                        {
                            EntitySkeletonHorse entityskeletonhorse = new EntitySkeletonHorse(world);
                            entityskeletonhorse.setTrap(true);
                            entityskeletonhorse.setGrowingAge(0);
                            entityskeletonhorse.setPosition((double)bp.getX(), (double)bp.getY(), (double)bp.getZ());
                            world.spawnEntity(entityskeletonhorse);
                            world.addWeatherEffect(new EntityLightningBolt(world, (double)bp.getX(), (double)bp.getY(), (double)bp.getZ(), true));
                            
                            WorldEditBridge.recordEntityCreation(worldEditPlayer, world, entityskeletonhorse);
                        }
                        else
                        {
                            world.addWeatherEffect(new EntityLightningBolt(world, (double)bp.getX(), (double)bp.getY(), (double)bp.getZ(), false));
                        }
                    }
                }
                notifyCommandListener(sender, this, "commands.summon.success", new Object[0]);
            }
            else
            {
                NBTTagCompound nbttagcompound = new NBTTagCompound();
                boolean flag = false;

                if (args.length >= 5)
                {
                    String s1 = buildString(args, 4);

                    try
                    {
                        nbttagcompound = JsonToNBT.getTagFromJson(s1);
                        flag = true;
                    }
                    catch (NBTException nbtexception)
                    {
                        throw new CommandException("commands.summon.tagError", new Object[] {nbtexception.getMessage()});
                    }
                }

                nbttagcompound.setString("id", s);
                Entity entity = AnvilChunkLoader.readWorldEntityPos(nbttagcompound, world, d0, d1, d2, true);

                if (entity == null)
                {
                    throw new CommandException("commands.summon.failed", new Object[0]);
                }
                else
                {
                    entity.setLocationAndAngles(d0, d1, d2, entity.rotationYaw, entity.rotationPitch);

                    if (!flag && entity instanceof EntityLiving)
                    {
                        ((EntityLiving)entity).onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entity)), (IEntityLivingData)null);
                    }
                    
                    WorldEditBridge.recordEntityCreation(worldEditPlayer, world, entity);

                    notifyCommandListener(sender, this, "commands.summon.success", new Object[0]);
                }
            }
        }
    }

    /**
     * Get a list of options for when the user presses the TAB key
     */
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, EntityList.getEntityNameList());
        }
        else
        {
            return args.length > 1 && args.length <= 4 ? getTabCompletionCoordinate(args, 1, targetPos) : Collections.emptyList();
        }
    }
}