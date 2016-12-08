package com.mirado.robocode;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.dropwizard.RobocodeApplication;
import com.mirado.robocode.git.GitPoller;
import com.mirado.robocode.guice.RoboModule;
import com.mirado.robocode.services.ScoreService;

/**
 * Created by Kurt on 18/11/16.
 */
public class RoboServer
{
    public static void main(String[] args) throws InterruptedException
    {
        try
        {
            Config.init();
            Injector injector = Guice.createInjector(new RoboModule());
            RobocodeApplication application = new RobocodeApplication(injector);
            ScoreService scoreService = injector.getInstance(ScoreService.class);
            scoreService.start();
            //GitPoller gitPoller = injector.getInstance(GitPoller.class);
            //gitPoller.start();
            application.run("server", Thread.currentThread().getContextClassLoader().getResource("config.yaml").getPath());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            Thread.sleep(1000);
        }
    }

}
