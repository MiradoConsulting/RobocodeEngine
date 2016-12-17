package com.mirado.robocode.guice;

import com.google.inject.AbstractModule;
import com.mirado.robocode.engine.BattleRunner;
import com.mirado.robocode.git.GitPoller;
import com.mirado.robocode.services.RoboService;
import com.mirado.robocode.services.ScoreService;

/**
 * Created by Kurt on 18/11/16.
 */
public class RoboModule extends AbstractModule
{
    protected void configure()
    {
        bind(BattleRunner.class).asEagerSingleton();
        bind(ScoreService.class).asEagerSingleton();
        bind(GitPoller.class).asEagerSingleton();
        bind(RoboService.class).asEagerSingleton();
    }
}
