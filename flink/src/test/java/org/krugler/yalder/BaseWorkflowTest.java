package org.krugler.yalder;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.flink.api.java.ExecutionEnvironment;

public abstract class BaseWorkflowTest {

    public abstract File getClassDir();
    
    protected File getTestDir(String subdir) throws IOException {
        return getTestDir(subdir, true);
    }

    protected File getTestDir(String subdir, boolean clean) throws IOException {
        File testDir = new File(getClassDir(), subdir);
        if (clean) {
            FileUtils.deleteDirectory(testDir);
        }
        
        testDir.mkdirs();
        return testDir;
    }

    protected ExecutionEnvironment getEnvironment(File workingDir) {
        System.setProperty("log.file", workingDir.getAbsolutePath());
        ExecutionEnvironment env = ExecutionEnvironment.createLocalEnvironment();
        return env;
    }

    protected ExecutionEnvironment getEnvironment(File workingDir, int parallelism) {
        ExecutionEnvironment env = getEnvironment(workingDir);
        env.setParallelism(parallelism);
        return env;
    }
}
