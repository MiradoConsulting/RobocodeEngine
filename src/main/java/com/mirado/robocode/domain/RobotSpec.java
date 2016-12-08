package com.mirado.robocode.domain;

import java.time.Instant;

/**
 * Created by Kurt on 04/12/16.
 */
public class RobotSpec
{
    private final String name;
    private final String owner;
    private final String url;
    private final Instant lastPushed;
    private final String className;
    private final String source;
    private final String packageName;

    private RobotSpec(Builder builder)
    {
        name = builder.name;
        owner = builder.owner;
        url = builder.url;
        lastPushed = builder.lastPushed;
        className = builder.className;
        source = builder.source;
        packageName = builder.packageName;
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public static Builder newBuilder(RobotSpec copy)
    {
        Builder builder = new Builder();
        builder.name = copy.name;
        builder.owner = copy.owner;
        builder.url = copy.url;
        builder.lastPushed = copy.lastPushed;
        builder.className = copy.className;
        builder.source = copy.source;
        builder.packageName = copy.packageName;
        return builder;
    }

    public String getName()
    {
        return name;
    }

    public String getOwner()
    {
        return owner;
    }

    public String getUrl()
    {
        return url;
    }

    public Instant getLastPushed()
    {
        return lastPushed;
    }

    public String getClassName()
    {
        return className;
    }

    public String getSource()
    {
        return source;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public static final class Builder
    {
        private String name;
        private String owner;
        private String url;
        private Instant lastPushed;
        private String className;
        private String source;
        private String packageName;

        private Builder()
        {
        }

        public Builder name(String val)
        {
            name = val;
            return this;
        }

        public Builder owner(String val)
        {
            owner = val;
            return this;
        }

        public Builder url(String val)
        {
            url = val;
            return this;
        }

        public Builder lastPushed(Instant val)
        {
            lastPushed = val;
            return this;
        }

        public Builder className(String val)
        {
            className = val;
            return this;
        }

        public Builder source(String val)
        {
            source = val;
            return this;
        }

        public Builder packageName(String val)
        {
            packageName = val;
            return this;
        }

        public RobotSpec build()
        {
            return new RobotSpec(this);
        }
    }
}
