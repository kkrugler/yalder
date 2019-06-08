package org.krugler.yalder.hash;

import static org.junit.Assert.*;

import java.io.DataInputStream;
import java.io.File;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.krugler.yalder.BaseLanguageDetector;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.CoreModels;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.EuroParlUtils;
import org.krugler.yalder.ExtraModels;
import org.krugler.yalder.IntCounter;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.ModelBuilder;
import org.krugler.yalder.ModelLoader;
import org.krugler.yalder.OtherDetectorsTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashLanguageDetectorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(HashLanguageDetectorTest.class);

    private HashLanguageDetector _detector;
    
    @Before
    public void setUp() throws IOException {
        File modelDir = new File("src/main/resources/org/krugler/yalder/models/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelDir, true);
        _detector = new HashLanguageDetector(models);
    }
    
    // TODO re-enable test
    @Ignore
    @Test
    public void testTikaShortSnippets() throws Exception {
        String chinese = "太初，道已經存在，道與上帝同在，道就是上帝。 2 太初，道就與上帝同在。 3 萬物都是藉著祂造的[a]，受造之物沒有一樣不是藉著祂造的。 4 祂裡面有生命，這生命是人類的光。 5 光照進黑暗裡，黑暗不能勝過[b]光。";
        _detector.reset();
        _detector.addText(chinese);
        Collection<DetectionResult> result = _detector.detect();
        Assert.assertTrue(result.size() > 0);
        DetectionResult dr = result.iterator().next();
        Assert.assertEquals(LanguageLocale.fromString("zh-CN"), dr.getLanguage());
    }
    
    @Test
    public void testApriorProbabilities() throws Exception {
        System.setProperty("logging.root.level", "TRACE");

        String tibetan = "འགྲོ་བ་མིའི་རིགས་རྒྱུད་ཡོངས་ལ་སྐྱེས་ཙམ་ཉིད་ནས་ཆེ་མཐོངས་དང༌། ཐོབ་ཐངགི་རང་དབང་འདྲ་མཉམ་དུ་ཡོད་ལ། ཁོང་ཚོར་རང་བྱུང་གི་བློ་རྩལ་དང་བསམ་ཚུལ་བཟང་པོ་འདོན་པའི་འོས་བབས་ཀྱང་ཡོད། དེ་བཞིན་ཕན་ཚུན་གཅིག་གིས་གཅིག་ལ་བུ་སྤུན་གྱི་འདུ་ཤེས་འཛིན་པའི་བྱ་སྤྱོད་ཀྱང་ལག་ལེན་བསྟར་དགོས་པ་ཡིན༎";
        String dzongkha = "འགྲོ་བ་མི་རིགས་ག་ར་དབང་ཆ་འདྲ་མཏམ་འབད་སྒྱེཝ་ལས་ག་ར་གིས་གཅིག་གིས་གཅིག་ལུ་སྤུན་ཆའི་དམ་ཚིག་བསྟན་དགོ།";
        
        // TODO seems like we don't have many matching ngrams for Waray?
        // TODO maybe we should have a "detailed" detection mode, where we ignore ngram probabilities
        // for languages that are essentially at 0 probability. We'd have to sum the the probabilities
        // for contender languages.
        // Normally we detect Waray as Cebuano, but let's skew probabilities.
        String waray = "Nga an ngatanan nga mga tawo, nahimugso talwas ug katpong ha ira dignidad ug katdungan. "
                        + "Hira natawo dinhi ha tuna mayda konsensya ug isip ug kaangayan gud la nga an ira pagtagad "
                        + "ha tagsatagsa sugad hin magburugto.";        

        String cebuano = "Ang tanang katawhan gipakatawo nga may kagawasan ug managsama sa kabililhon. Sila gigasahan "
                        + " sa salabutan ug tanlag og mag-ilhanay isip managsoon sa usa'g-usa diha sa diwa sa ospiritu.";
        
        _detector.setRenormalizeInterval(1);
        // detector.setDampening(detector.getDampening() * 10.0);
        // detector.setAlpha(detector.getAlpha() * 10.0);
        
        _detector.addText(dzongkha);
        Collection<DetectionResult> results = _detector.detect();
        for (DetectionResult result : results) {
            System.out.println(result);
        }
    }
    
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
            detector.reset();
            detector.addText(knownText.get(languageTag));
            
            Collection<DetectionResult> results = detector.detect();
            LanguageLocale targetLanguage = LanguageLocale.fromString(languageTag);
            assertTrue("No results for language " + targetLanguage, results.size() > 0);
            DetectionResult result = results.iterator().next();
            assertTrue(result.getLanguage().weaklyEqual(targetLanguage));
            LOGGER.debug(String.format("'%s': %s", targetLanguage, result.toString()));
        }
        
        // TODO verify that we have low confidence in whatever results we get back here.
        for (String languageTag : unknownText.keySet()) {
            detector.reset();
            detector.addText(unknownText.get(languageTag));

            Collection<DetectionResult> results = detector.detect();
            LanguageLocale targetLanguage = LanguageLocale.fromString(languageTag);
            for (DetectionResult result : results) {
                LOGGER.info(String.format("'%s': %s", targetLanguage, result.toString()));                
            }
        }
    }

    @Test
    public void testUniversalDeclarationOfHumanRights() throws Exception {
        System.setProperty("logging.root.level", "INFO");
        // Logger.getRootLogger().setLevel(Level.INFO);

        File modelDir = new File("src/main/resources/org/krugler/yalder/models/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelDir, true);
        HashLanguageDetector detector = new HashLanguageDetector(models);

        FileInputStream fis = new FileInputStream("src/test/resources/udhr.txt");
        List<String> lines = IOUtils.readLines(fis, "UTF-8");
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
            
            detector.reset();
            detector.addText(text);
            Collection<DetectionResult> result = detector.detect();
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
            
            detector.reset();
            detector.addText(text);
            List<DetectionResult> sortedResults = new ArrayList<DetectionResult>(detector.detect());
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

    // TODO re-enable
    @Ignore
    @Test
    public void testChineseWithSpaces() throws Exception {
        File modelDir = new File("src/main/resources/org/krugler/yalder/models/core/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelDir, true);
        HashLanguageDetector detector = new HashLanguageDetector(models);
        detector.reset();
        detector.addText("下 载 mac 清 理 工 具");
        Collection<DetectionResult> results = detector.detect();
        
        assertTrue(results.size() > 0);
        
        DetectionResult topResult = results.iterator().next();
        assertEquals("zho", topResult.getLanguage().getISO3LetterName());
        assertTrue(topResult.getScore() > 0.8);
    }
    
    @Ignore
    @Test
    public void testShortJapanese() throws Exception {
        File modelDir = new File("src/main/resources/org/krugler/yalder/models/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelDir, true);
        HashLanguageDetector detector = new HashLanguageDetector(models);
        detector.reset();
        detector.addText("私はガラスを食べられます。それは私を傷つけません。");
        Collection<DetectionResult> results = detector.detect();
        
        assertTrue(results.size() > 0);
        
        DetectionResult topResult = results.iterator().next();
        assertEquals("jpn", topResult.getLanguage().getISO3LetterName());
        assertTrue(topResult.getScore() > 0.8);
    }
    
    private static class LangDistanceResult implements Comparable<LangDistanceResult> {
        private LanguageLocale _l1;
        private LanguageLocale _l2;
        private double _distance;
        
        public LangDistanceResult(LanguageLocale l1, LanguageLocale l2, double distance) {
            _l1 = l1;
            _l2 = l2;
            _distance = distance;
        }

        public LanguageLocale getL1() {
            return _l1;
        }

        public LanguageLocale getL2() {
            return _l2;
        }

        public double getDistance() {
            return _distance;
        }

        @Override
        public int compareTo(LangDistanceResult o) {
            if (_distance > o._distance) {
                return -1;
            } else if (_distance < o._distance) {
                return 1;
            } else {
                return 0;
            }
        }
    }
    
    @Test
    public void testCloseLanguages() throws Exception {
        List<LanguageLocale> languages = ExtraModels.EXTRA_LANGUAGES;
        languages.addAll(CoreModels.CORE_LANGUAGES);
        Collection<BaseLanguageModel> models = loadModels(languages);
        HashLanguageDetector detector = new HashLanguageDetector(models);

        List<LangDistanceResult> results = new ArrayList<>();
        for (int i = 0; i < languages.size(); i++) {
            LanguageLocale ll1 = languages.get(i);
            for (int j = i + 1; j < languages.size(); j++) {
                LanguageLocale ll2 = languages.get(j);
                double distance = detector.calcDistance(ll1, ll2);
                results.add(new LangDistanceResult(ll1, ll2, distance));
            }
        }
        
        Collections.sort(results);
        
        for (LangDistanceResult result : results) {
            double distance = result.getDistance();
            if (distance < 0.04) {
                break;
            }
            
            System.out.format("%s to %s: %f\n", result.getL1(), result.getL2(), distance);
        }
    }
    
    // With default alpha, results are OK (error rate of 0.13%) until
    // dampening gets to 0.837, after which the error rate starts climbing
    // quickly. With no dampening, error rate is 0.057% (so 1/20th of a 
    // percent).
    
    @Ignore
    @Test
    public void testDampening() throws Exception {
        Collection<BaseLanguageModel> models = loadModels(OtherDetectorsTest.TARGET_LANGUAGES_FOR_YALDER);
        
        List<String> lines = EuroParlUtils.readLines();
        HashLanguageDetector detector = new HashLanguageDetector(models);
        
        // Keep incrementing dampening.
        double dampening = 0.0;
        while (dampening < 1.0) {
            detector.setDampening(dampening);
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
                }
            }
            
            System.out.println(String.format("Dampening %04f: error rate = %f%%", dampening, 100.0 * (double)numMisses/(double)(numMisses + numHits)));
            dampening += 0.001;
        }
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
                
                detector.reset();
                detector.addText(text);
                Collection<DetectionResult> result = detector.detect();
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
        List<LanguageLocale> languages = new ArrayList<>(targetLanguages.length);
        for (String language : targetLanguages) {
            languages.add(LanguageLocale.fromString(language));
        }
        
        return loadModels(languages);
    }
    
    private Collection<BaseLanguageModel> loadModels(List<LanguageLocale> targetLanguages) throws IOException {
        Collection<BaseLanguageModel> result = new ArrayList<>();
        for (LanguageLocale ll : targetLanguages) {
            HashLanguageModel model = new HashLanguageModel();
            String modelName = String.format("/org/krugler/yalder/models/core/yalder_model_%s.bin", ll.getName());
            InputStream is = HashLanguageDetectorTest.class.getResourceAsStream(modelName);
            if (is == null) {
                LOGGER.debug(String.format("Loading non-core model '%s'", ll.getName()));
                modelName = String.format("/org/krugler/yalder/models/extras/yalder_model_%s.bin", ll.getName());
                is = HashLanguageDetectorTest.class.getResourceAsStream(modelName);
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
