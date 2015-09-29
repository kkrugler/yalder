package com.scaleunlimited.yalder.tools;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.yalder.BaseLanguageDetector;
import com.scaleunlimited.yalder.BaseLanguageModel;
import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.EuroParlUtils;
import com.scaleunlimited.yalder.LanguageLocale;
import com.scaleunlimited.yalder.ModelBuilder;
import com.scaleunlimited.yalder.hash.HashLanguageDetector;
import com.scaleunlimited.yalder.hash.HashLanguageModel;

public class ModelBuilderTool {
    public static final Logger LOGGER = Logger.getLogger(ModelBuilderTool.class);
    
    private ModelBuilderOptions _options;
    private ModelBuilder _builder;
    private Collection<BaseLanguageModel> _models;
    
    public ModelBuilderTool(ModelBuilderOptions options) {
        _options = options;
        
        _builder = new ModelBuilder()
            .setMaxNGramLength(_options.getMaxNGramLength())
            .setMinNormalizedCount(_options.getMinNGramCount());
        
        _models = null;
    }
    
    public void buildModels() throws IOException {
        // TODO what happens if we haven't loaded any data yet?
        
        long startTime = System.currentTimeMillis();
        System.out.println("Building training models...");
        _models = _builder.makeModels();
        long deltaTime = System.currentTimeMillis() - startTime;
        System.out.println(String.format("Built %d models in %dms", _models.size(), deltaTime));
    }
    
    public void loadTrainingFile(String filename) throws IOException {
        File inputFile = new File(filename);
        
        if (!inputFile.exists()) {
            System.out.println(String.format("The file '%s' doesn't exist", inputFile.toString()));
            return;
        }

        if (inputFile.isDirectory()) {
            System.out.println(String.format("'%s' is a directory", inputFile.toString()));
            return;
        }

        System.out.println(String.format("Loading text lines from file '%s'...", inputFile.getCanonicalPath()));

        // TODO try to extract language from filename, and only require the tabbed format
        // if the filename doesn't have it.

        List<String> lines = FileUtils.readLines(inputFile, "UTF-8");
        for (String line : lines) {
            String[] parts = line.split("\t", 2);
            LanguageLocale language = LanguageLocale.fromString(parts[0]);
            String text = parts[1];
            _builder.addTrainingDoc(language, text);
        }
    }
    
