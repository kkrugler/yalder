package com.scaleunlimited.yalder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Test;

public class LanguageDetectorTest {

    @Test
    public void testHumanRightsDeclaration() throws Exception {
        Map<String, String> textSamples = new HashMap<String, String>();
        textSamples.put("de", "Alle Menschen sind frei und gleich an Würde und Rechten geboren. Sie sind mit Vernunft und Gewissen begabt und sollen einander im Geist der Brüderlichkeit begegnen.");
        textSamples.put("en", "All human beings are born free and equal in dignity and rights. They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.");
        textSamples.put("sv", "Alla människor är födda fria och lika i värdighet och rättigheter. De är utrustade med förnuft och samvete och bör handla gentemot varandra i en anda av broderskap.");
        
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder();

        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];
            builder.addTrainingDoc(language, text);
        }
        
        final int ngramsPerLanguage = 3000;
        LanguageDetector detector = new LanguageDetector(builder.makeModels(ngramsPerLanguage));
        
        for (String language : textSamples.keySet()) {
            Collection<DetectionResult> results = detector.detect(textSamples.get(language));
            assertTrue(results.size() > 0);
            DetectionResult result = results.iterator().next();
            assertEquals(language, result.getLanguage());
            System.out.println(String.format("'%s': %s", language, result.toString()));
        }
    }

    @Test
    public void testEuroParlManySamples() throws Exception {
        List<String> testLines = new ArrayList<String>();
        
        SummaryStatistics stats = new SummaryStatistics();
        
        for (int i = 0; i < 100; i++) {
            Collection<LanguageModel> models = makeModelsAndTestData(testLines, new Random());
            LanguageDetector detector = new LanguageDetector(models);

            int totalMisses = 0;
            for (String line : testLines) {
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                
                String text = pieces[1];

                List<DetectionResult> sortedResults = new ArrayList<DetectionResult>(detector.detect(text));
                DetectionResult bestResult = sortedResults.get(0);
                String bestLanguage = bestResult.getLanguage();
                if (!bestLanguage.equals(language)) {
                    totalMisses += 1;
                }
            }
            
            double missPercentage = 100.0 * (double)totalMisses/(double)testLines.size();
            stats.addValue(missPercentage);
            System.out.println(String.format("Total miss ratio = %.2f%%", missPercentage));
        }
        
        System.out.println(String.format("Min = %.2f%%,  max =  %.2f%%, mean =  %.2f%%, std deviation = %f",
                                        stats.getMin(), stats.getMax(), stats.getMean(), stats.getStandardDeviation()));
    }
    
    @Test
    public void testAllLanguages() throws Exception {
        testLanguages(null);
    }
    
    private void testLanguages(Set<String> targetLanguages) throws Exception {
        List<String> testLines = new ArrayList<String>();
        Collection<LanguageModel> models = makeModelsAndTestData(testLines, new Random(1L), targetLanguages);
        
        // Now try classifying the held-out text using the models.
        // Note that the testLines will only have text for the target languages.
        
        LanguageDetector detector = new LanguageDetector(models);
        
        int totalMisses = 0;
        IntCounter hitsPerLanguage = new IntCounter();

        Map<String, IntCounter> missesPerLanguage = new HashMap<String, IntCounter>();
        for (String line : testLines) {
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            IntCounter missCounter = missesPerLanguage.get(language);
            if (missCounter == null) {
                missCounter = new IntCounter();
                missesPerLanguage.put(language, missCounter);
            }
            
            List<DetectionResult> sortedResults = new ArrayList<DetectionResult>(detector.detect(text));
            DetectionResult bestResult = sortedResults.isEmpty() ? new DetectionResult("unknown", 0.0) : sortedResults.get(0);
            String bestLanguage = bestResult.getLanguage();
            if (bestLanguage.equals(language)) {
                hitsPerLanguage.increment(language);
            } else {
                missCounter.increment(bestLanguage);
                totalMisses += 1;

                // System.out.println(String.format("Best result for %d chars in '%s' was '%s' with score %f and confidence %f", text.length(), language, bestLanguage, bestResult.getScore(), bestResult.getConfidence()));
            }
        }
        
        for (LanguageModel model : models) {
            String language = model.getLanguage();
            IntCounter missCounter = missesPerLanguage.get(language);
            if (missCounter == null) {
                missCounter = new IntCounter();
            }
            
            int misses = missCounter.sum();
            int hits = hitsPerLanguage.get(language);
            if (hits + misses == 0) {
                // No data for this model.
                continue;
            }
            
            System.out.print(String.format("'%s'\t%.2f%%", language, 100.0 * (double)misses/(double)(misses + hits)));
            
            List<Entry<String, Integer>> counts = new ArrayList<Entry<String, Integer>>(missCounter.entrySet());
            Collections.sort(counts, new Comparator<Entry<String, Integer>>() {

                @Override
                public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                    if (o1.getValue() > o2.getValue()) {
                        return -1;
                    } else if (o1.getValue() < o2.getValue()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
            
            for (Entry<String, Integer> count : counts) {
                System.out.print(String.format("\t'%s'=% 2d%%", count.getKey(), Math.round(100.0 * (double)count.getValue()/(double)(misses))));
            }
            
            System.out.println();
        }

        System.out.println(String.format("Total miss ratio = %.2f%%", 100.0 * (double)totalMisses/(double)testLines.size()));
    }

    @Test
    public void testPerformance() throws Exception {
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder();
        // builder.setCsSk(false);
        
        // Skip languages that Mike McCandless didn't try because Tika didn't support them:
        // Bulgarian (bg), Czech (cs), Lithuanian (lt) and Latvian (lv)
        Set<String> skippedLanguages = new HashSet<String>();
        // skippedLanguages.add("bg");
        // skippedLanguages.add("cs");
        // skippedLanguages.add("lt");
        // skippedLanguages.add("lv");
        
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

        Collection<LanguageModel> models = builder.makeModels(1000);
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

    private Collection<LanguageModel> makeModelsAndTestData(List<String> testLines, Random rand) throws Exception {
        return makeModelsAndTestData(testLines, rand, null);
    }
    
    private Collection<LanguageModel> makeModelsAndTestData(List<String> testLines, Random rand, Set<String> targetLanguages) throws Exception {
        testLines.clear();
        
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder();

        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            
            if ((targetLanguages != null) && !targetLanguages.contains(language)) {
                continue;
            }
            
            // See if we want to hold it out.
            if (rand.nextInt(10) < 2) {
                testLines.add(line);
                continue;
            }
            
            String text = pieces[1];

            builder.addTrainingDoc(language, text);
        }

        Map<String, Integer> ngramsPerLanguage = new HashMap<String, Integer>();
        /*
         * 'sl' 0.45%   'es'= 100%

'pl'    0.47%   'it'= 100%
'et'    0.53%   'es'= 100%
'it'    0.51%   'es'= 100%
'sl'    0.45%   'es'= 100%

'de'    0.95%   'da'= 50%   'en'= 50%
'pt'    1.10%   'en'= 50%   'es'= 50%
'lt'    1.44%   'sl'= 33%   'it'= 33%   'es'= 33%

'ro'    2.36%   'es'= 80%   'it'= 20%
'cs'    3.47%   'sl'= 71%   'es'= 29%

'sk'    5.83%   'sl'= 77%   'es'= 15%   'it'= 8%
'sv'    4.57%   'da'= 100%

         */
        
        int defaultCount = 1000;
        ngramsPerLanguage.put("nl", defaultCount);
        ngramsPerLanguage.put("es", defaultCount);
        ngramsPerLanguage.put("en", defaultCount);
        ngramsPerLanguage.put("bg", defaultCount);
        ngramsPerLanguage.put("fr", defaultCount);
        ngramsPerLanguage.put("fi", defaultCount);
        ngramsPerLanguage.put("el", defaultCount);
        ngramsPerLanguage.put("lv", defaultCount);
        ngramsPerLanguage.put("hu", defaultCount);
        ngramsPerLanguage.put("da", defaultCount);

        ngramsPerLanguage.put("et", defaultCount * 2);
        ngramsPerLanguage.put("sl", defaultCount * 2);
        ngramsPerLanguage.put("pl", defaultCount * 2);
        ngramsPerLanguage.put("it", defaultCount * 2);
        ngramsPerLanguage.put("de", defaultCount * 2);
        ngramsPerLanguage.put("pt", defaultCount * 2);
        ngramsPerLanguage.put("lt", defaultCount * 2);

        ngramsPerLanguage.put("ro", defaultCount * 3);
        ngramsPerLanguage.put("cs", defaultCount * 3);

        ngramsPerLanguage.put("sk", defaultCount * 4);
        ngramsPerLanguage.put("sv", defaultCount * 4);

        return builder.makeModels(3000);
    }

}
