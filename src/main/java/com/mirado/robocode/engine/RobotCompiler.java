package com.mirado.robocode.engine;

import com.mirado.robocode.archaius.Config;
import net.sf.robocode.io.FileUtil;
import net.sf.robocode.ui.editor.CompilerProperties;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static net.sf.robocode.io.Logger.logError;

public class RobotCompiler
{
    private static final Logger logger = LoggerFactory.getLogger(RobotCompiler.class);
    private static final String COMPILER_CLASSPATH = "-classpath " + getJavaLib() + File.pathSeparator
            + FileUtil.getCwd().getAbsolutePath() + "/libs" + File.separator + "robocode.jar"
            + File.pathSeparator + FileUtil.quoteFileName(FileUtil.getRobotsDir().toString());
    private static boolean compilerTested = false;

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


    public static void compile(File file) throws InterruptedException, IOException
    {
        final String baseDirName = FileUtil.getRobotsDir().getAbsolutePath();
        CompilerProperties compilerProperties = getCompilerProperties();
        File baseDir = new File(baseDirName);
        String fileName = file.toString().substring(baseDirName.length() + 1);
        compileFile(fileName, baseDir, compilerProperties);
    }

    private static void compileFile(String fileName, File pwd, CompilerProperties compilerProperties) throws InterruptedException, IOException
    {
        fileName = FileUtil.quoteFileName(fileName);

        StringBuffer command = new StringBuffer(compilerProperties.getCompilerBinary())
                .append(' ').append(compilerProperties.getCompilerOptions())
                .append(' ').append(compilerProperties.getCompilerClasspath()).append(' ').append(
                        fileName);

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
            logger.error("Compile Failed: {}", p.exitValue());
        }
    }

    private static CompilerProperties getCompilerProperties()
    {
        String compilerBinary = "javac";
        String compilerOptions = "-deprecation -g -source 1.6 -encoding UTF-8";

        if (!testCompiler(compilerBinary))
        {
            throw new IllegalArgumentException("Could not find javac");
        }
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setRobocodeVersion(Config.getRoboCodeVersion());
        compilerProperties.setCompilerBinary(compilerBinary);
        compilerProperties.setCompilerOptions(compilerOptions);
        compilerProperties.setCompilerClasspath(COMPILER_CLASSPATH);

        return compilerProperties;
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
}
