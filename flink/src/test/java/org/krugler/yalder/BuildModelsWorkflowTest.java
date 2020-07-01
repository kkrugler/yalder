package org.krugler.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.junit.Test;
import org.krugler.yalder.hash.HashLanguageDetector;
import org.krugler.yalder.text.TextLanguageDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildModelsWorkflowTest extends BaseWorkflowTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildModelsWorkflowTest.class);

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
        testEuroparl(3, 4, 0.95f);
        
        // Try a bunch of different values.
//        for (int minNGramLength = 1; minNGramLength <= 4; minNGramLength++) {
//            for (int maxNGramLength = minNGramLength; maxNGramLength <= 4; maxNGramLength++) {
//                for (float targetNGramPercentage = 0.60f; targetNGramPercentage <= 1.0f; targetNGramPercentage += 0.099f) {
//                    testEuroparl(minNGramLength, maxNGramLength, targetNGramPercentage);
//                }
//            }
//        }
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

    @Test
    public void testLeipzig() throws Exception {
        File workingDir = getTestDir("testLeipzig");

        // Create the ngram count data.
        AnalyzeTextOptions analyzeOptions = new AnalyzeTextOptions();
        analyzeOptions.setWorkingDir(workingDir.getAbsolutePath());
        analyzeOptions.setLeipzigDir("/Users/kenkrugler/svn/opennlp-corpus/leipzig/data/");
        analyzeOptions.setSampleRate(1.00f);
        analyzeOptions.setMinNGramCount(2);
        analyzeOptions.setMinNGramLength(4);
        analyzeOptions.setMaxNGramLength(4);
        analyzeOptions.setCreateGui(true);
        AnalyzeTextWorkflowTest.run(analyzeOptions, null);

        // Generate in-memory map of language to ngram counts.
        BuildModelsOptions options = new BuildModelsOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());
        
        ExecutionEnvironment env1 = ExecutionEnvironment.getExecutionEnvironment();
        List<Tuple2<String, Integer>> langCounts = BuildModelsWorkflow.getCounts(env1, options);

        // Finally do the calculate of the "best" ngrams for each language.
        options.setCreateGui(true);
        options.setTargetNGramPercentage(0.90f);
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
                        3, 
                        4, 
                        0.95f,
                        numMisses,
                        100.0 * (double)numMisses/(double)(numMisses + numHits));
    }

    @Test
    public void testLeipzigPostAnalyze() throws Exception {
        File workingDir = getTestDir("testLeipzig", false);

        // Generate in-memory map of language to ngram counts.
        BuildModelsOptions options = new BuildModelsOptions();
        options.setWorkingDir(workingDir.getAbsolutePath());
        
        ExecutionEnvironment env1 = ExecutionEnvironment.getExecutionEnvironment();
        List<Tuple2<String, Integer>> langCounts = BuildModelsWorkflow.getCounts(env1, options);

        // Do the calculate of the "best" ngrams for each language.
        options.setCreateGui(true);
        options.setTargetNGramPercentage(0.80f);
        ExecutionEnvironment env2 = ExecutionEnvironment.getExecutionEnvironment();
        BuildModelsWorkflow.createWorkflow(env2, options, langCounts);
        env2.execute();

        // Now open all the models, and try to detect some text.
        File modelDir = new File(options.getWorkingSubdir(WorkingDir.MODELS_DIRNAME));
        String[] targetLanguages = new String[] {
                        "bg",
                        "cs",
                        "da",
                        "de",
                        "el",
                        "en",
                        "es",
                        "et",
                        "fi",
                        "fr",
                        "hu",
                        "it",
                        "lt",
                        "lv",
                        "nl",
                        "pl",
                        "pt",
                        "ro",
                        "sk",
                        "sl",
                        "sv"
        };
        
        List<BaseLanguageModel> models = new ArrayList<>();
        for (String lang : targetLanguages) {
            LanguageLocale ll = LanguageLocale.fromString(lang);
            String modelFilename = String.format("yalder_model_%s.txt", ll.getISO3LetterName());
            File modelFile = new File(modelDir, modelFilename);
            InputStreamReader isr = new InputStreamReader(new FileInputStream(modelFile), StandardCharsets.UTF_8);
            LOGGER.info("Loading model for {}", ll.getName());
            BaseLanguageModel model = ModelLoader.loadTextModel(ll, isr);
            // model.prune(60);
            models.add(model);
            isr.close();
        }

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
                        3, 
                        4, 
                        0.95f,
                        numMisses,
                        100.0 * (double)numMisses/(double)(numMisses + numHits));
    }
}
