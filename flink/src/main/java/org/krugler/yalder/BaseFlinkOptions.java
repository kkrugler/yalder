package org.krugler.yalder;

import java.io.Serializable;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.kohsuke.args4j.Option;

public abstract class BaseFlinkOptions implements Serializable {
    public static final int DEFAULT_PARALLELISM = -1;
    
    private int _parallelism = DEFAULT_PARALLELISM;
    private boolean _createGui = false;
    private String _workingDir;
    
    public int getParallelism() {
        return _parallelism;
    }
    
    @Option(name = "-parallelism", usage = "Default parallelism for workflow", required = false)
    public void setParallelism(int parallelism) {
        _parallelism = parallelism;
    }
    
    public boolean isCreateGui() {
        return _createGui;
    }
    
    @Option(name = "-gui", usage = "Create local GUI (http://localhost:8081, implies local mode)", required = false)
    public void setCreateGui(boolean createGui) {
        _createGui = createGui;
    }
    
    public String getWorkingDir() {
        return _workingDir;
    }

    @Option(name = "-workingdir", usage = "Location of working directory (can be in S3)", required = true)
    public void setWorkingDir(String workingDir) {
        _workingDir = workingDir;
    }

    public String getWorkingSubdir(String subdirName) {
        return String.format("%s%s%s", _workingDir, _workingDir.endsWith("/") ? "" : "/", subdirName);
    }
    
    public String getWorkingFileWithPattern(String fileNamePattern, String param) {
        return getWorkingFile(fileNamePattern.replaceAll("%s", param));
    }
    
    public String getWorkingFile(String fileName) {
        return String.format("%s%s%s", _workingDir, _workingDir.endsWith("/") ? "" : "/", fileName);
    }
    
    public ExecutionEnvironment makeEnvironment() {
        ExecutionEnvironment env = null;
        
        if (isCreateGui()) {
            Configuration config = new Configuration();
            config.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, true);
            env = ExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        } else {
            env = ExecutionEnvironment.getExecutionEnvironment();
        }
        
        if (getParallelism() != DEFAULT_PARALLELISM) {
            env.setParallelism(getParallelism());
        }

        return env;
    }
}
