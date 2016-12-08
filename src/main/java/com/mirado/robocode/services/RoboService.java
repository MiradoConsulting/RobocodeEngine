package com.mirado.robocode.services;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.util.Md5Utils;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.RobotSpec;
import com.mirado.robocode.engine.BattleRunner;
import com.mirado.robocode.engine.RobotCompiler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by Kurt on 04/12/16.
 */
public class RoboService
{
    private static final Logger logger = LoggerFactory.getLogger(RoboService.class);
    private static final File robotsDirectory = new File("robots");

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
    }

    private final Map<String, RobotSpec> robots = new HashMap<>();
    private final AmazonS3Client amazonS3Client;

    @Inject
    public RoboService(AmazonS3Client amazonS3Client)
    {
        this.amazonS3Client = amazonS3Client;
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

    public void runBattleAndUploadToS3()
    {
        String id = getCurrentSetupId();
        String s3Key = "runs/" + id;
        String s3Bucket = Config.getS3Bucket();
        if (amazonS3Client.doesObjectExist(s3Bucket, s3Key))
        {
            logger.info("Not running battle {} because it's already on s3 at {}", id, s3Key);
            return;
        }
        File file = BattleRunner.runBattle();
        if (file == null)
        {
            throw new RuntimeException("No output file from running battle");
        }
        amazonS3Client.putObject(s3Bucket, s3Key, file);
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
        return Md5Utils.md5AsBase64(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void recompile(RobotSpec robotSpec) throws IOException, InterruptedException
    {
        String relativePath = robotSpec.getPackageName().replace('.', '/') + "/" + robotSpec.getClassName() + ".java";
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
        Path propertiesPath = Paths.get(robotsDirectory.getAbsolutePath(), relativePath.replace(".java", ".properties"));
        Properties properties = new Properties();
        properties.put("robot.description", robotSpec.getOwner());
        properties.put("robot.webpage", robotSpec.getUrl());
        properties.put("robocode.version", Config.getRoboCodeVersion());
        properties.put("robot.java.source.included", "true");
        properties.put("robot.author.name", robotSpec.getOwner());
        properties.put("robot.classname", robotSpec.getPackageName() + "." + robotSpec.getClassName());
        properties.put("robot.name", robotSpec.getName());
        try (FileWriter fileWriter = new FileWriter(propertiesPath.toFile()))
        {
            properties.store(fileWriter, "");
        }
        RobotCompiler.compile(file);
    }
}
