package net.minecraft.potion;

import carpet.CarpetServer;
import carpet.logging.LoggerRegistry;
import carpet.utils.Messenger;
import com.google.common.collect.ComparisonChain;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemPotion;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import carpet.CarpetSettings;

public class PotionEffect implements Comparable<PotionEffect>
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final Potion potion;
    /** The duration of the potion effect */
    private int duration;
    /** The amplifier of the potion effect */
    private int amplifier;
    /** Whether the potion is a splash potion */
    private boolean isSplashPotion;
    /** Whether the potion effect came from a beacon */
    private boolean isAmbient;
    private boolean showParticles;

    // CM
    public PotionEffect previous;
    public static boolean itemPotion;

    public PotionEffect(Potion potionIn)
    {
        this(potionIn, 0, 0);
    }

    public PotionEffect(Potion potionIn, int durationIn)
    {
        this(potionIn, durationIn, 0);
    }

    public PotionEffect(Potion potionIn, int durationIn, int amplifierIn)
    {
        this(potionIn, durationIn, amplifierIn, false, true);
    }

    public PotionEffect(Potion potionIn, int durationIn, int amplifierIn, boolean ambientIn, boolean showParticlesIn)
    {
        this.potion = potionIn;
        this.duration = durationIn;
        this.amplifier = amplifierIn;
        this.isAmbient = ambientIn;
        this.showParticles = showParticlesIn;
    }

    public PotionEffect(PotionEffect other)
    {
        this.potion = other.potion;
        this.duration = other.duration;
        this.amplifier = other.amplifier;
        this.isAmbient = other.isAmbient;
        this.showParticles = other.showParticles;
    }

    // Carpet: void -> PotionEffect, other -> other for smaller diff
    public PotionEffect combine(PotionEffect other)
    {
        if (this.potion != other.potion)
        {
            LOGGER.warn("This method should only be called for matching effects!");
        }
        // CM
        if (other == this) {
            return this;
        }
        if (CarpetSettings.effectsFix && !itemPotion && !this.isAmbient && other.amplifier >= this.amplifier && other.duration < this.duration) {
            boolean stack = true;
            for (PotionEffect e = other; e != null; e = e.previous) {
                if (e == this) {
                    LOGGER.warn("Tried to recursively combine effects " + this + " and " + other);
                    stack = false;
                    break;
                }
            }
            if (stack) {
                other.previous = this;
                return other;
            }
        }
        // CM END

        if (other.amplifier > this.amplifier)
        {
            this.amplifier = other.amplifier;
            this.duration = other.duration;
        }
        // Combines the potion durations of identical potions when a player drinks the potions. CARPET-XCOM
        else if(CarpetSettings.combinePotionDuration > 0 && itemPotion && other.amplifier == this.amplifier){
            if(this.duration < 0) this.duration = 0;
            this.duration += other.duration;
            // Cap the duration to the carpet rule amount. Also make sure its more then the amount being added.
            if(this.duration > CarpetSettings.combinePotionDuration && CarpetSettings.combinePotionDuration > other.duration){
                this.duration = CarpetSettings.combinePotionDuration;
            }
        }
        else if (other.amplifier == this.amplifier && this.duration < other.duration)
        {
            this.duration = other.duration;
        }
        else if (!other.isAmbient && this.isAmbient)
        {
            this.isAmbient = other.isAmbient;
        }

        this.showParticles = other.showParticles;
        return this; // Carpet added return
    }

    public Potion getPotion()
    {
        return this.potion;
    }

    public int getDuration()
    {
        return this.duration;
    }

    public int getAmplifier()
    {
        return this.amplifier;
    }

    /**
     * Gets whether this potion effect originated from a beacon
     */
    public boolean getIsAmbient()
    {
        return this.isAmbient;
    }

    /**
     * Gets whether this potion effect will show ambient particles or not.
     */
    public boolean doesShowParticles()
    {
        return this.showParticles;
    }

    public boolean onUpdate(EntityLivingBase entityIn)
    {
        if (this.duration > 0)
        {
            if (this.potion.isReady(this.duration, this.amplifier))
            {
                this.performEffect(entityIn);
            }

            this.deincrementDuration();
        }

        return this.duration > 0;
    }

    private int deincrementDuration()
    {
        return --this.duration;
    }

    public void performEffect(EntityLivingBase entityIn)
    {
        if (this.duration > 0)
        {
            this.potion.performEffect(entityIn, this.amplifier);
        }
    }

    public String getEffectName()
    {
        return this.potion.getName();
    }

    public String toString()
    {
        String s;

        if (this.amplifier > 0)
        {
            s = this.getEffectName() + " x " + (this.amplifier + 1) + ", Duration: " + this.duration;
        }
        else
        {
            s = this.getEffectName() + ", Duration: " + this.duration;
        }

        if (this.isSplashPotion)
        {
            s = s + ", Splash: true";
        }

        if (!this.showParticles)
        {
            s = s + ", Particles: false";
        }

        return s;
    }

    public boolean equals(Object p_equals_1_)
    {
        if (this == p_equals_1_)
        {
            return true;
        }
        else if (!(p_equals_1_ instanceof PotionEffect))
        {
            return false;
        }
        else
        {
            PotionEffect potioneffect = (PotionEffect)p_equals_1_;
            return this.duration == potioneffect.duration && this.amplifier == potioneffect.amplifier && this.isSplashPotion == potioneffect.isSplashPotion && this.isAmbient == potioneffect.isAmbient && this.potion.equals(potioneffect.potion);
        }
    }

    public int hashCode()
    {
        int i = this.potion.hashCode();
        i = 31 * i + this.duration;
        i = 31 * i + this.amplifier;
        i = 31 * i + (this.isSplashPotion ? 1 : 0);
        i = 31 * i + (this.isAmbient ? 1 : 0);
        return i;
    }

    /**
     * Write a custom potion effect to a potion item's NBT data.
     */
    public NBTTagCompound writeCustomPotionEffectToNBT(NBTTagCompound nbt)
    {
        nbt.setByte("Id", (byte)Potion.getIdFromPotion(this.getPotion()));
        nbt.setByte("Amplifier", (byte)this.getAmplifier());
        nbt.setInteger("Duration", this.getDuration());
        nbt.setBoolean("Ambient", this.getIsAmbient());
        nbt.setBoolean("ShowParticles", this.doesShowParticles());
        return nbt;
    }

    /**
     * Read a custom potion effect from a potion item's NBT data.
     */
    public static PotionEffect readCustomPotionEffectFromNBT(NBTTagCompound nbt)
    {
        int i = nbt.getByte("Id");
        Potion potion = Potion.getPotionById(i);

        if (potion == null)
        {
            return null;
        }
        else
        {
            int j = nbt.getByte("Amplifier");
            int k = nbt.getInteger("Duration");
            boolean flag = nbt.getBoolean("Ambient");
            boolean flag1 = true;

            if (nbt.hasKey("ShowParticles", 1))
            {
                flag1 = nbt.getBoolean("ShowParticles");
            }

            return new PotionEffect(potion, k, j < 0 ? 0 : j, flag, flag1);
        }
    }

    public int compareTo(PotionEffect p_compareTo_1_)
    {
        int i = 32147;
        return (this.getDuration() <= 32147 || p_compareTo_1_.getDuration() <= 32147) && (!this.getIsAmbient() || !p_compareTo_1_.getIsAmbient()) ? ComparisonChain.start().compare(Boolean.valueOf(this.getIsAmbient()), Boolean.valueOf(p_compareTo_1_.getIsAmbient())).compare(this.getDuration(), p_compareTo_1_.getDuration()).compare(this.getPotion().getLiquidColor(), p_compareTo_1_.getPotion().getLiquidColor()).result() : ComparisonChain.start().compare(Boolean.valueOf(this.getIsAmbient()), Boolean.valueOf(p_compareTo_1_.getIsAmbient())).compare(this.getPotion().getLiquidColor(), p_compareTo_1_.getPotion().getLiquidColor()).result();
    }
}