package com.mirado.robocode.engine;

import com.mirado.robocode.archaius.Config;
import net.sf.robocode.battle.IBattleManagerBase;
import net.sf.robocode.core.ContainerBase;
import net.sf.robocode.repository.IRepositoryManager;
import net.sf.robocode.ui.IWindowManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotResults;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleCompletedEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs battles, records to files and replays files.
 */
@SuppressWarnings("unchecked")
public class BattleRunner
{
    private static final Logger logger = LoggerFactory.getLogger(BattleRunner.class);
    private final RobocodeEngine robocodeEngine;
    private final IBattleManagerBase iBattleManagerBase;
    private final IRepositoryManager repositoryManager;
    private final IWindowManager windowManager;
    private final Object lock = new Object();
    private final LoggingBattleListener loggingBattleListener = new LoggingBattleListener();

    public BattleRunner()
    {
        robocodeEngine = new RobocodeEngine();
        iBattleManagerBase = ContainerBase.getComponent(IBattleManagerBase.class);
        repositoryManager = ContainerBase.getComponent(IRepositoryManager.class);
        windowManager = ContainerBase.getComponent(IWindowManager.class);
        if (Config.enableUi())
        {
            windowManager.setSlave(false);
            windowManager.setEnableGUI(true);
            windowManager.init();
            windowManager.showRobocodeFrame(true, false);
        }
        iBattleManagerBase.addListener(loggingBattleListener);
    }

    public Pair<List<RobotResults>, File> runBattle()
    {
        repositoryManager.reload(true);
        RobotSpecification[] robots = robocodeEngine.getLocalRepository();
        synchronized (lock)
        {
            iBattleManagerBase.startNewBattle(new BattleSpecification(10, new BattlefieldSpecification(), robots), null, true, true);
        }
        try
        {
            final File file = recordFile(iBattleManagerBase);
            logger.info("Recorded to file {} ", file.getAbsolutePath());
            BattleCompletedEvent battleCompletedEvent = loggingBattleListener.getBattleCompletedEvent();
            if (battleCompletedEvent == null)
            {
                throw new IllegalStateException("Could not get results :(");
            }
            List<RobotResults> results = Arrays.stream(battleCompletedEvent.getSortedResults())
                    .map(result -> (RobotResults) result)
                    .collect(Collectors.toList());
            return Pair.of(results, file);
        }
        catch (Throwable e)
        {
            logger.error("Could not save file", e);
            return null;
        }
    }

    public List<RobotResults> replay(byte[] bytes) throws IOException
    {
        try
        {
            File tempFile = File.createTempFile("temp", "recording");
            FileUtils.writeByteArrayToFile(tempFile, bytes);
            Field recordManagerField = iBattleManagerBase.getClass().getDeclaredField("recordManager");
            recordManagerField.setAccessible(true);
            Object recordManager = recordManagerField.get(iBattleManagerBase);
            Optional<Method> maybeLoadRecord = Arrays.stream(recordManager.getClass().getMethods()).filter(c -> "loadRecord".equals(c.getName())).findAny();
            if (!maybeLoadRecord.isPresent())
            {
                throw new RuntimeException("Could not find load record method");
            }
            Method loadRecord = maybeLoadRecord.get();
            loadRecord.invoke(recordManager, tempFile.getAbsolutePath(), Enum.valueOf((Class<Enum>) loadRecord.getParameterTypes()[1], "BINARY_ZIP"));
            Method replay = iBattleManagerBase.getClass().getDeclaredMethod("replay");
            synchronized (lock)
            {
                replay.setAccessible(true);
                replay.invoke(iBattleManagerBase);
                iBattleManagerBase.waitTillOver();
            }
            Field recordInfoField = recordManager.getClass().getDeclaredField("recordInfo");
            recordInfoField.setAccessible(true);
            Object recordInfo = recordInfoField.get(recordManager);
            Field resultsField = recordInfo.getClass().getDeclaredField("results");
            resultsField.setAccessible(true);
            Object resultsObject = resultsField.get(recordInfo);
            List<RobotResults> results = (List<RobotResults>) resultsObject;
            return results;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private File recordFile(IBattleManagerBase iBattleManagerBase)
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
