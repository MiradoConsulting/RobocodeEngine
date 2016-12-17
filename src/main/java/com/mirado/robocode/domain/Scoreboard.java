package com.mirado.robocode.domain;

import robocode.control.RobotResults;

import java.util.Collections;
import java.util.List;

/**
 * Created by oskarkjellin on 2016-12-17.
 */
public class Scoreboard
{
    private final List<RobotResults> scoreBoard;
    private final List<BattleStatistics> battleStatistics;

    private Scoreboard(Builder builder)
    {
        scoreBoard = builder.scoreBoard == null ? Collections.emptyList() : Collections.unmodifiableList(builder.scoreBoard);
        battleStatistics = builder.battleStatistics == null ? Collections.emptyList() : Collections.unmodifiableList(builder.battleStatistics);
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public List<RobotResults> getScoreBoard()
    {
        return scoreBoard;
    }

    public List<BattleStatistics> getBattleStatistics()
    {
        return battleStatistics;
    }

    public static final class Builder
    {
        private List<RobotResults> scoreBoard;
        private List<BattleStatistics> battleStatistics;

        private Builder()
        {
        }

        public Builder scoreBoard(List<RobotResults> val)
        {
            scoreBoard = val;
            return this;
        }

        public Builder battleStatistics(List<BattleStatistics> val)
        {
            battleStatistics = val;
            return this;
        }

        public Scoreboard build()
        {
            return new Scoreboard(this);
        }
    }
}
