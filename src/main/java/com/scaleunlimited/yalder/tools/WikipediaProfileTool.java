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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.EuroParlUtils;
import com.scaleunlimited.yalder.LanguageDetector;
import com.scaleunlimited.yalder.LanguageModel;
import com.scaleunlimited.yalder.ModelBuilder;

public class WikipediaProfileTool {
    public static final Logger LOGGER = Logger.getLogger(WikipediaProfileTool.class);
    
    private WikipediaProfileOptions _options;
    private Collection<LanguageModel> _models;
    
    public WikipediaProfileTool(WikipediaProfileOptions options) {
        _options = options;
    }
    
    public void buildModels(int numNGgramsPerLanguage, double minNGramProbability) throws IOException {
        File inputDir = new File(_options.getInputDir());
        if (!inputDir.exists()) {
            throw new IllegalArgumentException(String.format("The directory '%s' doesn't exist", inputDir.toString()));
        }
        
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
        }
        
        System.out.println(String.format("Loading text lines from files in '%s'...", inputDir.getCanonicalPath()));

        ModelBuilder mb = new ModelBuilder()
            .setNGramsPerLanguage(numNGgramsPerLanguage)
            .setMinNGramProbability(minNGramProbability)
            .setProbabilityScorePower(1.0);

        
        Map<String, Integer> charsPerLanguage = new HashMap<String, Integer>();
        
        for (File file : FileUtils.listFiles(inputDir, new String[]{"txt"}, true)) {
            String filename = file.getName();
            Pattern p = Pattern.compile("(.+)_(.+).txt");
            Matcher m = p.matcher(filename);
            if (!m.matches()) {
                LOGGER.warn(String.format("Found file '%s' without a language code", filename));
                continue;
            }
            
            int numChars = 0;
            String language = m.group(2);
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
        
        // TODO remove languages with less than X characters
        for (String language : charsPerLanguage.keySet()) {
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
                System.out.println(String.format("Model for '%s'", model.getLanguage()));
                
                Map<CharSequence, Integer> ngramCounts = model.getNGramCounts();
                for (CharSequence ngram : ngramCounts.keySet()) {
                    System.out.println(String.format("\t'%s': %d", ngram, ngramCounts.get(ngram)));
                }
            }
        }
    }
    
    public void interactiveTest() throws IOException {
        System.out.flush();
        System.out.println("Enter comma-separated languages for models (return == all Europarl): ");
        String languages = readInputLine();

        Set<String> supportedLanguages = new HashSet<String>();
        if (languages.isEmpty()) {
            List<String> lines = EuroParlUtils.readLines();
            for (String line : lines) {
                // Format is <language code><tab>text
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                supportedLanguages.add(language);
            }
        } else {
            for (String language : languages.split(",")) {
                supportedLanguages.add(language.trim());
            }
        }

        Set<LanguageModel> activeModels = new HashSet<LanguageModel>();
        for (LanguageModel model : _models) {
            if (supportedLanguages.contains(model.getLanguage())) {
                System.out.println("Adding model for " + model.getLanguage());
                activeModels.add(model);
            }
        }

        LanguageDetector detector = new LanguageDetector(activeModels, 4);

        System.out.flush();
        System.out.println("Enter alpha to use during detection (return == default): ");
        String alphaAsStr = readInputLine();
        if (!alphaAsStr.isEmpty()) {
            detector.setAlpha(Double.parseDouble(alphaAsStr));
        }
        
        while (true) {
            System.out.flush();
            System.out.println("Enter text to analyze (return to quit): ");
            String text = readInputLine();
            if (text.isEmpty()) {
                break;
            }
            
            Collection<DetectionResult> results = detector.detect(text, true);
            DetectionResult result = results.iterator().next();
            System.out.println(String.format("Detected as '%s':\n'%s'", result.getLanguage(), result.getDetails()));
        }
    }
    
    public void testEuroparl() throws IOException {
        // Try out these models on the corpus we've got.
        Set<String> testLanguages = new HashSet<String>();
        List<String> lines = EuroParlUtils.readLines();
        for (String line : lines) {
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            testLanguages.add(language);
        }

        Set<LanguageModel> supportedLanguages = new HashSet<LanguageModel>();
        for (LanguageModel model : _models) {
            if (testLanguages.contains(model.getLanguage())) {
                System.out.println("Adding model for " + model.getLanguage());
                supportedLanguages.add(model);
            }
        }
        
        Map<String, Integer> correctLines = new HashMap<String, Integer>();
        Map<String, Integer> incorrectLines = new HashMap<String, Integer>();
        
        LanguageDetector detector = new LanguageDetector(supportedLanguages, 4);
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

        for (String language : testLanguages) {
            Integer correctCountAsObj = correctLines.get(language);
            int correctCount = correctCountAsObj == null ? 0 : correctCountAsObj;
            
            Integer incorrectCountAsObj = incorrectLines.get(language);
            int incorrectCount = incorrectCountAsObj == null ? 0 : incorrectCountAsObj;
            
            System.out.println(String.format("'%s' error rate = %.2f", language, (100.0 * incorrectCount)/(correctCount +incorrectCount)));
        }
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
             tool.buildModels(5000, 0.02);

            // Go into the mode where we ask the user what they want to do.
            while (true) {
                System.out.flush();
                System.out.println("Enter command (test, euro, save, dump, quit): ");
                String cmdName = readInputLine();
                if (cmdName.equalsIgnoreCase("test")) {
                    tool.interactiveTest();
                } else if (cmdName.equalsIgnoreCase("euro")) {
                    tool.testEuroparl();
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
        
        @Option(name = "-inputdir", usage = "input directory containing training data", required = true)
        public void setInputDir(String inputDir) {
            _inputDir = inputDir;
        }

        public String getInputDir() {
            return _inputDir;
        }
    }
}
