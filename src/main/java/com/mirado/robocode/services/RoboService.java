package com.mirado.robocode.services;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.RobotSpec;
import com.mirado.robocode.engine.BattleRunner;
import com.mirado.robocode.engine.RobotCompiler;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.control.RobotResults;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps track of the existing robots, compiles them and triggers battles.
 */
public class RoboService
{
    private static final Logger logger = LoggerFactory.getLogger(RoboService.class);
    private static final File robotsDirectory = new File("robots");
    private static final File clojureClassesDirectory = Paths.get(robotsDirectory.getAbsolutePath(), "classes").toFile();
    private final Map<String, RobotSpec> robots = new HashMap<>();
    private final AmazonS3Client amazonS3Client;
    private final RobotCompiler robotCompiler;
    private final ScoreService scoreService;
    private final BattleRunner battleRunner;

    @Inject
    public RoboService(AmazonS3Client amazonS3Client, RobotCompiler robotCompiler, ScoreService scoreService, BattleRunner battleRunner)
    {
        this.amazonS3Client = amazonS3Client;
        this.robotCompiler = robotCompiler;
        this.scoreService = scoreService;
        this.battleRunner = battleRunner;
    }

    public void putRobotAndRecompile(String repoName, RobotSpec robotSpec) throws IOException, InterruptedException
    {
        robots.put(repoName, robotSpec);
        recompile(robotSpec);
    }

    public RobotSpec getRobot(String repoName)
    {
        return robots.get(repoName);
    }

    public void runBattleAndUploadToS3() throws IOException
    {
        String id = getCurrentSetupId();
        String s3Key = "runs/" + id;
        String s3Bucket = Config.getS3Bucket();
        if (amazonS3Client.doesObjectExist(s3Bucket, s3Key))
        {
            logger.info("Not running battle {} because it's already on s3 at {}", id, s3Key);
            return;
        }
        Pair<List<RobotResults>, File> pair = battleRunner.runBattle();
        if (pair == null)
        {
            throw new RuntimeException("No output from running battle");
        }
        File file = pair.getRight();
        Instant timestamp = Instant.now();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.addUserMetadata("timestamp", timestamp.toString());
        PutObjectRequest putObjectRequest = new PutObjectRequest(s3Bucket, s3Key, file)
                .withMetadata(objectMetadata);
        amazonS3Client.putObject(putObjectRequest);
        scoreService.storeResult(s3Key, pair.getKey(), timestamp);
    }

    /**
     * Gets an ID that is consistent for the same source codes and robot names.
     *
     * @return base64 md5 id
     */
    private String getCurrentSetupId()
    {                   //Make sure order doesn't matter
        Set<String> sources = new TreeSet<>();
        for (RobotSpec s : robots.values())
        {
            sources.add(s.getClassName() + s.getSource());
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : sources)
        {
            stringBuilder.append(s);
        }
        return DigestUtils.md5Hex(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void recompile(RobotSpec robotSpec) throws IOException, InterruptedException
    {
        String relativePath = robotSpec.getPackageName().replace('.', '/') + "/" + robotSpec.getClassName() + robotSpec.getSourceLanguage().getExtension();
        Path path = Paths.get(robotsDirectory.getAbsolutePath(), relativePath);
        File file = path.toFile();
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs())
        {
            throw new IllegalStateException("Could not create file " + file);
        }
        try (FileWriter fileWriter = new FileWriter(file))
        {
            IOUtils.write(robotSpec.getSource(), fileWriter);
        }
        Path propertiesPath = Paths.get(robotsDirectory.getAbsolutePath(), relativePath.replace(robotSpec.getSourceLanguage().getExtension(), ".properties"));
        Properties properties = new Properties();
        properties.put("robot.description", robotSpec.getOwner());
        properties.put("robot.webpage", robotSpec.getUrl());
        properties.put("robocode.version", Config.getRoboCodeVersion());
        properties.put("robot.java.source.included", "true");
        properties.put("robot.author.name", robotSpec.getOwner());
        properties.put("robot.classname", robotSpec.getPackageName() + "." + robotSpec.getClassName());
        properties.put("robot.name", robotSpec.getName());
        properties.put("robot.version", robotSpec.getVersion());
        try (FileWriter fileWriter = new FileWriter(propertiesPath.toFile()))
        {
            properties.store(fileWriter, "");
        }
        robotCompiler.compile(file, robotSpec.getSourceLanguage());
    }

    static
    {
        System.setProperty("ROBOTPATH", robotsDirectory.getAbsolutePath());
        if (!robotsDirectory.exists())
        {
            if (!robotsDirectory.mkdir())
            {
                throw new IllegalStateException("Could not create dir " + robotsDirectory.getAbsolutePath());
            }
        }
        if (!clojureClassesDirectory.exists())
        {
            if (!clojureClassesDirectory.mkdir())
            {
                throw new IllegalStateException("Could not create dir " + clojureClassesDirectory.getAbsolutePath());
            }
        }
    }
}
