package net.minecraft.entity.item;

import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

import carpet.CarpetSettings;
import carpet.logging.logHelpers.TNTLogHelper;
import carpet.logging.LoggerRegistry;

import java.util.Random;

public class EntityTNTPrimed extends Entity
{
    private static final DataParameter<Integer> FUSE = EntityDataManager.<Integer>createKey(EntityTNTPrimed.class, DataSerializers.VARINT);
    @Nullable
    private EntityLivingBase tntPlacedBy;
    /** How long the fuse is */
    private int fuse;
    
    // Merge code for optimizing the tnt code CARPET-XCOM
    private int mergedTNT;
    private boolean mergeBool;

    public static Random randAngle = new Random();

    // ----- Carpet Start ----- //
    private TNTLogHelper logHelper = null;
    public String cm_name() { return "Primed TNT"; }
    // ----- Carpet End ----- //

    public EntityTNTPrimed(World worldIn)
    {
        super(worldIn);

        if (LoggerRegistry.__tnt && logHelper == null)
        {
            logHelper = new TNTLogHelper();
            logHelper.onPrimed(this.posX,this.posY,this.posZ,0);
        }

        this.fuse = CarpetSettings.tntFuseLength; //CM Vanilla default is 80gt
        mergedTNT = 1;
        mergeBool = false;
        this.preventEntitySpawning = true;
        this.isImmuneToFire = true;
        this.setSize(0.98F, 0.98F);
    }

    public EntityTNTPrimed(World worldIn, double x, double y, double z, EntityLivingBase igniter)
    {
        this(worldIn);
        this.setPosition(x, y, z);
        float f = 0F;
        if(!CarpetSettings.tntPrimerMomentumRemoved){
            if(!CarpetSettings.TNTAdjustableRandomAngle){
                f = (float)(Math.random() * (Math.PI * 2D));
            } else {
                // Use predictable RNG seed to find bugs in TNT prime momentum with large dupers. CARPET-XCOM
                f = (float)(randAngle.nextDouble() * (Math.PI * 2D));
            }
            if(CarpetSettings.hardcodeTNTangle > 0) f = (float) CarpetSettings.hardcodeTNTangle;
            this.motionX = (double)(-((float)Math.sin((double)f)) * 0.02F);
            this.motionY = 0.20000000298023224D;
            this.motionZ = (double)(-((float)Math.cos((double)f)) * 0.02F);
        }
        // ----- Carpet Start ----- //
        if (LoggerRegistry.__tnt)
        {
            logHelper = new TNTLogHelper();
            logHelper.onPrimed(x, y, z, f);
        }
        // ----- Carpet End ----- //
        this.setFuse(CarpetSettings.tntFuseLength); //CM Vanilla default is 80gt
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        this.tntPlacedBy = igniter;
    }

    protected void entityInit()
    {
        this.dataManager.register(FUSE, Integer.valueOf(80));
    }

    /**
     * returns if this entity triggers Block.onEntityWalking on the blocks they walk on. used for spiders and wolves to
     * prevent them from trampling crops
     */
    protected boolean canTriggerWalking()
    {
        return false;
    }

