package org.krugler.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.junit.Test;

public class AnalyzeTextWorkflowTest extends BaseWorkflowTest {

	@Override
	public File getClassDir() {
		return new File("target/test/AnalyzeTextWorkflowTest");
	}

	@Test
	public void test() throws Exception {
        File workingDir = getTestDir("test");
        
        AnalyzeTextOptions options = new AnalyzeTextOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());
        
        run(options);
        
        // TODO validate results.
    }
	
	protected static void run(AnalyzeTextOptions options) throws Exception {
		run(options, "src/test/resources/wikipedia/");
	}

	protected static void run(AnalyzeTextOptions options, String inputDirname) throws Exception {
        options.setWikipediaDir(inputDirname);

        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
        AnalyzeTextWorkflow.createWorkflow(env, options);
        env.execute();
	}

}
