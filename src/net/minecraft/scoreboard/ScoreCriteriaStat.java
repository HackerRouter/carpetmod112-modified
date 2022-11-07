package net.minecraft.scoreboard;

import net.minecraft.stats.StatBase;

public class ScoreCriteriaStat extends ScoreCriteria
{
    public final StatBase stat; // CM public

    public ScoreCriteriaStat(StatBase statIn)
    {
        super(statIn.statId);
        this.stat = statIn;
    }
}