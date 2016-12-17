package com.mirado.robocode.services;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.BattleStatistics;
import com.mirado.robocode.domain.Scoreboard;
import com.mirado.robocode.engine.BattleRunner;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.BattleResults;
import robocode.control.RobotResults;
import robocode.control.RobotSpecification;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Checks S3 for replay files, replays them and tracks the overall scoreboard
 */
public class ScoreService
{
    private static final Logger logger = LoggerFactory.getLogger(ScoreService.class);
    private final Timer timer = new Timer(true);
    private final AmazonS3Client s3client;
    private final Map<String, BattleStatistics> battles = new HashMap<>();
    private final BattleRunner battleRunner;

    @Inject
    public ScoreService(AmazonS3Client s3client, BattleRunner battleRunner)
    {
        this.s3client = s3client;
        this.battleRunner = battleRunner;
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

    private void triggerReplay(String key, byte[] replayBytes, Instant timestamp) throws IOException
    {
        List<RobotResults> results = battleRunner.replay(replayBytes);
        storeResult(key, results, timestamp);
    }

    void storeResult(String key, List<RobotResults> results, Instant timestamp)
    {
        results.sort(Comparator.comparing(BattleResults::getRank));
        battles.put(key, BattleStatistics
                .newBuilder()
                .results(results)
                .timestamp(timestamp)
                .build());
    }

    public Scoreboard getStatistics()
    {
        List<BattleStatistics> battleStatistics = new ArrayList<>(battles.values());
        battleStatistics.sort(Comparator.comparing(BattleStatistics::getTimestamp));
        Collections.reverse(battleStatistics);
        List<String> robotNames = battleStatistics
                .stream()
                .flatMap(c -> c.getResults().stream())
                .map(RobotResults::getRobot)
                .map(RobotSpecification::getName)
                .distinct()
                .collect(Collectors.toList());
        List<Pair<String, Integer>> scoresPerRobot = robotNames
                .stream()
                .map(name -> Pair.of(name, getStatisticsForRobot(battleStatistics, name)
                        .stream()
                        .mapToInt(BattleResults::getScore)
                        .sum()))
                .sorted(Comparator.comparingInt((ToIntFunction<Pair<String, Integer>>) Pair::getRight).reversed())
                .collect(Collectors.toList());

        List<RobotResults> scoreBoard = new ArrayList<>();
        for (String robotName : robotNames)
        {
            List<RobotResults> robotStats = getStatisticsForRobot(battleStatistics, robotName);
            RobotResults robotResults = robotStats.get(0);
            int rank = scoresPerRobot.indexOf(scoresPerRobot.stream().filter(c -> c.getLeft().equals(robotName)).findAny().get()) + 1;
            int score = robotStats.stream().mapToInt(BattleResults::getScore).sum();
            int survival = robotStats.stream().mapToInt(BattleResults::getSurvival).sum();
            int lastSurvivorBonus = robotStats.stream().mapToInt(BattleResults::getLastSurvivorBonus).sum();
            int bulletDamage = robotStats.stream().mapToInt(BattleResults::getBulletDamage).sum();
            int bulletDamageBonus = robotStats.stream().mapToInt(BattleResults::getBulletDamageBonus).sum();
            int ramDamage = robotStats.stream().mapToInt(BattleResults::getRamDamage).sum();
            int ramDamageBonus = robotStats.stream().mapToInt(BattleResults::getRamDamageBonus).sum();
            int firsts = robotStats.stream().mapToInt(BattleResults::getFirsts).sum();
            int seconds = robotStats.stream().mapToInt(BattleResults::getSeconds).sum();
            int thirds = robotStats.stream().mapToInt(BattleResults::getThirds).sum();
            scoreBoard.add(new RobotResults(
                    robotResults.getRobot(),
                    robotResults.getTeamLeaderName(),
                    rank, score, survival, lastSurvivorBonus, bulletDamage, bulletDamageBonus, ramDamage, ramDamageBonus,
                    firsts, seconds, thirds));
        }
        scoreBoard.sort(Comparator.comparing(BattleResults::getRank));
        return Scoreboard
                .newBuilder()
                .battleStatistics(battleStatistics)
                .scoreBoard(scoreBoard)
                .build();
    }

    private static List<RobotResults> getStatisticsForRobot(List<BattleStatistics> battleStatistics, String robotName)
    {
        return battleStatistics
                .stream()
                .flatMap(c -> c.getResults().stream())
                .filter(c -> c.getRobot().getName().equals(robotName))
                .collect(Collectors.toList());
    }
}
