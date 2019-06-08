package org.krugler.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.junit.Test;
import org.krugler.yalder.hash.HashLanguageDetector;
import org.krugler.yalder.text.TextLanguageDetector;

public class BuildModelsWorkflowTest extends BaseWorkflowTest {

    @Override
    public File getClassDir() {
        return new File("target/test/BuildModelsWorkflowTest");
    }

    @Test
    public void testWikipedia() throws Exception {
        File workingDir = getTestDir("testWikipedia");

        // Create the ngram count data.
        AnalyzeTextOptions analyzeOptions = new AnalyzeTextOptions();
        analyzeOptions.setWorkingDir(workingDir.getAbsolutePath());

        String inputDirname = System.getProperty("yalder.inputdir");
        if (inputDirname == null) {
            AnalyzeTextWorkflowTest.run(analyzeOptions);
        } else {
            AnalyzeTextWorkflowTest.run(analyzeOptions, inputDirname);
        }

        // Generate in-memory map of language to ngram counts.
        BuildModelsOptions options = new BuildModelsOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());

        ExecutionEnvironment env1 = ExecutionEnvironment.getExecutionEnvironment();
        List<Tuple2<String, Integer>> langCounts = BuildModelsWorkflow.getCounts(env1, options);

        // Finally do the calculate of the "best" ngrams for each language.
        ExecutionEnvironment env2 = ExecutionEnvironment.getExecutionEnvironment();
        BuildModelsWorkflow.createWorkflow(env2, options, langCounts);
        env2.execute();

        // TODO analyze results
    }

    @Test
    public void testEuroparl() throws Exception {
        // Try a bunch of different values.
        for (int minNGramLength = 1; minNGramLength <= 4; minNGramLength++) {
            for (int maxNGramLength = minNGramLength; maxNGramLength <= 4; maxNGramLength++) {
                for (float targetNGramPercentage = 0.60f; targetNGramPercentage <= 1.0f; targetNGramPercentage += 0.099f) {
                    testEuroparl(minNGramLength, maxNGramLength, targetNGramPercentage);
                }
            }
        }
    }

    private void testEuroparl(int minNGramLength, int maxNGramLength, float targetNGramPercentage) throws Exception {
        File workingDir = getTestDir("testEuroparl");

        // Create the ngram count data.
        AnalyzeTextOptions analyzeOptions = new AnalyzeTextOptions();
        analyzeOptions.setWorkingDir(workingDir.getAbsolutePath());
        analyzeOptions.setTaggedFile("src/test/resources/europarl.test");
        analyzeOptions.setSampleRate(0.80f);
        analyzeOptions.setMinNGramLength(minNGramLength);
        analyzeOptions.setMaxNGramLength(maxNGramLength);
        AnalyzeTextWorkflowTest.run(analyzeOptions, null);

        // Generate in-memory map of language to ngram counts.
        BuildModelsOptions options = new BuildModelsOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());
        
        ExecutionEnvironment env1 = ExecutionEnvironment.getExecutionEnvironment();
        List<Tuple2<String, Integer>> langCounts = BuildModelsWorkflow.getCounts(env1, options);

        // Finally do the calculate of the "best" ngrams for each language.
        options.setTargetNGramPercentage(targetNGramPercentage);
        ExecutionEnvironment env2 = ExecutionEnvironment.getExecutionEnvironment();
        BuildModelsWorkflow.createWorkflow(env2, options, langCounts);
        env2.execute();

        // Now open all the models, and try to detect some text.
        File modelDir = new File(options.getWorkingSubdir(WorkingDir.MODELS_DIRNAME));
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelDir, false);
        assertEquals("There should be 21 models", 21, models.size());
        
        TextLanguageDetector detector = new TextLanguageDetector(models);
        
        FileInputStream fis = new FileInputStream("src/test/resources/europarl.test");
        List<String> lines = IOUtils.readLines(fis, "UTF-8");

        int numHits = 0;
        int numMisses = 0;

        for (String line : lines) {
            String[] pieces = line.split("\t", 2);
            LanguageLocale ll = LanguageLocale.fromString(pieces[0]);
            String text = pieces[1];
            
            detector.reset();
            detector.addText(text);
            Collection<DetectionResult> result = detector.detect();
            if (result.size() > 0 && result.iterator().next().getLanguage().weaklyEqual(ll)) {
                numHits += 1;
            } else {
                numMisses += 1;
                if (result.size() == 0) {
                    // System.out.format("Error, no identification for \"%s\"\n", text);
                } else {
                    // LanguageLocale resultingLL = result.iterator().next().getLanguage();
                    // System.out.format("Error, identified as '%s', was '%s' for \"%s\"\n", resultingLL.getISO3LetterName(), ll.getISO3LetterName(), text);
                }
            }
        }
        
        System.out.format("%d,%d,%f = %d wrong (%f%%)\n", 
                        minNGramLength, 
                        maxNGramLength, 
                        targetNGramPercentage,
                        numMisses,
                        100.0 * (double)numMisses/(double)(numMisses + numHits));
        
        if (false) {
            final char[] confusedPolAsEng = "Regiony te to belgijski region Limburg, holenderski region Limburg i region Aachen.".toCharArray();
            StringBuilder details = new StringBuilder();

            detector.reset();
            detector.addText(confusedPolAsEng, 0, confusedPolAsEng.length, details, null);
            System.out.println(details);
        }
    }

}
