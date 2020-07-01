package org.krugler.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.junit.Test;

public class AnalyzeLeipzigWorkflowTest extends BaseWorkflowTest {

    @Override
    public File getClassDir() {
        return new File("target/test/AnalyzeLeipzigWorkflowTest");
    }

    @Test
    public void test() throws Exception {
        File workingDir = getTestDir("test");
        
        File leipzipDir = new File("src/test/resources/leipzig/");
        
        AnalyzeLeipzigOptions options = new AnalyzeLeipzigOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());
        options.setLeipzigDir(leipzipDir.getAbsolutePath());
        options.setMinBlockCount(8);
        
        ExecutionEnvironment env = getEnvironment(workingDir);
        AnalyzeLeipzigWorkflow.createWorkflow(env, options);
        env.execute();
        
        File outputFile = new File(workingDir, WorkingDir.SINGLE_LANGUAGE_BLOCKS_FILENAME);
        assertTrue(outputFile.exists());
        
        FileInputStream fis = new FileInputStream(outputFile);
        Set<String> results = new HashSet<>(IOUtils.readLines(fis, StandardCharsets.UTF_8));
        fis.close();

        assertEquals(2, results.size());
        assertTrue(results.contains("VARIATION_SELECTORS,8,eng"));
        assertTrue(results.contains("LATIN_1_SUPPLEMENT,51,afr"));
    }

}
