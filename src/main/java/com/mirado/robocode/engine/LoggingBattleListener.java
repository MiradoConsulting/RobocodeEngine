package com.mirado.robocode.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;
import robocode.control.events.BattleFinishedEvent;
import robocode.control.events.BattleMessageEvent;
import robocode.control.events.BattlePausedEvent;
import robocode.control.events.BattleResumedEvent;
import robocode.control.events.BattleStartedEvent;
import robocode.control.events.IBattleListener;
import robocode.control.events.RoundEndedEvent;
import robocode.control.events.RoundStartedEvent;
import robocode.control.events.TurnEndedEvent;
import robocode.control.events.TurnStartedEvent;

/**
 * Created by oskarkjellin on 2016-12-17.
 */
public class LoggingBattleListener implements IBattleListener
{
    private static final Logger logger = LoggerFactory.getLogger(LoggingBattleListener.class);
    private BattleCompletedEvent battleCompletedEvent;

    public BattleCompletedEvent getBattleCompletedEvent()
    {
        return battleCompletedEvent;
    }

    @Override
    public void onBattleStarted(BattleStartedEvent event)
    {
        logger.info("Starting battle for {} robots", event.getRobotsCount());
    }

    @Override
    public void onBattleFinished(BattleFinishedEvent event)
    {
        logger.info("Battle finished. isAborted:{}", event.isAborted());
    }

    @Override
    public void onBattleCompleted(BattleCompletedEvent event)
    {
        logger.info("Battle completed");
        battleCompletedEvent = event;
    }

    @Override
    public void onBattlePaused(BattlePausedEvent event)
    {

    }

    @Override
    public void onBattleResumed(BattleResumedEvent event)
    {

    }

    @Override
    public void onRoundStarted(RoundStartedEvent event)
    {
        logger.info("Starting round {}", event.getRound());
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event)
    {
        logger.info("Round {} done", event.getRound());
    }

    @Override
    public void onTurnStarted(TurnStartedEvent event)
    {
    }

    @Override
    public void onTurnEnded(TurnEndedEvent event)
    {
    }

    @Override
    public void onBattleMessage(BattleMessageEvent event)
    {
        logger.info(event.getMessage());
    }

    @Override
    public void onBattleError(BattleErrorEvent event)
    {
        logger.error("Error when running battle {}", event.getError());
    }
}
