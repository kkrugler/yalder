package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.mahout.math.Vector;
import org.junit.Test;

import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.IntCounter;
import com.scaleunlimited.yalder.LanguageDetector;
import com.scaleunlimited.yalder.LanguageModel;
import com.scaleunlimited.yalder.ModelBuilder;

public class LanguageDetectorTest {

    @Test
    public void testEuroParl() throws Exception {
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder();

        List<String> testLines = new ArrayList<String>();
        Random rand = new Random(1L);
        
        for (String line : lines) {
            // See if we want to hold it out.
            if (rand.nextInt(10) < 5) {
                testLines.add(line);
                continue;
            }
            
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            builder.addTrainingDoc(language, text);
        }

        Collection<LanguageModel> models = builder.makeModels();
        
        // Now try classifying the held-out text using the models.
        LanguageDetector detector = new LanguageDetector(models);
        
        int totalMisses = 0;
        IntCounter missesPerLanguage = new IntCounter();
        IntCounter hitsPerLanguage = new IntCounter();

        for (String line : testLines) {
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            List<DetectionResult> sortedResults = new ArrayList<DetectionResult>(detector.detect(text));
            DetectionResult bestResult = sortedResults.get(0);
            String bestLanguage = bestResult.getLanguage();
            if (bestLanguage.equals(language)) {
                hitsPerLanguage.increment(language);
                // TODO what am I trying to record here???
                if ((bestResult.getConfidence() >= 0.4) && detector.hasSpecificModel(language, bestLanguage)) {
                    System.out.println(String.format("We would do extra ", text.length(), language, bestLanguage, bestResult.getScore(), bestResult.getConfidence()));
                }
            } else {
                missesPerLanguage.increment(language);
                totalMisses += 1;

                System.out.println(String.format("Best result for %d chars in '%s' was '%s' with score %f and confidence %f", text.length(), language, bestLanguage, bestResult.getScore(), bestResult.getConfidence()));
                DetectionResult nextBestResult = sortedResults.get(1);
                if (nextBestResult.getLanguage().equals(language)) {
                    System.out.println(String.format("Second best result was a match with score %f", nextBestResult.getScore()));
                } else {
                    System.out.println(String.format("Second best result was '%s' with score %f", nextBestResult.getLanguage(), nextBestResult.getScore()));
                }
            }
        }
        
        for (LanguageModel model : models) {
            String language = model.getLanguage();
            int misses = missesPerLanguage.get(language);
            int hits = hitsPerLanguage.get(language);
            
            System.out.println(String.format("Miss ratio for '%s' = %.2f%%", language, 100.0 * (double)misses/(double)(misses + hits)));
        }

        System.out.println(String.format("Total miss ratio = %.2f%%", 100.0 * (double)totalMisses/(double)testLines.size()));
    }

    @Test
    public void testPerformance() throws Exception {
        
        FileInputStream fis = new FileInputStream("src/test/resources/europarl.test");
        List<String> lines = IOUtils.readLines(fis, "UTF-8");

        ModelBuilder builder = new ModelBuilder();

        // Skip languages that Mike McCandless didn't try because Tika didn't support them:
        // Bulgarian (bg), Czech (cs), Lithuanian (lt) and Latvian (lv)
        Set<String> skippedLanguages = new HashSet<String>();
        skippedLanguages.add("bg");
        skippedLanguages.add("cs");
        skippedLanguages.add("lt");
        skippedLanguages.add("lv");
        
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            if (skippedLanguages.contains(language)) {
                continue;
            }
            
            builder.addTrainingDoc(language, text);
        }

        Collection<LanguageModel> models = builder.makeModels();
        LanguageDetector detector = new LanguageDetector(models);
        
        // Do 10 runs, and take the fastest time.
        long bestDuration = Long.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            for (String line : lines) {
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                if (skippedLanguages.contains(language)) {
                    continue;
                }

                String text = pieces[1];
                detector.detect(text);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println(String.format("Run #%d duration = %dms", i + 1, duration));
            bestDuration = Math.min(bestDuration, duration);
        }
        
        System.out.println(String.format("Best duration = %dms", bestDuration));
    }

}