    /**
     * Returns true if other Entities should be prevented from moving through this Entity.
     */
    public boolean canBeCollidedWith()
    {
        return !this.isDead;
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void onUpdate()
    {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (!this.hasNoGravity())
        {
            this.motionY -= 0.03999999910593033D;
        }

        // Optimized TNT movement skipping the move code given its expensive if identical tnt movement is done. CARPET-XCOM
        if(!CarpetSettings.TNTmovementOptimization) {
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        } else {
            if(!cacheMatching()) {
                cache[0] = posX;
                cache[1] = posY;
                cache[2] = posZ;
                cache[3] = motionX;
                cache[4] = motionY;
                cache[5] = motionZ;
                cacheTime = getServer().getTickCounter();
                this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                if (!isDead) {
                    cache[6] = posX;
                    cache[7] = posY;
                    cache[8] = posZ;
                    cache[9] = motionX;
                    cache[10] = motionY;
                    cache[11] = motionZ;
                    cacheBool[0] = isInWeb;
                    cacheBool[1] = onGround;
                } else {
                    cache[0] = Integer.MAX_VALUE;
                }
            } else {
                this.setPosition(cache[6], cache[7], cache[8]);
                motionX = cache[9];
                motionY = cache[10];
                motionZ = cache[11];
                isInWeb = cacheBool[0];
                onGround = cacheBool[1];
            }
        }
        this.motionX *= 0.9800000190734863D;
        this.motionY *= 0.9800000190734863D;
        this.motionZ *= 0.9800000190734863D;

        if (this.onGround)
        {
            // Merge code for combining tnt into a single entity if they happen to exist in the same spot, same fuse, no motion CARPET-XCOM
            if(CarpetSettings.mergeTNT){
                if(!world.isRemote && mergeBool && this.motionX == 0 && this.motionY == 0 && this.motionZ == 0){
                    mergeBool = false;
                    for(Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox())){
                        if(entity instanceof EntityTNTPrimed && !entity.isDead){
                            EntityTNTPrimed entityTNTPrimed = (EntityTNTPrimed)entity;
                            if(entityTNTPrimed.motionX == 0 && entityTNTPrimed.motionY == 0 && entityTNTPrimed.motionZ == 0
                                    && this.posX == entityTNTPrimed.posX && this.posZ == entityTNTPrimed.posZ && this.posY == entityTNTPrimed.posY
                                    && this.fuse == entityTNTPrimed.fuse){
                                mergedTNT += entityTNTPrimed.mergedTNT;
                                entityTNTPrimed.setDead();
                            }
                        }
                    }
                }
            }
            this.motionX *= 0.699999988079071D;
            this.motionZ *= 0.699999988079071D;
            this.motionY *= -0.5D;
        }

     // Merge code, merge only tnt that have had a chance to move CARPET-XCOM
        if(!world.isRemote && (this.motionY != 0 || this.motionX != 0 || this.motionZ != 0)){
            mergeBool = true;
        }

        --this.fuse;

        if (this.fuse <= 0)
        {
            this.setDead();

            if (!this.world.isRemote)
            {
                this.explode();
            }
        }
        else
        {
            this.handleWaterMovement();
            this.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, this.posX, this.posY + 0.5D, this.posZ, 0.0D, 0.0D, 0.0D);
        }
    }

    private void explode()
    {
        // ----- Carpet Start ----- //
        if (LoggerRegistry.__tnt && logHelper != null)
            logHelper.onExploded(posX, posY, posZ);
        // ----- Carpet End ----- //
        float f = 4.0F;
        // Multi explode the amount of merged TNT CARPET-XCOM
        for(int i = 0; i < mergedTNT; i++)
            this.world.createExplosion(this, this.posX, this.posY + (double)(this.height / 16.0F), this.posZ, 4.0F, true);
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    protected void writeEntityToNBT(NBTTagCompound compound)
    {
        compound.setShort("Fuse", (short)this.getFuse());
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    protected void readEntityFromNBT(NBTTagCompound compound)
    {
        this.setFuse(compound.getShort("Fuse"));
    }

    /**
     * returns null or the entityliving it was placed or ignited by
     */
    @Nullable
    public EntityLivingBase getTntPlacedBy()
    {
        return this.tntPlacedBy;
    }

    public float getEyeHeight()
    {
        return 0.0F;
    }

    public void setFuse(int fuseIn)
    {
        this.dataManager.set(FUSE, Integer.valueOf(fuseIn));
        this.fuse = fuseIn;
    }

    public void notifyDataManagerChange(DataParameter<?> key)
    {
        if (FUSE.equals(key))
        {
            this.fuse = this.getFuseDataManager();
        }
    }

    /**
     * Gets the fuse from the data manager
     */
    public int getFuseDataManager()
    {
        return ((Integer)this.dataManager.get(FUSE)).intValue();
    }

    public int getFuse()
    {
        return this.fuse;
    }

    // Optimization methods CARPET-XCOM
    private static double[] cache = new double[12];
    private static boolean[] cacheBool = new boolean[2];
    private static long cacheTime = 0;
    private boolean cacheMatching() {
        return cache[0] == posX && cache[1] == posY && cache[2] == posZ && cache[3] == motionX && cache[4] == motionY && cache[5] == motionZ && cacheTime == getServer().getTickCounter();
    }
}