package com.mirado.robocode.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.RobotSpec;
import com.mirado.robocode.domain.SourceLanguage;
import com.mirado.robocode.services.RoboService;
import com.netflix.archaius.api.Property;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.io.FileNotFoundException;
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

    private static SourceSpec findRobot(JsonNode directory) throws IOException
    {
        for (JsonNode node : directory.get("tree"))
        {
            if (!"tree".equals(node.get("type").asText()))
            {
                String fileName = node.get("path").asText();
                String className = fileName.split("\\.")[0];
                if (fileName.endsWith(".java") || fileName.endsWith(".clj"))
                {
                    JsonNode blobNode = readUrl(node.get("url").asText());
                    String content = new String(Base64.decodeBase64(blobNode.get("content").asText()), StandardCharsets.UTF_8);
                    SourceSpec sourceSpec = null;
                    if (fileName.endsWith(".java"))
                    {
                        sourceSpec = fromJavaFile(className, content);
                    }
                    else if (fileName.endsWith(".clj"))
                    {
                        sourceSpec = fromClojureFile(className, content);
                    }
                    if (sourceSpec != null)
                    {
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

    private static SourceSpec fromClojureFile(String className, String content) throws MalformedURLException
    {
        if (extendsClojureRobot(content))
        {
            Matcher packageMatcher = Pattern.compile("\\(ns ([^\\s]*)\\." + className).matcher(content);
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
            sourceSpec.language = SourceLanguage.CLOJURE;
            return sourceSpec;
        }
        return null;
    }

    private static SourceSpec fromJavaFile(String className, String content) throws MalformedURLException
    {
        if (extendsJavaRobot(className, content))
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
            sourceSpec.language = SourceLanguage.JAVA;
            return sourceSpec;
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

    private static boolean extendsClojureRobot(String content)
    {
        Matcher matcher = Pattern.compile("\\:gen-class :extends [^\\s]*Robot").matcher(content);
        return matcher.find();
    }

    private static boolean extendsJavaRobot(String className, String content)
    {
        Matcher matcher = Pattern.compile(className + " extends [^\\s]*Robot").matcher(content);
        return matcher.find();
    }

    private static JsonNode getRobotJson(String repoName) throws IOException
    {
        try
        {
            String url = "https://raw.githubusercontent.com/" + getGithubOrganization() + "/" + repoName + "/master/robot.json";
            return readUrl(url);
        }
        catch (FileNotFoundException ex)
        {
            return null;
        }
    }

    private static String getGithubOrganization()
    {
        return "MiradoConsulting";
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
                if (robotJson == null)
                {
                    continue;
                }
                String version = getVersion(repoName);
                SourceSpec sourceSpec = findRobot(readUrl("https://api.github.com/repos/" + getGithubOrganization() + "/" + repoName + "/git/trees/" + version));
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
                        .version(version)
                        .sourceLanguage(sourceSpec.language)
                        .build();
                roboService.putRobotAndRecompile(repoName, robotSpec);
                modified = true;
            }
            if (modified)
            {
                roboService.runBattleAndUploadToS3();
            }
        }
        catch (Exception e)
        {
            logger.error("", e);
        }
    }

    private String getVersion(String repoName) throws IOException
    {
        JsonNode commits = readUrl("https://api.github.com/repos/" + getGithubOrganization() + "/" + repoName + "/commits");
        return commits.get(0).get("sha").asText();
    }

    private static final class SourceSpec
    {
        private String className;
        private String source;
        private String packageName;
        private SourceLanguage language;
    }
}
