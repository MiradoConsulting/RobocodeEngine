package com.mirado.robocode.archaius;

import com.netflix.archaius.DefaultPropertyFactory;
import com.netflix.archaius.api.Property;
import com.netflix.archaius.api.config.CompositeConfig;
import com.netflix.archaius.api.exceptions.ConfigException;
import com.netflix.archaius.config.DefaultCompositeConfig;
import com.netflix.archaius.config.PollingDynamicConfig;
import com.netflix.archaius.config.polling.FixedPollingStrategy;
import net.sf.robocode.settings.SettingsManager;
import net.sf.robocode.version.VersionManager;

import java.util.concurrent.TimeUnit;

/**
 * Created by Kurt on 04/12/16.
 */
public class Config
{
    private static final CompositeConfig config;
    private static final DefaultPropertyFactory propertyFactory;
    private static String roboCodeVersion;

    static
    {
        try
        {
            config = DefaultCompositeConfig.create();
            propertyFactory = DefaultPropertyFactory.from(config);
        }
        catch (ConfigException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static final Property<String> S3_BUCKET = Config.getPropertyFactory().getProperty("recording.s3_bucket").asString(null);
    public static DefaultPropertyFactory getPropertyFactory()
    {
        return propertyFactory;
    }

    public static void init() throws ConfigException
    {
        PollingDynamicConfig dynamicConfig = new PollingDynamicConfig(new DynamoDbSource(), new FixedPollingStrategy(60, TimeUnit.SECONDS));
        config.addConfig("dynamo", dynamicConfig);
    }

    public static String getRoboCodeVersion()
    {
        if (roboCodeVersion != null)
        {
            return roboCodeVersion;
        }
        roboCodeVersion = new VersionManager(new SettingsManager()).getVersion();
        return roboCodeVersion;
    }

    public static String getS3Bucket()
    {
        String s3Bucket = S3_BUCKET.get();
        if (s3Bucket == null)
        {
            throw new IllegalArgumentException("recording.s3_bucket must be configured!");
        }
        return s3Bucket;
    }
}
