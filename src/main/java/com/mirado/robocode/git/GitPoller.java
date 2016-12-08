package com.mirado.robocode.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.RobotSpec;
import com.mirado.robocode.services.RoboService;
import com.netflix.archaius.api.Property;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls a github organization for robots
 */
public class GitPoller
{
    private static final Property<String> CLIENT_ID = Config.getPropertyFactory().getProperty("github.client_id").asString(null);
    private static final Property<String> CLIENT_SECRET = Config.getPropertyFactory().getProperty("github.client_secret").asString(null);
    private static final Logger logger = LoggerFactory.getLogger(GitPoller.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Timer timer = new Timer(true);
    private final Map<String, Instant> LAST_CHECKED = new HashMap<>();
    private final RoboService roboService;

    @Inject
    public GitPoller(RoboService roboService)
    {
        this.roboService = roboService;
    }

    public void start()
    {
        GitPoller gitPoller = this;
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                gitPoller.run();
            }
        }, 0, 60000);
    }

    private void run()
    {
        String url = "https://api.github.com/orgs/" + getGithubOrganization() + "/repos";
        try
        {
            ArrayNode repositoriesNode = (ArrayNode) readUrl(url);
            boolean modified = false;
            for (JsonNode repo : repositoriesNode)
            {
                String repoName = repo.get("name").asText();
                Instant lastPushed = Instant.parse(repo.get("pushed_at").asText());
                RobotSpec existing = roboService.getRobot(repoName);
                if (existing != null)
                {
                    if (existing.getLastPushed().equals(lastPushed))
                    {
                        continue;
                    }
                }
                //To not check the same repo too often that we know we can't
                if (LAST_CHECKED.containsKey(repoName) && LAST_CHECKED.get(repoName).plusSeconds(60 * 15).isBefore(Instant.now()))
                {
                    continue;
                }
                JsonNode robotJson = getRobotJson(repoName);
                SourceSpec sourceSpec = findRobot(readUrl("https://api.github.com/repos/" + getGithubOrganization() + "/" + repoName + "/contents/src?ref=master"));
                if (sourceSpec == null)
                {
                    LAST_CHECKED.put(repoName, Instant.now());
                    continue;
                }
                RobotSpec robotSpec = RobotSpec
                        .newBuilder()
                        .lastPushed(lastPushed)
                        .name(sourceSpec.className)
                        .owner(robotJson.get("owner").asText())
                        .url(repo.get("html_url").asText())
                        .className(sourceSpec.className)
                        .source(sourceSpec.source)
                        .packageName(sourceSpec.packageName)
                        .build();
                roboService.putRobotAndRecompile(repoName, robotSpec);
                modified = true;
            }
            if (modified)
            {
                roboService.runBattleAndUploadToS3();
            }
        }
        catch (IOException | InterruptedException e)
        {
            logger.error("", e);
        }
    }

    private static SourceSpec findRobot(JsonNode directory) throws IOException
    {
        for (JsonNode node : directory)
        {
            if (!"dir".equals(node.get("type").asText()))
            {
                String fileName = node.get("name").asText();
                if (fileName.endsWith(".java"))
                {
                    String className = fileName.split("\\.")[0];
                    String content = IOUtils.toString(withSecret(node.get("download_url").asText()), StandardCharsets.UTF_8);
                    Matcher matcher = Pattern.compile(className + " extends [^\\s]*Robot").matcher(content);
                    if (matcher.find())
                    {
                        Matcher packageMatcher = Pattern.compile("package ([^\\s]*);").matcher(content);
                        String packageName;
                        if (packageMatcher.find())
                        {
                            packageName = packageMatcher.group(1);
                        }
                        else
                        {
                            packageName = "";
                        }
                        SourceSpec sourceSpec = new SourceSpec();
                        sourceSpec.className = className;
                        sourceSpec.source = content;
                        sourceSpec.packageName = packageName;

                        return sourceSpec;
                    }
                }
            }
            else
            {
                SourceSpec robot = findRobot(readUrl(node.get("url").asText()));
                if (robot != null)
                {
                    return robot;
                }
            }
        }
        return null;
    }

    private static JsonNode readUrl(String urlString) throws IOException
    {
        return OBJECT_MAPPER.readTree(withSecret(urlString));
    }

    private static URL withSecret(String urlString) throws MalformedURLException
    {
        String clientId = CLIENT_ID.get();
        String clientSecret = CLIENT_SECRET.get();
        UriBuilder uriBuilder = UriBuilder
                .fromUri(urlString);
        if (clientId != null && clientSecret != null)
        {
            uriBuilder = uriBuilder
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret);
        }
        URL url = uriBuilder
                .build()
                .toURL();
        return url;
    }

    private static JsonNode getRobotJson(String repoName) throws IOException
    {
        String url = "https://raw.githubusercontent.com/" + getGithubOrganization() + "/" + repoName + "/master/robot.json";
        return readUrl(url);
    }

    private static String getGithubOrganization()
    {
        return "MiradoConsulting";
    }

    private static final class SourceSpec
    {
        private String className;
        private String source;
        private String packageName;
    }
}
