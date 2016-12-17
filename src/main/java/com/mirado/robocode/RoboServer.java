package com.mirado.robocode;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.dropwizard.RobocodeApplication;
import com.mirado.robocode.git.GitPoller;
import com.mirado.robocode.guice.RoboModule;
import com.mirado.robocode.services.ScoreService;

import java.io.File;

/**
 * Created by Kurt on 18/11/16.
 */
public class RoboServer
{
    public static void main(String[] args) throws InterruptedException
    {
        try
        {
            if (args.length < 1)
            {
                System.out.println("Need to specify at least one configuration file");
                return;
            }
            String config = new File(args[0]).getAbsolutePath();
            System.setProperty("sun.io.useCanonCaches", "false");
            System.setProperty("NOSECURITY", "true");

            Config.init();
            Injector injector = Guice.createInjector(new RoboModule());
            RobocodeApplication application = new RobocodeApplication(injector);
            ScoreService scoreService = injector.getInstance(ScoreService.class);
            scoreService.start();
            GitPoller gitPoller = injector.getInstance(GitPoller.class);
            gitPoller.start();
            application.run("server", config);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            Thread.sleep(1000);
        }
    }

}
