package org.krugler.yalder.hash;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.EuroParlUtils;
import org.krugler.yalder.IntCounter;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.ModelBuilder;
import org.krugler.yalder.OtherDetectorsTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguageDetectorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageDetectorTest.class);

    @Test
    public void testHumanRightsDeclaration() throws Exception {
        System.setProperty("logging.root.level", "INFO");
        // LOGGER.setLevel(Level.INFO);

        Map<String, String> knownText = new HashMap<String, String>();
        knownText.put("da", "Alle mennesker er født frie og lige i værdighed og rettigheder. De er udstyret med fornuft og samvittighed, og de bør handle mod hverandre i en broderskabets ånd.");
        knownText.put("de", "Alle Menschen sind frei und gleich an Würde und Rechten geboren. Sie sind mit Vernunft und Gewissen begabt und sollen einander im Geist der Brüderlichkeit begegnen.");
        knownText.put("en", "All human beings are born free and equal in dignity and rights. They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.");
        knownText.put("nl", "Alle mensen worden vrij en gelijk in waardigheid en rechten geboren. Zij zijn begiftigd met verstand en geweten, en behoren zich jegens elkander in een geest van broederschap te gedragen.");
        knownText.put("sv", "Alla människor är födda fria och lika i värdighet och rättigheter. De är utrustade med förnuft och samvete och bör handla gentemot varandra i en anda av broderskap.");

        Map<String, String> unknownText = new HashMap<String, String>();
        unknownText.put("ty", "E fanauhia te tā'āto'ara'a o te ta'atātupu ma te ti'amā e te ti'amanara'a 'aifaito. Ua 'ī te mana'o pa'ari e i te manava e ma te 'a'au taea'e 'oia ta ratou ha'a i rotopū ia ratou iho, e ti'a ai;");
        
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder().setBinaryMode(true);
        
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];
            builder.addTrainingDoc(language, text);
        }
        
        HashLanguageDetector detector = new HashLanguageDetector(builder.makeModels());
        
        for (String languageTag : knownText.keySet()) {
            Collection<DetectionResult> results = detector.detect(knownText.get(languageTag));
            LanguageLocale targetLanguage = LanguageLocale.fromString(languageTag);
            assertTrue("No results for language " + targetLanguage, results.size() > 0);
            DetectionResult result = results.iterator().next();
            assertTrue(result.getLanguage().weaklyEqual(targetLanguage));
            LOGGER.debug(String.format("'%s': %s", targetLanguage, result.toString()));
        }
        
        // TODO verify that we have low confidence in whatever results we get back here.
        for (String languageTag : unknownText.keySet()) {
            Collection<DetectionResult> results = detector.detect(unknownText.get(languageTag));
            LanguageLocale targetLanguage = LanguageLocale.fromString(languageTag);
            for (DetectionResult result : results) {
                LOGGER.info(String.format("'%s': %s", targetLanguage, result.toString()));                
            }
        }
    }

    // TODO reenable this test when we're loading binary models
    // @Test
    public void testUniversalDeclarationOfHumanRights() throws Exception {
        System.setProperty("logging.root.level", "INFO");
        // Logger.getRootLogger().setLevel(Level.INFO);

        // TODO move modelbuilder to tools?
        // TODO load models
        // First build all models
        ModelBuilder mb = new ModelBuilder();

        // TODO add support for remapping some language names, e.g.
        // zh-min-nan => nan (Min Nan dialect of Chinese)

        // TODO put this into a WikipediaUtils class, use it everywhere
        Set<String> excludedLanguages = new HashSet<String>();

        // Simplified English looks like English
        excludedLanguages.add("simple");

        // Belarusian using classic orthography
        excludedLanguages.add("be-x-old");

        // https://en.wikipedia.org/wiki/Sranan_Tongo (looks like Dutch)
        excludedLanguages.add("srn");

        // Chavacano or Chabacano [tʃaβaˈkano] is a Spanish-based creole language spoken in the Philippines
        excludedLanguages.add("cbk-zam");

        // https://en.wikipedia.org/wiki/Asturian_language (looks like Spanish)
        excludedLanguages.add("ast");

        // https://en.wikipedia.org/wiki/Galician_language (looks like Spanish)
        excludedLanguages.add("gl");

        // Scottish looks like English
        excludedLanguages.add("sco");

        // Deprecated code for obsolete "Serbo-Croatian"
        // http://www.personal.psu.edu/ejp10/blogs/gotunicode/2010/08/the-language-codes-of-the-form.html
        excludedLanguages.add("sh");

        FileInputStream fis = new FileInputStream("src/test/resources/wikipedia.txt");
        List<String> lines = IOUtils.readLines(fis, "UTF-8");
        fis.close();

        for (String line : lines) {
            String[] parts = line.split("\t", 2);
            String language = parts[0];
            if (excludedLanguages.contains(language)) {
                continue;
            }

            String text = parts[1];
            mb.addTrainingDoc(language, text);
        }
        
        System.out.println("Building training models...");
        Collection<BaseLanguageModel> models = mb.makeModels();

        HashLanguageDetector detector = new HashLanguageDetector(models);

        fis = new FileInputStream("src/test/resources/udhr.txt");
        lines = IOUtils.readLines(fis, "UTF-8");
        fis.close();

        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\t", 3);
            String languageCode = parts[0];
            if (languageCode.equals("   ") || languageCode.equals("???")) {
                continue;
            }
            
            String text = parts[2];

            LanguageLocale language = LanguageLocale.fromString(languageCode);
            if (!detector.supportsLanguage(language)) {
                System.out.println(String.format("Skipping text from '%s', not supported", language));
                continue;
            }
            
            Collection<DetectionResult> result = detector.detect(text);
            if (result.isEmpty()) {
                System.out.println(String.format("Detected '%s' as nothing", language));
            } else {
                DetectionResult dr = result.iterator().next();
                if (dr.getLanguage().weaklyEqual(language)) {
                    System.out.println(String.format("Correctly detected '%s' as '%s'", language, dr.getLanguage()));
                } else {
                    System.out.println(String.format("Incorrectly detected '%s' as '%s'", language, dr.getLanguage()));
                }
            }
        }
    }
    
    @Test
    public void testLanguageSpread() throws Exception {
        List<String> lines = EuroParlUtils.readLines();

        ModelBuilder builder = new ModelBuilder();

        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            builder.addTrainingDoc(language, text);
        }

        Collection<BaseLanguageModel> models = builder.makeModels();
        Map<LanguageLocale, HashLanguageModel> modelsAsMap = new HashMap<LanguageLocale, HashLanguageModel>();
        for (BaseLanguageModel model : models) {
            assertNull(modelsAsMap.put(model.getLanguage(), (HashLanguageModel)model));
        }
        
        for (LanguageLocale language : modelsAsMap.keySet()) {
            IntIntMap langNGramCounts = modelsAsMap.get(language).getNGramCounts();
            
            for (LanguageLocale otherLanguage : modelsAsMap.keySet()) {
                if (language.equals(otherLanguage)) {
                    continue;
                }
                
                IntIntMap otherLangNGramCounts = modelsAsMap.get(otherLanguage).getNGramCounts();
                
                // Calculate the overlap of ngrams
                Set<Integer> allNGrams = new HashSet<Integer>();
                for (int ngramHash : langNGramCounts.keySet()) {
                    allNGrams.add(ngramHash);
                }
                for (int ngramHash : otherLangNGramCounts.keySet()) {
                    allNGrams.add(ngramHash);
                }
                double totalNGramCount = allNGrams.size();

                int intersectionCount = 0;
                
                double langProb = 0.50;
                double otherLangProb = 0.50;
                for (int ngram : langNGramCounts.keySet()) {
                    int langCount = langNGramCounts.getValue(ngram);
                    int otherLangCount = otherLangNGramCounts.contains(ngram) ? otherLangNGramCounts.getValue(ngram) : 0;
                    if (otherLangCount != 0) {
                        intersectionCount += 1;
                    }
                    
                    double totalCount = langCount + otherLangCount;
                    
                    double langNGramProb = langCount == 0 ? HashLanguageDetector.DEFAULT_ALPHA : langCount/totalCount;
                    double otherLangNGramProb = otherLangCount == 0 ? HashLanguageDetector.DEFAULT_ALPHA : otherLangCount/totalCount;
                    
                    int scaledTotalCount = (int)Math.round(totalCount / 1000);
                    for (int i = 0; i < scaledTotalCount; i++) {
                        langProb *= langNGramProb;
                        otherLangProb *= otherLangNGramProb;
                    
                        double probScalar = 1.0/(langProb + otherLangProb);
                        langProb *= probScalar;
                        otherLangProb *= probScalar;
                    }
                }
                
                System.out.println(String.format("'%s' (%d) vs '%s' (%d) = %.2f%% overlap", language, langNGramCounts.keySet().length, otherLanguage, otherLangNGramCounts.keySet().length, 100.0 * intersectionCount/totalNGramCount));
                if (otherLangProb > 0.0) {
                    System.out.println(String.format("'%s' (%f) vs '%s' (%f)", language, langProb, otherLanguage, otherLangProb));
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
        return testLanguages(new Random(1L), targetLanguages);
    }
    
    private double testLanguages(Random rand, Set<String> targetLanguages) throws Exception {
        List<String> testLines = new ArrayList<String>();
        Collection<BaseLanguageModel> models = makeModelsAndTestData(testLines, rand, targetLanguages);
        
        // Now try classifying the held-out text using the models.
        // Note that the testLines will only have text for the target languages.
        
        HashLanguageDetector detector = new HashLanguageDetector(models);
        
        int totalMisses = 0;
        IntCounter hitsPerLanguage = new IntCounter();

        Map<LanguageLocale, IntCounter> missesPerLanguage = new HashMap<LanguageLocale, IntCounter>();
        for (String line : testLines) {
            String[] pieces = line.split("\t", 2);
            String languageAsStr = pieces[0];
            String text = pieces[1];
            LanguageLocale language = LanguageLocale.fromString(languageAsStr);
            IntCounter missCounter = missesPerLanguage.get(language);
            if (missCounter == null) {
                missCounter = new IntCounter();
                missesPerLanguage.put(language, missCounter);
            }
            
            List<DetectionResult> sortedResults = new ArrayList<DetectionResult>(detector.detect(text));
            DetectionResult bestResult = sortedResults.isEmpty() ? new DetectionResult(LanguageLocale.fromString("zxx"), 0.0) : sortedResults.get(0);
            LanguageLocale bestLanguage = bestResult.getLanguage();
            if (bestLanguage.equals(language)) {
                hitsPerLanguage.increment(language.toString());
            } else {
                missCounter.increment(bestLanguage.toString());
                totalMisses += 1;

                // System.out.println(String.format("Best result for %d chars in '%s' was '%s' with score %f and confidence %f", text.length(), language, bestLanguage, bestResult.getScore(), bestResult.getConfidence()));
            }
        }
        
        StringBuilder debugMsg = new StringBuilder('\n');
        for (BaseLanguageModel model : models) {
            LanguageLocale language = model.getLanguage();
            IntCounter missCounter = missesPerLanguage.get(language);
            if (missCounter == null) {
                missCounter = new IntCounter();
            }
            
            int misses = missCounter.sum();
            int hits = hitsPerLanguage.get(language.toString());
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

        LOGGER.debug(debugMsg.toString());
        
        double missRatio = 100.0 * (double)totalMisses/(double)testLines.size();
        LOGGER.debug(String.format("\nTotal miss ratio = %.2f%%", missRatio));
        return missRatio;
    }

    @Test
    public void testPerformance() throws Exception {
        Collection<BaseLanguageModel> models = loadModels(OtherDetectorsTest.TARGET_LANGUAGES_FOR_YALDER);
        
        List<String> lines = EuroParlUtils.readLines();
        HashLanguageDetector detector = new HashLanguageDetector(models);
        
        // Do 10 runs, and take the fastest time.
        long bestDuration = Long.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            int numHits = 0;
            int numMisses = 0;

            long startTime = System.currentTimeMillis();
            for (String line : lines) {
                String[] pieces = line.split("\t", 2);
                LanguageLocale ll = LanguageLocale.fromString(pieces[0]);
                String text = pieces[1];
                Collection<DetectionResult> result = detector.detect(text);
                if (result.size() > 0 && result.iterator().next().getLanguage().weaklyEqual(ll)) {
                    numHits += 1;
                } else {
                    numMisses += 1;
                }

            }
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println(String.format("Run #%d duration = %dms", i + 1, duration));
            System.out.println(String.format("Run #%d error rate = %f%%", i + 1, 100.0 * (double)numMisses/(double)(numMisses + numHits)));
            bestDuration = Math.min(bestDuration, duration);
        }
        
        System.out.println(String.format("Best duration = %dms", bestDuration));
    }

    private Collection<BaseLanguageModel> loadModels(String[] targetLanguages) throws IOException {
        Collection<BaseLanguageModel> result = new ArrayList<>();
        for (String languageTag : targetLanguages) {
            LanguageLocale ll = LanguageLocale.fromString(languageTag);
            
            HashLanguageModel model = new HashLanguageModel();
            String modelName = String.format("/org/krugler/yalder/models/core/yalder_model_%s.bin", ll.getName());
            InputStream is = LanguageDetectorTest.class.getResourceAsStream(modelName);
            if (is == null) {
                LOGGER.debug(String.format("Loading non-core model '%s'", ll.getName()));
                modelName = String.format("/org/krugler/yalder/models/extras/yalder_model_%s.bin", ll.getName());
                is = LanguageDetectorTest.class.getResourceAsStream(modelName);
            }
            
            DataInputStream dis = new DataInputStream(is);
            model.readAsBinary(dis);
            dis.close();
            
            result.add(model);
        }
        
        return result;
    }

    private Collection<BaseLanguageModel> makeModelsAndTestData(List<String> testLines, Random rand) throws Exception {
        return makeModelsAndTestData(testLines, rand, null);
    }
    
    private Collection<BaseLanguageModel> makeModelsAndTestData(List<String> testLines, Random rand, Set<String> targetLanguages) throws Exception {
        return makeModelsAndTestData(testLines, rand, targetLanguages);
    }
    
    private Collection<BaseLanguageModel> makeModelsAndTestData(List<String> testLines, Random rand, Set<String> targetLanguages, int ngramsPerLanguage, double minNGramProbability, double probabilityScorePower) throws Exception {
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

        return builder.makeModels();
    }

}
