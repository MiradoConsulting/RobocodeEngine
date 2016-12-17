package com.mirado.robocode.domain;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import robocode.BattleResults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by oskarkjellin on 2016-12-17.
 */
public class BattleStatistics
{
    private final Instant timestamp;
    private final List<BattleResults> results;

    private BattleStatistics(Builder builder)
    {
        timestamp = builder.timestamp;
        results = builder.results == null ? Collections.emptyList() : Collections.unmodifiableList(builder.results);
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public static Builder newBuilder(BattleStatistics copy)
    {
        Builder builder = new Builder();
        builder.timestamp = copy.timestamp;
        builder.results = new ArrayList<>(copy.results);
        return builder;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public List<BattleResults> getResults()
    {
        return results;
    }

    public static final class Builder
    {
        private Instant timestamp;
        private List<BattleResults> results;

        private Builder()
        {
        }

        public Builder timestamp(Instant val)
        {
            timestamp = val;
            return this;
        }

        public Builder results(BattleResults... val)
        {
            if (results == null)
            {
                results = new ArrayList<>();
            }
            results.addAll(Arrays.asList(val));
            return this;
        }

        public Builder results(List<BattleResults> val)
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
