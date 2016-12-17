package com.mirado.robocode.domain;

import robocode.BattleResults;
import robocode.control.RobotResults;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Created by oskarkjellin on 2016-12-17.
 */
public class BattleStatistics
{
    private final Instant timestamp;
    private final List<RobotResults> results;

    private BattleStatistics(Builder builder)
    {
        timestamp = builder.timestamp;
        results = builder.results == null ? Collections.emptyList() : Collections.unmodifiableList(builder.results);
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public List<RobotResults> getResults()
    {
        return results;
    }

    public static final class Builder
    {
        private Instant timestamp;
        private List<RobotResults> results;

        private Builder()
        {
        }

        public Builder timestamp(Instant val)
        {
            timestamp = val;
            return this;
        }

        public Builder results(List<RobotResults> val)
        {
            results = val;
            return this;
        }

        public BattleStatistics build()
        {
            return new BattleStatistics(this);
        }
    }
}
