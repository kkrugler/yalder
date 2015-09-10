package com.scaleunlimited.yalder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

public class LanguageDetectorTest {
    private static final Logger LOGGER = Logger.getLogger(LanguageDetectorTest.class);

    @Test
    public void testHumanRightsDeclaration() throws Exception {
        System.setProperty("logging.root.level", "INFO");
        Logger.getRootLogger().setLevel(Level.INFO);

        Map<String, String> knownText = new HashMap<String, String>();
        knownText.put("da", "Alle mennesker er født frie og lige i værdighed og rettigheder. De er udstyret med fornuft og samvittighed, og de bør handle mod hverandre i en broderskabets ånd.");
        knownText.put("de", "Alle Menschen sind frei und gleich an Würde und Rechten geboren. Sie sind mit Vernunft und Gewissen begabt und sollen einander im Geist der Brüderlichkeit begegnen.");
        knownText.put("en", "All human beings are born free and equal in dignity and rights. They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.");
        knownText.put("nl", "Alle mensen worden vrij en gelijk in waardigheid en rechten geboren. Zij zijn begiftigd met verstand en geweten, en behoren zich jegens elkander in een geest van broederschap te gedragen.");
        knownText.put("sv", "Alla människor är födda fria och lika i värdighet och rättigheter. De är utrustade med förnuft och samvete och bör handla gentemot varandra i en anda av broderskap.");

        Map<String, String> unknownText = new HashMap<String, String>();
        unknownText.put("ty", "E fanauhia te tā'āto'ara'a o te ta'atātupu ma te ti'amā e te ti'amanara'a 'aifaito. Ua 'ī te mana'o pa'ari e i te manava e ma te 'a'au taea'e 'oia ta ratou ha'a i rotopū ia ratou iho, e ti'a ai;");
        
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder()
            .setNGramsPerLanguage(1000);
        
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];
            builder.addTrainingDoc(language, text);
        }
        
        LanguageDetector detector = new LanguageDetector(builder.makeModels());
        
        for (String language : knownText.keySet()) {
            Collection<DetectionResult> results = detector.detect(knownText.get(language));
            assertTrue(results.size() > 0);
            DetectionResult result = results.iterator().next();
            assertEquals(language, result.getLanguage());
            LOGGER.debug(String.format("'%s': %s", language, result.toString()));
        }
        
        for (String language : unknownText.keySet()) {
            Collection<DetectionResult> results = detector.detect(unknownText.get(language));
            for (DetectionResult result : results) {
                LOGGER.info(String.format("'%s': %s", language, result.toString()));                
            }
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
        // System.setProperty("logging.root.level", "INFO");
        System.setProperty("logging.root.level", "DEBUG");
        Logger.getRootLogger().setLevel(Level.DEBUG);
        testLanguages(new Random(1L), null, 1000, 0.20, 1.0);
    }
    
    @Test
    public void testLanguageSpread() throws Exception {
        List<String> lines = EuroParlUtils.readLines();

        final int ngramsPerLanguage = 1000;
        final double minNGramProbability = .50;
        final double probabilityScorePower = 1.0;
        
        ModelBuilder builder = new ModelBuilder()
            .setNGramsPerLanguage(ngramsPerLanguage)
            .setMinNGramProbability(minNGramProbability)
            .setProbabilityScorePower(probabilityScorePower);

        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            builder.addTrainingDoc(language, text);
        }

        Collection<LanguageModel> models = builder.makeModels();
        Map<String, LanguageModel> modelsAsMap = new HashMap<String, LanguageModel>();
        for (LanguageModel model : models) {
            assertNull(modelsAsMap.put(model.getLanguage(), model));
        }
        
        for (String language : modelsAsMap.keySet()) {
            Map<CharSequence, Integer> langNGramCounts = modelsAsMap.get(language).getNGramCounts();
            
            for (String otherLanguage : modelsAsMap.keySet()) {
                if (language.equals(otherLanguage)) {
                    continue;
                }
                
                Map<CharSequence, Integer> otherLangNGramCounts = modelsAsMap.get(otherLanguage).getNGramCounts();
                
                // Calculate the overlap of ngrams
                Set<CharSequence> allNGrams = new HashSet<CharSequence>(langNGramCounts.keySet());
                allNGrams.addAll(otherLangNGramCounts.keySet());
                double totalNGramCount = allNGrams.size();

                int intersectionCount = 0;
                
                double langProb = 0.50;
                double otherLangProb = 0.50;
                for (CharSequence ngram : langNGramCounts.keySet()) {
                    int langCount = langNGramCounts.get(ngram);
                    int otherLangCount = otherLangNGramCounts.containsKey(ngram) ? otherLangNGramCounts.get(ngram) : 0;
                    if (otherLangCount != 0) {
                        intersectionCount += 1;
                    }
                    
                    double totalCount = langCount + otherLangCount;
                    
                    double langNGramProb = langCount == 0 ? LanguageDetector.DEFAULT_ALPHA : langCount/totalCount;
                    double otherLangNGramProb = otherLangCount == 0 ? LanguageDetector.DEFAULT_ALPHA : otherLangCount/totalCount;
                    
                    int scaledTotalCount = (int)Math.round(totalCount / 1000);
                    for (int i = 0; i < scaledTotalCount; i++) {
                        langProb *= langNGramProb;
                        otherLangProb *= otherLangNGramProb;
                    
                        double probScalar = 1.0/(langProb + otherLangProb);
                        langProb *= probScalar;
                        otherLangProb *= probScalar;
                    }
                }
                
                System.out.println(String.format("'%s' (%d) vs '%s' (%d) = %.2f%% overlap", language, langNGramCounts.keySet().size(), otherLanguage, otherLangNGramCounts.keySet().size(), 100.0 * intersectionCount/totalNGramCount));
                if (otherLangProb > 0.0) {
                    System.out.println(String.format("'%s' (%f) vs '%s' (%f)", language, langProb, otherLanguage, otherLangProb));
                }
            }
        }

    }
    
    
    @Test
    // TODO remove this test.
    public void optimizeBuilderSettings() throws Exception {
        System.setProperty("logging.root.level", "INFO");
        Logger.getRootLogger().setLevel(Level.INFO);
        
        final int numTrials = 1;
        for (int ngramsPerLanguage = 1500; ngramsPerLanguage <= 5000; ngramsPerLanguage += 500) {
            for (double minNGramProbability = 0.30; minNGramProbability <= 0.51; minNGramProbability += .02) {
                for (double probabilityScorePower = 1.0; probabilityScorePower <= 1.05; probabilityScorePower += 0.1) {
                    Random rand = new Random(1L);
                    double totalMissRatio = 0.0;
                    for (int trial = 0; trial < numTrials; trial++) {
                        totalMissRatio += testLanguages(new Random(1L), null, ngramsPerLanguage, minNGramProbability, probabilityScorePower);
                    }

                    System.out.println(String.format("%d ngrams, min prob of %f, score power of %f == %f miss ratio", ngramsPerLanguage, minNGramProbability, probabilityScorePower, totalMissRatio / numTrials));
                }
            }
        }
    }
    
    /**
     * @param targetLanguages Set of languages to test
     * @return the miss ratio (* 100, so it's a display percentage)
     * @throws Exception
     */
    
    private double testLanguages(Set<String> targetLanguages) throws Exception {
        return testLanguages(new Random(1L), targetLanguages, ModelBuilder.DEFAULT_NGRAMS_PER_LANGUAGE, ModelBuilder.DEFAULT_MIN_NGRAM_PROBABILITY, ModelBuilder.DEFAULT_PROBABILITY_SCORE_POWER);
    }
    
    private double testLanguages(Random rand, Set<String> targetLanguages, int ngramsPerLanguage, double minNGramProbability, double probabilityScorePower) throws Exception {
        List<String> testLines = new ArrayList<String>();
        Collection<LanguageModel> models = makeModelsAndTestData(testLines, rand, targetLanguages, ngramsPerLanguage, minNGramProbability, probabilityScorePower);
        
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
        
        StringBuilder debugMsg = new StringBuilder('\n');
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
            
            
            debugMsg.append(String.format("'%s'\t%.2f%%", language, 100.0 * (double)misses/(double)(misses + hits)));
            
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
                debugMsg.append(String.format("\t'%s'=% 2d%%", count.getKey(), Math.round(100.0 * (double)count.getValue()/(double)(misses))));
            }
            
            debugMsg.append('\n');
        }

        LOGGER.debug(debugMsg);
        
        double missRatio = 100.0 * (double)totalMisses/(double)testLines.size();
        LOGGER.debug(String.format("\nTotal miss ratio = %.2f%%", missRatio));
        return missRatio;
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

        Collection<LanguageModel> models = builder.makeModels();
        LanguageDetector detector = new LanguageDetector(models, builder.getMaxNGramLength());
        
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
        return makeModelsAndTestData(testLines, rand, targetLanguages, ModelBuilder.DEFAULT_NGRAMS_PER_LANGUAGE, ModelBuilder.DEFAULT_MIN_NGRAM_PROBABILITY, ModelBuilder.DEFAULT_PROBABILITY_SCORE_POWER);
    }
    
    private Collection<LanguageModel> makeModelsAndTestData(List<String> testLines, Random rand, Set<String> targetLanguages, int ngramsPerLanguage, double minNGramProbability, double probabilityScorePower) throws Exception {
        testLines.clear();
        
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder()
            .setNGramsPerLanguage(ngramsPerLanguage)
            .setMinNGramProbability(minNGramProbability)
            .setProbabilityScorePower(probabilityScorePower);

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

        return builder.makeModels();
    }

}
