package com.mirado.robocode.engine;

import net.sf.robocode.battle.IBattleManagerBase;
import net.sf.robocode.core.ContainerBase;
import net.sf.robocode.recording.BattleRecordFormat;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * Created by Kurt on 04/12/16.
 */
public class BattleRunner
{
    private static final Logger logger = LoggerFactory.getLogger(BattleRunner.class);

    public static File runBattle()
    {
        RobocodeEngine robocodeEngine = new RobocodeEngine();
        IBattleManagerBase iBattleManagerBase = ContainerBase.getComponent(IBattleManagerBase.class);
        final File[] recordedFile = new File[1];
        iBattleManagerBase.addListener(new IBattleListener()
        {
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
                try
                {
                    recordedFile[0] = recordFile(iBattleManagerBase);
                    logger.info("Recorded to file {} ", recordedFile[0].getAbsolutePath());
                }
                catch (Throwable e)
                {
                    logger.error("Could not save file", e);
                }
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
        });
        iBattleManagerBase.startNewBattle(new BattleSpecification(10, new BattlefieldSpecification(), robocodeEngine.getLocalRepository()), null, true, true);
        return recordedFile[0];
    }

    public static BattleCompletedEvent replay(byte[] bytes) throws IOException
    {
        try
        {
            File tempFile = File.createTempFile("temp", "recording");
            FileUtils.writeByteArrayToFile(tempFile, bytes);
            IBattleManagerBase battleManager = ContainerBase.getComponent(IBattleManagerBase.class);
            Field recordManagerField = battleManager.getClass().getDeclaredField("recordManager");
            recordManagerField.setAccessible(true);
            Object recordManager = recordManagerField.get(battleManager);
            Optional<Method> maybeLoadRecord = Arrays.stream(recordManager.getClass().getMethods()).filter(c -> "loadRecord".equals(c.getName())).findAny();
            if (!maybeLoadRecord.isPresent())
            {
                throw new RuntimeException("Could not find load record method");
            }
            Method loadRecord = maybeLoadRecord.get();
            loadRecord.invoke(recordManager, tempFile.getAbsolutePath(), Enum.valueOf((Class<Enum>) loadRecord.getParameterTypes()[1], "BINARY_ZIP"));

            final BattleCompletedEvent[] event = new BattleCompletedEvent[1];
            battleManager.addListener(new IBattleListener()
            {
                @Override
                public void onBattleStarted(BattleStartedEvent battleStartedEvent)
                {

                }

                @Override
                public void onBattleFinished(BattleFinishedEvent battleFinishedEvent)
                {

                }

                @Override
                public void onBattleCompleted(BattleCompletedEvent battleCompletedEvent)
                {
                    event[0] = battleCompletedEvent;
                }

                @Override
                public void onBattlePaused(BattlePausedEvent battlePausedEvent)
                {

                }

                @Override
                public void onBattleResumed(BattleResumedEvent battleResumedEvent)
                {

                }

                @Override
                public void onRoundStarted(RoundStartedEvent roundStartedEvent)
                {

                }

                @Override
                public void onRoundEnded(RoundEndedEvent roundEndedEvent)
                {

                }

                @Override
                public void onTurnStarted(TurnStartedEvent turnStartedEvent)
                {

                }

                @Override
                public void onTurnEnded(TurnEndedEvent turnEndedEvent)
                {

                }

                @Override
                public void onBattleMessage(BattleMessageEvent battleMessageEvent)
                {

                }

                @Override
                public void onBattleError(BattleErrorEvent battleErrorEvent)
                {

                }
            });
            Method replay = battleManager.getClass().getDeclaredMethod("replay");
            replay.setAccessible(true);
            replay.invoke(battleManager);
            battleManager.waitTillOver();
            return event[0];
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static File recordFile(IBattleManagerBase iBattleManagerBase)
    {
        try
        {
            Field field = iBattleManagerBase.getClass().getDeclaredField("recordManager");
            field.setAccessible(true);
            Object recordManager = field.get(iBattleManagerBase);
            //This is all in another class loader so have to use ugly reflection hacks
            Optional<Method> maybeMethod = Arrays.stream(recordManager.getClass().getMethods()).filter(c -> "saveRecord".equals(c.getName())).findAny();
            if (!maybeMethod.isPresent())
            {
                throw new IllegalArgumentException("Could not find method saveRecord!");
            }
            Method method = maybeMethod.get();
            File file = File.createTempFile("temp", Long.toString(System.nanoTime()));
            Enum battleRecordFormat = Enum.valueOf((Class<Enum>) method.getParameterTypes()[1], "BINARY_ZIP");
            Object serializableOptions = method.getParameterTypes()[2].getConstructor(boolean.class).newInstance(false);
            method.invoke(recordManager, file.getAbsolutePath(), battleRecordFormat, serializableOptions);
            return file;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
