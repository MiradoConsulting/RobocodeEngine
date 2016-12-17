package com.mirado.robocode.engine;

import com.mirado.robocode.archaius.Config;
import com.mirado.robocode.domain.SourceLanguage;
import com.mirado.robocode.services.NotificationService;
import net.sf.robocode.io.FileUtil;
import net.sf.robocode.ui.editor.CompilerProperties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static net.sf.robocode.io.Logger.logError;

public class RobotCompiler
{
    private static final Logger logger = LoggerFactory.getLogger(RobotCompiler.class);
    private static final String COMPILER_CLASSPATH = getJavaLib() + File.pathSeparator
            + FileUtil.getCwd().getAbsolutePath() + "/libs" + File.separator + "robocode.jar"
            + File.pathSeparator + FileUtil.quoteFileName(FileUtil.getRobotsDir().toString());
    private static boolean compilerTested = false;
    private final NotificationService notificationService;

    @Inject
    public RobotCompiler(NotificationService notificationService)
    {
        this.notificationService = notificationService;
    }

    private static String getJavaLib()
    {
        String javahome = System.getProperty("java.home");
        String javalib;
        if (System.getProperty("os.name").indexOf("Mac") == 0)
        {
            javalib = new File(javahome).getParentFile().getPath() + "/Classes/classes.jar";
        }
        else
        {
            javalib = javahome + "/lib/rt.jar";
        }

        return FileUtil.quoteFileName(javalib);
    }

    private static CompilerProperties getCompilerProperties(SourceLanguage language)
    {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setRobocodeVersion(Config.getRoboCodeVersion());
        if (language == SourceLanguage.JAVA)
        {
            String compilerBinary = "javac";
            String compilerOptions = "-deprecation -g -source 1.6 -encoding UTF-8";

            if (!testCompiler(compilerBinary))
            {
                throw new IllegalArgumentException("Could not find javac");
            }
            compilerProperties.setCompilerBinary(compilerBinary);
            compilerProperties.setCompilerOptions(compilerOptions);
            compilerProperties.setCompilerClasspath("-classpath " + COMPILER_CLASSPATH);
            return compilerProperties;
        }
        else if (language == SourceLanguage.CLOJURE)
        {
            String classPath = COMPILER_CLASSPATH + File.pathSeparator + FileUtil.getCwd().getAbsolutePath() + "/libs" + File.separator + "clojure.jar";
            compilerProperties.setCompilerBinary("java");
            compilerProperties.setCompilerClasspath("-cp " + classPath);
            compilerProperties.setCompilerOptions("-Dclojure.compile.path=classes clojure.lang.Compile");
            return compilerProperties;
        }
        else
        {
            throw new IllegalArgumentException("Unknown language " + language);
        }
    }

    /**
     * Tests a compiler by trying to let it compile the CompilerTest.java file.
     *
     * @param filepath the file path of the compiler.
     *
     * @return true if the compiler was found and did compile the test file; false otherwise.
     */
    private static boolean testCompiler(String filepath)
    {
        if (compilerTested)
        {
            return true;
        }
        boolean result = false;

        try
        {
            String cmdAndArgs = filepath + " -version";

            // Must be split command and arguments individually
            ProcessBuilder pb = new ProcessBuilder(cmdAndArgs.split(" "));

            pb.directory(FileUtil.getCwd());
            pb.redirectErrorStream(true); // we can use p.getInputStream()
            Process p = pb.start();

            // The waitFor() must done after reading the input and error stream of the process
            String output = IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8);
            logger.info("Output from compiler: {}", output);
            p.waitFor();

            result = (p.exitValue() == 0);

        }
        catch (IOException e)
        {
            logError(e);
        }
        catch (InterruptedException e)
        {
            // Immediately reasserts the exception by interrupting the caller thread itself
            Thread.currentThread().interrupt();
        }
        if (result)
        {
            compilerTested = true;
        }
        return result;
    }

    private String getCompileFileName(String fileName, SourceLanguage language)
    {
        if (language == SourceLanguage.JAVA)
        {
            return fileName;
        }
        else if (language == SourceLanguage.CLOJURE)
        {
            if (fileName.endsWith(".clj"))
            {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            return fileName.replace('/', '.');
        }
        else
        {
            throw new IllegalArgumentException("Unknown language " + language);
        }
    }

    private void compileFile(String fileName, File pwd, SourceLanguage language) throws InterruptedException, IOException
    {
        CompilerProperties compilerProperties = getCompilerProperties(language);
        fileName = FileUtil.quoteFileName(fileName);

        StringBuffer command = new StringBuffer(compilerProperties.getCompilerBinary())
                .append(' ').append(compilerProperties.getCompilerClasspath())
                .append(' ').append(compilerProperties.getCompilerOptions())
                .append(' ').append(getCompileFileName(fileName, language));

        logger.info("Compile command: {}", command);

        ProcessBuilder pb = new ProcessBuilder(command.toString().split(" "));

        pb.redirectErrorStream(true);
        pb.directory(pwd);
        Process p = pb.start();

        // The waitFor() must done after reading the input and error stream of the process
        String output = IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8);
        logger.info("Compiler output: {}", output);
        p.waitFor();

        if (p.exitValue() == 0)
        {
            logger.info("Compiled successfully");
        }
        else
        {
            notificationService.notify("Robot " + fileName + " didn't compile! Output: " + output);
            logger.error("Compile Failed: {}", p.exitValue());
        }
    }

    public void compile(File file, SourceLanguage language) throws InterruptedException, IOException
    {
        final String baseDirName = FileUtil.getRobotsDir().getAbsolutePath();
        File baseDir = new File(baseDirName);
        String fileName = file.toString().substring(baseDirName.length() + 1);
        compileFile(fileName, baseDir, language);
        if (language == SourceLanguage.CLOJURE)
        {
            File classesDirectory = new File("robots/classes");
            FileUtils.copyDirectory(classesDirectory, new File("robots"));
            FileUtils.cleanDirectory(classesDirectory);
        }
    }
}
