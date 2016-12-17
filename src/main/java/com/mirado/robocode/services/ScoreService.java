package com.mirado.robocode.services;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.BattleStatistics;
import com.mirado.robocode.engine.BattleRunner;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.BattleResults;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Checks S3 for replay files, replays them and tracks the overall scoreboard
 */
public class ScoreService
{
    private static final Logger logger = LoggerFactory.getLogger(ScoreService.class);
    private final Timer timer = new Timer(true);
    private final AmazonS3Client s3client;
    private final Map<String, BattleStatistics> battles = new HashMap<>();

    @Inject
    public ScoreService(AmazonS3Client s3client)
    {
        this.s3client = s3client;
    }

    public void start()
    {
        ScoreService self = this;
        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                self.run();
            }
        }, 0, 60000);
    }

    private void run()
    {
        String bucket = Config.getS3Bucket();
        final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket).withPrefix("runs/");
        ListObjectsV2Result result;
        do
        {
            result = s3client.listObjectsV2(req);

            for (S3ObjectSummary objectSummary :
                    result.getObjectSummaries())
            {
                try
                {
                    if (objectSummary.getSize() == 0)
                    {
                        continue;
                    }
                    String key = objectSummary.getKey();
                    if (battles.containsKey(key))
                    {
                        continue;
                    }
                    S3Object s3Object = s3client.getObject(bucket, key);
                    byte[] bytes;
                    try (InputStream inputStream = s3Object.getObjectContent())
                    {
                        int length = (int) s3Object.getObjectMetadata().getContentLength();
                        bytes = IOUtils.readFully(inputStream, length);
                    }
                    triggerReplay(key, bytes, Instant.parse(s3Object.getObjectMetadata().getUserMetadata().get("timestamp")));
                }
                catch (Exception ex)
                {
                    logger.error("Error when running item", ex);
                }
            }
            req.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());
    }

    void triggerReplay(String key, byte[] replayBytes, Instant timestamp) throws IOException
    {
        List<BattleResults> battleCompletedEvent = BattleRunner.replay(replayBytes);
        battles.put(key, BattleStatistics
                .newBuilder()
                .results(battleCompletedEvent)
                .timestamp(timestamp)
                .build());
    }

    public Collection<BattleStatistics> getStatistics()
    {
        List<BattleStatistics> battleStatistics = new ArrayList<>(battles.values());
        battleStatistics.sort(Comparator.comparing(BattleStatistics::getTimestamp));
        return battleStatistics;
    }
}