    public void loadTrainingDir(String dirname) throws IOException {

        File inputDir = new File(dirname);
        if (!inputDir.exists()) {
            System.out.println(String.format("The directory '%s' doesn't exist", inputDir.toString()));
            return;
        }

        if (!inputDir.isDirectory()) {
            System.out.println(String.format("'%s' is not a directory", inputDir.toString()));
            return;
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

            LanguageLocale language = LanguageLocale.fromString(m.group(2));
            List<String> lines = FileUtils.readLines(file, "UTF-8");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    _builder.addTrainingDoc(language, line);
                }
            }
        }
    }
    
    public void loadTrainingData() throws IOException {
        
        // Ask the user for the name of the file or directory to load.
        // If it's a file, the format must be <language><tab><text>, one per line.
        // If it's a directory, each file found must have a name of the form xxx_<language>.txt
        
        String dirOrFilename = readInputLine("Enter a file or directory path that contains sample text: ");
        if (dirOrFilename.isEmpty()) {
            return;
        }
        
        File dirOrFile = new File(dirOrFilename);
        if (dirOrFile.isFile()) {
            loadTrainingFile(dirOrFilename);
        } else {
            loadTrainingDir(dirOrFilename);
        }
    }
    
    private double testEuro(List<String> lines, BaseLanguageDetector detector) throws IOException {
        int errorCount = 0;
        int totalCount = 0;
        
        if (lines == null) {
            lines = EuroParlUtils.readLines();
        }
        
        for (String line : lines) {
            // Format is <ISO 639-1 eurolanguage code><tab>text
            String[] pieces = line.split("\t", 2);
            LanguageLocale language = LanguageLocale.fromString(pieces[0]);
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
    
    public void interactiveTest() throws IOException {
        // Make sure we've got models loaded.
        if (_models == null) {
            System.out.println("Models must be built or loaded first");
            return;
        }
        
        // TODO verify that we have text-based models loaded
        
        List<String> lines = EuroParlUtils.readLines();
        Set<LanguageLocale> supportedLanguages = getLanguages(lines);
        Set<BaseLanguageModel> activeModels = getModels(supportedLanguages);

        com.scaleunlimited.yalder.text.TextLanguageDetector detector = new com.scaleunlimited.yalder.text.TextLanguageDetector(activeModels);
        Set<String> detailLanguages = null;
        
        while (true) {
            String text = readInputLine("Enter text to analyze (return to quit): ");
            if (text.isEmpty()) {
                break;
            } else if (text.equals("damp")) {
                String prompt = String.format("Enter dampening to use during detection (currently %f): ", detector.getDampening());
                detector.setDampening(Double.parseDouble(readInputLine(prompt)));
            } else if (text.equals("alpha")) {
                String prompt = String.format("Enter alpha to use during detection (currently %f): ", detector.getAlpha());
                detector.setAlpha(Double.parseDouble(readInputLine(prompt)));
            } else if (text.equals("euro")) {
                double errorRate = testEuro(lines, detector);
                System.out.println(String.format("Error rate = %.6f: alpha=%f, dampening=%f", errorRate, detector.getAlpha(), detector.getDampening()));
            } else if (text.equals("lang")) {
                String detailLanguagesAsStr = readInputLine("Enter comma-separated languages for comparison (return == all): ");
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
        String languages = readInputLine("Enter comma-separated languages for models (all == every language, return == only Europarl): ");

        Set<LanguageLocale> supportedLanguages = new HashSet<LanguageLocale>();
        if (languages.isEmpty()) {
            for (String line : euroParlLines) {
                // Format is <language code><tab>text
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                supportedLanguages.add(LanguageLocale.fromString(language));
            }
        } else if (languages.equals("all")) {
            for (BaseLanguageModel model : _models) {
                supportedLanguages.add(model.getLanguage());
            }
        } else {
            for (String language : languages.split(",")) {
                supportedLanguages.add(LanguageLocale.fromString(language.trim()));
            }
        }

        return supportedLanguages;
    }
    
    private Set<BaseLanguageModel> getModels(Set<LanguageLocale> supportedLanguages) throws IOException {
        Set<BaseLanguageModel> result = new HashSet<>();
        for (BaseLanguageModel model : _models) {
            if (supportedLanguages.contains(model.getLanguage())) {
                result.add(model);
            }
        }

        return result;
    }
    
    public void testEuroparl() throws IOException {
        // Make sure we've got models loaded.
        if (_models == null) {
            System.out.println("Models must be built or loaded first");
            return;
        }
        
        List<String> lines = EuroParlUtils.readLines();
        Set<LanguageLocale> supportedLanguages = getLanguages(lines);
        
        // Map from language to correct/incorrect counts.
        Map<LanguageLocale, Integer> correctLines = new HashMap<LanguageLocale, Integer>();
        Map<LanguageLocale, Integer> incorrectLines = new HashMap<LanguageLocale, Integer>();
        
        // TODO get max ngram length from models
        HashLanguageDetector detector = new HashLanguageDetector(getModels(supportedLanguages));
        for (String line : lines) {
            // Format is <ISO 639-1 language code><tab>text
            String[] pieces = line.split("\t", 2);
            LanguageLocale language = LanguageLocale.fromString(pieces[0]);
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
            
            Map<LanguageLocale, Integer> mapToIncrement = correct ? correctLines : incorrectLines;
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
    
    public void loadModels() throws IOException {
        String dirname = readInputLine("Enter path to directory containing models: ");
        if (dirname.length() == 0) {
            return;
        }
        
        File dirFile = new File(dirname);
        if (!dirFile.exists()) {
            System.out.println("Directory must exist");
            return;
        }
        
        if (!dirFile.isDirectory()) {
            System.out.println(String.format("%s is not a directory", dirFile));
            return;
        }
        
        String modelSuffix = readInputLine("Enter type of model (bin or txt): ");
        boolean isBinary = modelSuffix.equals("bin");
        
        System.out.println(String.format("Loading models from files in '%s'...", dirFile.getCanonicalPath()));
        Set<BaseLanguageModel> newModels = new HashSet<>();
        
        int totalPruned = 0;
        for (File file : FileUtils.listFiles(dirFile, new String[]{"txt"}, true)) {
            String filename = file.getName();
            Pattern p = Pattern.compile(String.format("yalder_model_(.+).%s", modelSuffix));
            Matcher m = p.matcher(filename);
            if (!m.matches()) {
                LOGGER.warn(String.format("Found file '%s' with invalid name", filename));
                continue;
            }

            // Verify that language is valid
            LanguageLocale.fromString(m.group(1));
            
            // Figure out if it's binary or text
            BaseLanguageModel model = null;
            if (isBinary) {
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                com.scaleunlimited.yalder.hash.HashLanguageModel binaryModel = new com.scaleunlimited.yalder.hash.HashLanguageModel();
                binaryModel.readAsBinary(dis);
                dis.close();
                
                model = binaryModel;
            } else {
                InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8");
                com.scaleunlimited.yalder.text.TextLanguageModel textModel = new com.scaleunlimited.yalder.text.TextLanguageModel();
                textModel.readAsText(isr);
                isr.close();

                model = textModel;
            }

            totalPruned += model.prune(20);
            
            newModels.add(model);
        }

        if (newModels.isEmpty()) {
            System.out.println("No models were loaded, keeping current models (if any)");
            return;
        }
        
        System.out.println(String.format("Pruned %d ngrams by setting min count to 20", totalPruned));
        _models = newModels;
    }
    
    public void saveModels() throws IOException {
        // Make sure we've got models created.
        if (_models == null) {
            System.out.println("Models must be built or loaded first");
            return;
        }

        String dirname = readInputLine("Enter output directory path: ");
        if (dirname.length() == 0) {
            return;
        }
        
        File dirFile = new File(dirname);
        if (!dirFile.exists()) {
            System.out.println("Directory must exist");
            return;
        }
        
        if (!dirFile.isDirectory()) {
            System.out.println(String.format("%s is not a directory", dirFile));
            return;
        }
        
        String modelSuffix = readInputLine("Enter type of model (bin or txt): ");
        boolean isBinary = modelSuffix.equals("bin");

        for (BaseLanguageModel baseModel : _models) {
            String modelFileName = String.format("yalder_model_%s.%s", baseModel.getLanguage().getName(), modelSuffix);
            File modelFile = new File(dirFile,  modelFileName);

            if (isBinary) {
                com.scaleunlimited.yalder.hash.HashLanguageModel model = (com.scaleunlimited.yalder.hash.HashLanguageModel)baseModel;
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(modelFile));
                model.writeAsBinary(dos);
                dos.close();
            } else {
                com.scaleunlimited.yalder.text.TextLanguageModel model = (com.scaleunlimited.yalder.text.TextLanguageModel)baseModel;
                OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(modelFile), "UTF-8");
                model.writeAsText(osw);
                osw.close();
            }
        }
    }
    
    public static void main(String[] args) {
        ModelBuilderOptions options = new ModelBuilderOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        ModelBuilderTool tool = new ModelBuilderTool(options);
        
        try {
            // If the user provided input file(s) and/or directories, load them first.
            // TODO handle multiple inputs for files and/or directories.
            if (options.getInputFile() != null) {
                tool.loadTrainingFile(options.getInputFile());
            }
            
            if (options.getInputDir() != null) {
                tool.loadTrainingDir(options.getInputDir());
            }
            
            // TODO if options has an output directory, auto-build the models
            // and save the models to the output directory, then quit.
            
            
            // Go into the mode where we ask the user what they want to do.
            while (true) {
                // TODO have base tool, with readInputLine code that takes prompt text
                // TODO how to set params per language for collapsing chars, setting max ngram length, etc.
                // TODO add help command
                String cmdName = readInputLine("Enter command (data, build, load, save, test, euro, quit): ");
                if (cmdName.equalsIgnoreCase("data")) {
                    tool.loadTrainingData();
                } else if (cmdName.equalsIgnoreCase("build")) {
                    tool.buildModels();
                } else if (cmdName.equalsIgnoreCase("save")) {
                    tool.saveModels();
                } else if (cmdName.equalsIgnoreCase("load")) {
                    tool.loadModels();
                } else if (cmdName.equalsIgnoreCase("test")) {
                    tool.interactiveTest();
                } else if (cmdName.equalsIgnoreCase("euro")) {
                    tool.testEuroparl();
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
    private static String readInputLine(String prompt) throws IOException {
        System.out.print(prompt);
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    private static class ModelBuilderOptions {
        
        private static final int DEFAULT_MAX_NGRAM_LENGTH = 4;
        
        private static final int DEFAULT_MIN_NORMALIZED_NGRAM_COUNT = 10;
        

        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
        // TODO support multiple input files and directories.
        private String _inputDir = null;
        private String _inputFile = null;
        
        private int _maxNGramLength = DEFAULT_MAX_NGRAM_LENGTH;
        private int _minNormalizedNGramCount = DEFAULT_MIN_NORMALIZED_NGRAM_COUNT;
        
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
        
        @Option(name = "-maxngram", usage = "max length of ngrams, in chars", required = false)
        public void setMaxNGramLength(int maxNGramLength) {
            _maxNGramLength = maxNGramLength;
        }

        public int getMaxNGramLength() {
            return _maxNGramLength;
        }
        
        @Option(name = "-minngramcount", usage = "minimum (normalized) count of ngrams to be included in model", required = false)
        public void setMinNGramCount(int minNGramCount) {
            _minNormalizedNGramCount = minNGramCount;
        }

        public int getMinNGramCount() {
            return _minNormalizedNGramCount;
        }
    }
}
