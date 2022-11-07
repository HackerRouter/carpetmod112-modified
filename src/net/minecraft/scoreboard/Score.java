package net.minecraft.scoreboard;

import carpet.CarpetServer;
import carpet.CarpetSettings;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

public class Score
{
    /** Used for sorting score by points */
    public static final Comparator<Score> SCORE_COMPARATOR = new Comparator<Score>()
    {
        public int compare(Score p_compare_1_, Score p_compare_2_)
        {
            if (p_compare_1_.getScorePointsDelta() > p_compare_2_.getScorePointsDelta())
            {
                return 1;
            }
            else
            {
                return p_compare_1_.getScorePointsDelta() < p_compare_2_.getScorePointsDelta() ? -1 : p_compare_2_.getPlayerName().compareToIgnoreCase(p_compare_1_.getPlayerName());
            }
        }
    };
    private final Scoreboard scoreboard;
    private final ScoreObjective objective;
    private final String scorePlayerName;
    private int scorePoints;
    private boolean locked;
    private boolean forceUpdate;
    private int scorePointsDelta;

    public Score(Scoreboard scoreboard, ScoreObjective objective, String playerName)
    {
        this.scoreboard = scoreboard;
        this.objective = objective;
        this.scorePlayerName = playerName;
        this.forceUpdate = true;
    }

    public void increaseScore(int amount)
    {
        if (this.objective.getCriteria().isReadOnly())
        {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        else
        {
            this.setScorePoints(this.getScorePoints() + amount);
        }
    }

    public void decreaseScore(int amount)
    {
        if (this.objective.getCriteria().isReadOnly())
        {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        else
        {
            this.setScorePoints(this.getScorePoints() - amount);
        }
    }

    public void incrementScore()
    {
        if (this.objective.getCriteria().isReadOnly())
        {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        else
        {
            this.increaseScore(1);
        }
    }

    public int getScorePoints()
    {
        return this.scorePoints;
    }

    public void setScorePoints(int points)
    {
        int i = this.scorePoints;
        this.scorePoints = points;

        if (CarpetSettings.scoreboardDelta > 0) {
            list.add(Pair.of(System.currentTimeMillis(), scorePoints));
            computeScoreDelta();
        }

        if (i != points || this.forceUpdate)
        {
            this.forceUpdate = false;
            this.getScoreScoreboard().onScoreUpdated(this);
        }
    }

    public ScoreObjective getObjective()
    {
        return this.objective;
    }

    /**
     * Returns the name of the player this score belongs to
     */
    public String getPlayerName()
    {
        return this.scorePlayerName;
    }

    public Scoreboard getScoreScoreboard()
    {
        return this.scoreboard;
    }

    public boolean isLocked()
    {
        return this.locked;
    }

    public void setLocked(boolean locked)
    {
        this.locked = locked;
    }

    // Added for changing scoreboard values to the score per time value option. CARPET-XCOM
    LinkedList<Pair<Long,Integer>> list = new LinkedList<>();
    public int getScorePointsDelta()
    {
        if(CarpetSettings.scoreboardDelta > 0) {
            return this.scorePointsDelta;
        }else{
            return this.scorePoints;
        }
    }
    public void computeScoreDelta(){
        int oldest = Integer.MIN_VALUE;
        Iterator<Pair<Long, Integer>> iter = list.iterator();
        while (iter.hasNext()) {
            Pair p = iter.next();
            if ((long)p.getKey() > (System.currentTimeMillis() - CarpetSettings.scoreboardDelta * 1000)) {
                oldest = (int)p.getValue();
                break;
            } else {
                iter.remove();
            }
        }
        if (oldest != Integer.MIN_VALUE) {
            scorePointsDelta = (int)((float) (scorePoints - oldest) / (float) 10);
        } else {
            scorePointsDelta = 0;
        }
    }
}