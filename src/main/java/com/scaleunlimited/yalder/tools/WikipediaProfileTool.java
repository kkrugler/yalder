package com.scaleunlimited.yalder.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.EuroParlUtils;
import com.scaleunlimited.yalder.LanguageDetector;
import com.scaleunlimited.yalder.LanguageLocale;
import com.scaleunlimited.yalder.LanguageModel;
import com.scaleunlimited.yalder.ModelBuilder;

public class WikipediaProfileTool {
    public static final Logger LOGGER = Logger.getLogger(WikipediaProfileTool.class);
    
    private static final int MAX_NGRAM_LENGTH = 4;
    
    private static final int MIN_NORMALIZED_NGRAM_COUNT = 10;
    
    private WikipediaProfileOptions _options;
    private Collection<LanguageModel> _models;
    
    public WikipediaProfileTool(WikipediaProfileOptions options) {
        _options = options;
    }
    
    public void buildModels() throws IOException {
        ModelBuilder mb = new ModelBuilder()
            .setMaxNGramLength(MAX_NGRAM_LENGTH)
            .setMinNormalizedCount(MIN_NORMALIZED_NGRAM_COUNT);
        
        // TODO add support for remapping some language names, e.g.
        // zh-min-nan => nan (Min Nan dialect of Chinese)
        
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

        Map<LanguageLocale, Integer> charsPerLanguage = new HashMap<LanguageLocale, Integer>();

        if (_options.getInputFile() != null) {
            File inputFile = new File(_options.getInputFile());
            
            if (!inputFile.exists()) {
                throw new IllegalArgumentException(String.format("The file '%s' doesn't exist", inputFile.toString()));
            }

            if (inputFile.isDirectory()) {
                throw new IllegalArgumentException(String.format("'%s' is a directory", inputFile.toString()));
            }

            System.out.println(String.format("Loading text lines from file '%s'...", inputFile.getCanonicalPath()));

            List<String> lines = FileUtils.readLines(inputFile, "UTF-8");
            for (String line : lines) {
                String[] parts = line.split("\t", 2);
                String languageAsStr = parts[0];
                if (excludedLanguages.contains(languageAsStr)) {
                    continue;
                }
                
                LanguageLocale language = LanguageLocale.fromString(languageAsStr);
                
                String text = parts[1];
                mb.addTrainingDoc(language, text);

                int numChars = text.length();
                Integer curCount = charsPerLanguage.get(language);
                if (curCount == null) {
                    charsPerLanguage.put(language, numChars);
                } else {
                    charsPerLanguage.put(language, numChars + curCount);
                }
            }
        }
        
        if (_options.getInputDir() != null) {
            File inputDir = new File(_options.getInputDir());
            if (!inputDir.exists()) {
                throw new IllegalArgumentException(String.format("The directory '%s' doesn't exist", inputDir.toString()));
            }

            if (!inputDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
            }

            System.out.println(String.format("Loading text lines from files in '%s'...", inputDir.getCanonicalPath()));

            for (File file : FileUtils.listFiles(inputDir, new String[]{"txt"}, true)) {
                String filename = file.getName();
                Pattern p = Pattern.compile("(.+)_(.+).txt");
                Matcher m = p.matcher(filename);
                if (!m.matches()) {
                    LOGGER.warn(String.format("Found file '%s' without a language code", filename));
                    continue;
                }

                String languageAsStr = m.group(2);
                
                if (excludedLanguages.contains(languageAsStr)) {
                    continue;
                }
                
                LanguageLocale language = LanguageLocale.fromString(languageAsStr);
                int numChars = 0;
                List<String> lines = FileUtils.readLines(file, "UTF-8");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        mb.addTrainingDoc(language, line);
                    }

                    numChars += line.length();
                }

                Integer curCount = charsPerLanguage.get(language);
                if (curCount == null) {
                    charsPerLanguage.put(language, numChars);
                } else {
                    charsPerLanguage.put(language, numChars + curCount);
                }

                // System.out.println(String.format("Added %d lines (%d chars) for language '%s'", lines.size(), numChars, language));
            }
        }
        
        if (charsPerLanguage.isEmpty()) {
            throw new IllegalArgumentException("Data must be provided for at least one language");
        }
        
        for (LanguageLocale language : charsPerLanguage.keySet()) {
            System.out.println(String.format("Language '%s' had %d chars", language, charsPerLanguage.get(language)));
        }
        
        long startTime = System.currentTimeMillis();
        System.out.println("Building training models...");
        _models = mb.makeModels();
        long deltaTime = System.currentTimeMillis() - startTime;
        System.out.println(String.format("Built %d models in %dms", _models.size(), deltaTime));
    }
    
    public void dumpModels() throws IOException {
        System.out.flush();
        System.out.println("Enter comma-separated languages for models (return == all models): ");
        String languages = readInputLine();
        
        Set<String> targetLanguages = new HashSet<String>();
        if (!languages.isEmpty()) {
            for (String language : languages.split(",")) {
                targetLanguages.add(language.trim());
            }
        }

        for (LanguageModel model : _models) {
            if (targetLanguages.isEmpty() || targetLanguages.contains(model.getLanguage())) {
                System.out.println(String.format("Model for '%s':", model.getLanguage()));
                
                Map<String, Integer> ngramCounts = model.getNGramCounts();
                for (String ngram : ngramCounts.keySet()) {
                    System.out.println(String.format("\t'%s': %d", ngram, ngramCounts.get(ngram)));
                }
                
                System.out.println(String.format("Model for '%s' has %d ngrams", model.getLanguage(), ngramCounts.size()));
            }
        }
    }
    
    private double testEuro(List<String> lines, LanguageDetector detector) throws IOException {
        int errorCount = 0;
        int totalCount = 0;
        
        if (lines == null) {
            lines = EuroParlUtils.readLines();
        }
        
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];
            
            totalCount += 1;
            Collection<DetectionResult> results = detector.detect(text);
            if (results.isEmpty()) {
                errorCount += 1;
            } else {
                DetectionResult result = results.iterator().next();
                if (!language.equals(result.getLanguage())) {
                    errorCount += 1;
                }
            }
        }
        
        double errorRate = (100.0 * errorCount) / totalCount;
        return errorRate;
    }
    
    public void tuneParameters() throws IOException {
        System.out.print("Enter number of trials: ");
        String trialsAsStr = readInputLine();
        int trials = Integer.parseInt(trialsAsStr);
        
        List<String> lines = EuroParlUtils.readLines();
        Set<LanguageLocale> supportedLanguages = getLanguages(lines);
        LanguageDetector detector = new LanguageDetector(getModels(supportedLanguages), MAX_NGRAM_LENGTH);
        
        // Set up default based on what we think is the current "optimal" values, but scaled up to give
        // us some room to explore the potential space away from 0.
        double defaultAlpha = detector.getAlpha() * 2;
        double defaultDampening = detector.getDampening() * 2;
        
        Random rand = new Random(System.currentTimeMillis());
        
        for (int i = 0; i < trials; i++) {
            // Set alpha and dampening to random values that vary around the current defaults
            while (true) {
                double nextAlpha = defaultAlpha * (1 + rand.nextGaussian());
                if (nextAlpha < 0.0) {
                    continue;
                }
                detector.setAlpha(nextAlpha);
                
                double nextDampening = defaultDampening * (1 + rand.nextGaussian());
                if (nextDampening < 0.0) {
                    continue;
                }
                detector.setDampening(nextDampening);
                break;
            }
            
            double errorRate = testEuro(lines, detector);
            System.out.println(String.format("%.6f: alpha=%f, dampening=%f", errorRate, detector.getAlpha(), detector.getDampening()));
        }
    }
    
    public void interactiveTest() throws IOException {
        List<String> lines = EuroParlUtils.readLines();
        Set<LanguageLocale> supportedLanguages = getLanguages(lines);
        Set<LanguageModel> activeModels = getModels(supportedLanguages);

        LanguageDetector detector = new LanguageDetector(activeModels, MAX_NGRAM_LENGTH);
        Set<String> detailLanguages = null;
        
        while (true) {
            System.out.print("Enter text to analyze (return to quit): ");
            String text = readInputLine();
            if (text.isEmpty()) {
                break;
            } else if (text.equals("damp")) {
                System.out.print(String.format("Enter dampening to use during detection (currently %f): ", detector.getDampening()));
                detector.setDampening(Double.parseDouble(readInputLine()));
            } else if (text.equals("alpha")) {
                System.out.print(String.format("Enter alpha to use during detection (currently %f): ", detector.getAlpha()));
                detector.setAlpha(Double.parseDouble(readInputLine()));
            } else if (text.equals("euro")) {
                double errorRate = testEuro(lines, detector);
                System.out.println(String.format("Error rate = %.6f: alpha=%f, dampening=%f", errorRate, detector.getAlpha(), detector.getDampening()));
            } else if (text.equals("lang")) {
                System.out.print("Enter comma-separated languages for comparison (return == all): ");
                String detailLanguagesAsStr = readInputLine();
                if (detailLanguagesAsStr.isEmpty()) {
                    detailLanguages = null;
                } else {
                    detailLanguages = new HashSet<String>();
                    for (String detailLanguage : detailLanguagesAsStr.split(",")) {
                        detailLanguages.add(detailLanguage.trim());
                    }
                }
            } else {
                StringBuilder details = new StringBuilder();
                Collection<DetectionResult> results = detector.detect(text, details, detailLanguages);
                System.out.println(String.format("Detection details:\n%s", details));
                System.out.println(String.format("Alpha = %f, dampening = %f", detector.getAlpha(), detector.getDampening()));
                if (results.isEmpty()) {
                    System.out.println("No language reliably detected");
                } else {
                    DetectionResult result = results.iterator().next();
                    System.out.println(String.format("Detected as '%s'", result.getLanguage()));
                }
            }
        }
    }
    
    private Set<LanguageLocale> getLanguages(List<String> euroParlLines) throws IOException {
        System.out.flush();
        System.out.println("Enter comma-separated languages for models (all == every language, return == only Europarl): ");
        String languages = readInputLine();

        Set<LanguageLocale> supportedLanguages = new HashSet<LanguageLocale>();
        if (languages.isEmpty()) {
            for (String line : euroParlLines) {
                // Format is <language code><tab>text
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                supportedLanguages.add(LanguageLocale.fromString(language));
            }
        } else if (languages.equals("all")) {
            for (LanguageModel model : _models) {
                supportedLanguages.add(model.getLanguage());
            }
        } else {
            for (String language : languages.split(",")) {
                supportedLanguages.add(LanguageLocale.fromString(language.trim()));
            }
        }

        return supportedLanguages;
    }
    
    private Set<LanguageModel> getModels(Set<LanguageLocale> supportedLanguages) throws IOException {
        Set<LanguageModel> result = new HashSet<LanguageModel>();
        for (LanguageModel model : _models) {
            if (supportedLanguages.contains(model.getLanguage())) {
                result.add(model);
            }
        }

        return result;
    }
    
    public void testEuroparl() throws IOException {
        List<String> lines = EuroParlUtils.readLines();
        Set<LanguageLocale> supportedLanguages = getLanguages(lines);
        
        // Map from language to correct/incorrect counts.
        Map<String, Integer> correctLines = new HashMap<String, Integer>();
        Map<String, Integer> incorrectLines = new HashMap<String, Integer>();
        
        LanguageDetector detector = new LanguageDetector(getModels(supportedLanguages), MAX_NGRAM_LENGTH);
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];
            
            boolean correct = false;
            Collection<DetectionResult> results = detector.detect(text);
            if (results.isEmpty()) {
                System.out.println(String.format("'%s' detected as ' ': %s", language, text));
            } else {
                DetectionResult result = results.iterator().next();
                if (language.equals(result.getLanguage())) {
                    correct = true;
                } else {
                    System.out.println(String.format("'%s' detected as '%s': %s", language, result.getLanguage(), text));
                }
            }
            
            Map<String, Integer> mapToIncrement = correct ? correctLines : incorrectLines;
            Integer curCount = mapToIncrement.get(language);
            mapToIncrement.put(language, curCount == null ? 1 : curCount + 1);
        }

        int totalCorrect = 0;
        int totalIncorrect = 0;
        for (LanguageLocale language : supportedLanguages) {
            Integer correctCountAsObj = correctLines.get(language);
            int correctCount = correctCountAsObj == null ? 0 : correctCountAsObj;
            totalCorrect += correctCount;
            
            Integer incorrectCountAsObj = incorrectLines.get(language);
            int incorrectCount = incorrectCountAsObj == null ? 0 : incorrectCountAsObj;
            totalIncorrect += incorrectCount;
            
            int languageCount = correctCount + incorrectCount;
            if (languageCount > 0) {
                System.out.println(String.format("'%s' error rate = %.2f", language, (100.0 * incorrectCount)/languageCount));
            }
        }
        
        System.out.println(String.format("Total error rate = %.2f", (100.0 * totalIncorrect)/(totalCorrect +totalIncorrect)));
    }
    
    public void saveModels() throws IOException {
        // TODO save the models in an output dir that user provides, as
        // yalder_model_<language>.bin
        // Directory must exist, and be empty
    }
    
    public static void main(String[] args) {
        WikipediaProfileOptions options = new WikipediaProfileOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        WikipediaProfileTool tool = new WikipediaProfileTool(options);
        
        try {
             tool.buildModels();

            // Go into the mode where we ask the user what they want to do.
            while (true) {
                System.out.flush();
                System.out.println("Enter command (test, euro, tune, save, dump, quit): ");
                String cmdName = readInputLine();
                if (cmdName.equalsIgnoreCase("test")) {
                    tool.interactiveTest();
                } else if (cmdName.equalsIgnoreCase("euro")) {
                    tool.testEuroparl();
                } else if (cmdName.equalsIgnoreCase("tune")) {
                    tool.tuneParameters();
                } else if (cmdName.equalsIgnoreCase("dump")) {
                    tool.dumpModels();
                } else if (cmdName.equalsIgnoreCase("save")) {
                    tool.saveModels();
                } else if (cmdName.equalsIgnoreCase("quit")) {
                    break;
                } else {
                    System.out.println("Unknown command: " + cmdName);
                }

            }
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    /**
     * Read one line of input from the console.
     * 
     * @return Text that the user entered
     * @throws IOException
     */
    private static String readInputLine() throws IOException {
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    private static class WikipediaProfileOptions {
        
        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
        private String _inputDir = null;
        private String _inputFile = null;
        
        @Option(name = "-debug", usage = "debug logging", required = false)
        public void setDebugLogging(boolean debugLogging) {
            _debugLogging = debugLogging;
        }

        @Option(name = "-trace", usage = "trace logging", required = false)
        public void setTraceLogging(boolean traceLogging) {
            _traceLogging = traceLogging;
        }

        public boolean isDebugLogging() {
            return _debugLogging;
        }
        
        public boolean isTraceLogging() {
            return _traceLogging;
        }
        
        public Level getLogLevel() {
            if (isTraceLogging()) {
                return Level.TRACE;
            } else if (isDebugLogging()) {
                return Level.DEBUG;
            } else {
                return Level.INFO;
            }
        }
        
        @Option(name = "-inputdir", usage = "input directory containing training data", required = false)
        public void setInputDir(String inputDir) {
            _inputDir = inputDir;
        }

        public String getInputDir() {
            return _inputDir;
        }
        
        @Option(name = "-inputfile", usage = "input file containing training data", required = false)
        public void setInputFile(String inputFile) {
            _inputFile = inputFile;
        }

        public String getInputFile() {
            return _inputFile;
        }
    }
}
