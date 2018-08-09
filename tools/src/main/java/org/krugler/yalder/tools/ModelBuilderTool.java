package org.krugler.yalder.tools;

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
import org.krugler.yalder.BaseLanguageDetector;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.ModelBuilder;
import org.krugler.yalder.hash.HashLanguageDetector;
import org.krugler.yalder.hash.HashLanguageModel;
import org.krugler.yalder.text.TextLanguageDetector;
import org.krugler.yalder.text.TextLanguageModel;

public class ModelBuilderTool {
    public static final Logger LOGGER = Logger.getLogger(ModelBuilderTool.class);
    
    /*
    English     54.8%
    Russian     5.8%
    German      5.7%
    Japanese        5.0%
    Spanish     4.6%
    French      4.0%
    Portuguese  2.6%
    Chinese     2.5%
    Italian     2.0%
    Polish      1.8%
    Turkish     1.6%
    Dutch, Flemish  1.4%
    Persian     1.0%
    Arabic      0.8%
    Czech       0.7%
    Korean      0.7%
    Vietnamese  0.5%
    Swedish     0.5%
    Indonesian  0.5%
    Greek       0.4%
    Romanian        0.4%
    Hungarian       0.4%
    Danish      0.3%
    Thai            0.3%
    Finnish     0.2%
    Slovak      0.2%
    Bulgarian       0.2%
    Norwegian   0.2%
    Hebrew      0.2%
    Lithuanian      0.1%
    Croatian        0.1%
    Ukrainian       0.1%
    Norwegian Bokmål    0.1%
    Serbian     0.1%
    Catalan, Valencian  0.1%
    Slovenian       0.1%
    Latvian     0.1%
    Estonian        0.1%
    */
    
    // ISO 639-2 codes for all languages with at least 0.1% of web content
    // See http://w3techs.com/technologies/overview/content_language/all
    private static final Set<String> CORE_LANGUAGES = new HashSet<>();
    static {
        CORE_LANGUAGES.add("ara");
        CORE_LANGUAGES.add("bul");
        CORE_LANGUAGES.add("cat");
        CORE_LANGUAGES.add("zho");
        CORE_LANGUAGES.add("hrv");
        CORE_LANGUAGES.add("ces");
        CORE_LANGUAGES.add("dan");
        CORE_LANGUAGES.add("nld");
        CORE_LANGUAGES.add("eng");
        CORE_LANGUAGES.add("est");
        CORE_LANGUAGES.add("fin");
        CORE_LANGUAGES.add("fra");
        CORE_LANGUAGES.add("deu");
        CORE_LANGUAGES.add("ell");
        CORE_LANGUAGES.add("heb");
        CORE_LANGUAGES.add("hun");
        CORE_LANGUAGES.add("ind");
        CORE_LANGUAGES.add("ita");
        CORE_LANGUAGES.add("jap");
        CORE_LANGUAGES.add("kor");
        CORE_LANGUAGES.add("lav");
        CORE_LANGUAGES.add("lit");
        CORE_LANGUAGES.add("nno");
        CORE_LANGUAGES.add("nob");
        CORE_LANGUAGES.add("fas");
        CORE_LANGUAGES.add("pol");
        CORE_LANGUAGES.add("por");
        CORE_LANGUAGES.add("ron");
        CORE_LANGUAGES.add("rus");
        CORE_LANGUAGES.add("srp");
        CORE_LANGUAGES.add("slk");
        CORE_LANGUAGES.add("slv");
        CORE_LANGUAGES.add("spa");
        CORE_LANGUAGES.add("swe");
        CORE_LANGUAGES.add("tha");
        CORE_LANGUAGES.add("tur");
        CORE_LANGUAGES.add("ukr");
        CORE_LANGUAGES.add("vie");
    }
    
    private ModelBuilderOptions _options;
    private boolean _binaryMode = true;
    private ModelBuilder _builder;
    private Collection<BaseLanguageModel> _models;
    
    public ModelBuilderTool(ModelBuilderOptions options) {
        _options = options;
        
        _builder = new ModelBuilder()
            .setBinaryMode(_binaryMode)
            .setMaxNGramLength(_options.getMaxNGramLength())
            .setMinNGramFrequency(_options.getMinNGramFrequency());
        
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
            detector.reset();
            detector.addText(text);
            Collection<DetectionResult> results = detector.detect();
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

        org.krugler.yalder.text.TextLanguageDetector detector = new org.krugler.yalder.text.TextLanguageDetector(activeModels);
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
                detector.reset();
                detector.addText(text.toCharArray(), 0, text.length(), details, detailLanguages);
                Collection<DetectionResult> results = detector.detect();
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
        
        Set<BaseLanguageModel> supportedModels = getModels(supportedLanguages);
        BaseLanguageDetector detector = _binaryMode ? new HashLanguageDetector(supportedModels) : new TextLanguageDetector(supportedModels);
        
        long startTime = System.currentTimeMillis();
        for (String line : lines) {
            // Format is <ISO 639-1 language code><tab>text
            String[] pieces = line.split("\t", 2);
            LanguageLocale language = LanguageLocale.fromString(pieces[0]);
            String text = pieces[1];
            
            boolean correct = false;
            detector.reset();
            detector.addText(text);
            Collection<DetectionResult> results = detector.detect();
            if (results.isEmpty()) {
                System.out.println(String.format("'%s' detected as '<unknown>': %s", language, text));
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
        long deltaTime = System.currentTimeMillis() - startTime;
        System.out.println(String.format("Detecting %d lines took %dms", lines.size(), deltaTime));
        
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
    
    private void changeMode() throws IOException {
        String curMode = _binaryMode ? "binary" : "text";
        String yesNo = readInputLine(String.format("Switch from current mode of %s (y/n)?: ", curMode));
        if (yesNo.equalsIgnoreCase("y")) {
            _binaryMode = !_binaryMode;
            _builder.setBinaryMode(_binaryMode);
            _models = null;
        }
    }

    private void pruneModels() {
        if (_models.isEmpty()) {
            System.err.println("Models must be built or loaded first before pruning");
            return;
        }
        
        String minNGramCountAsStr = readInputLine("Enter minimum normalized ngram count: ");
        if (minNGramCountAsStr.trim().isEmpty()) {
            return;
        }
        
        int minNGramCount = Integer.parseInt(minNGramCountAsStr);
        
        int totalPruned = 0;
        for (BaseLanguageModel model : _models) {
            totalPruned += model.prune(minNGramCount);
        }
        
        System.out.println(String.format("Pruned %d ngrams by setting min count to 20", totalPruned));
    }
    
    private void loadModels() throws IOException {
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
        
        for (File file : FileUtils.listFiles(dirFile, new String[]{"txt", "bin"}, true)) {
            String filename = file.getName();
            Pattern p = Pattern.compile(String.format("yalder_model_(.+).%s", modelSuffix));
            Matcher m = p.matcher(filename);
            if (!m.matches()) {
                continue;
            }

            // Verify that language is valid
            LanguageLocale.fromString(m.group(1));
            
            // Figure out if it's binary or text
            BaseLanguageModel model = null;
            if (isBinary) {
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                HashLanguageModel binaryModel = new HashLanguageModel();
                binaryModel.readAsBinary(dis);
                dis.close();
                
                model = binaryModel;
            } else {
                InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8");
                TextLanguageModel textModel = new TextLanguageModel();
                textModel.readAsText(isr);
                isr.close();

                model = textModel;
            }

            newModels.add(model);
        }

        if (newModels.isEmpty()) {
            System.out.println("No models were loaded, keeping current models (if any)");
            return;
        }
        
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
        File modelDir = new File(dirFile, "models");
        if (modelDir.exists()) {
            FileUtils.deleteDirectory(modelDir);
        }
        modelDir.mkdirs();
        
        // Now set up the two sub-dirs, core and extras.
        File coreDir = new File(modelDir, "core");
        if (coreDir.exists()) {
            FileUtils.deleteDirectory(coreDir);
        }
        coreDir.mkdir();
        
        File extrasDir = new File(modelDir, "extras");
        if (extrasDir.exists()) {
            FileUtils.deleteDirectory(extrasDir);
        }
        extrasDir.mkdir();

        String modelSuffix = _binaryMode ? "bin" : "txt";

        for (BaseLanguageModel baseModel : _models) {
            String languageCode = baseModel.getLanguage().getName();
            String modelFileName = String.format("yalder_model_%s.%s", languageCode, modelSuffix);
            
            
            File modelFile = null;
            
            if (CORE_LANGUAGES.contains(languageCode)) {
                modelFile = new File(coreDir,  modelFileName);
            } else {
                modelFile = new File(extrasDir,  modelFileName);
            }

            if (_binaryMode) {
                HashLanguageModel model = (HashLanguageModel)baseModel;
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(modelFile));
                model.writeAsBinary(dos);
                dos.close();
            } else {
                TextLanguageModel model = (TextLanguageModel)baseModel;
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
                String cmdName = readInputLine("Enter command (mode, data, build, load, prune, save, test, euro, quit): ");
                if (cmdName.equalsIgnoreCase("data")) {
                    tool.loadTrainingData();
                } else if (cmdName.equalsIgnoreCase("mode")) {
                    tool.changeMode();
                } else if (cmdName.equalsIgnoreCase("build")) {
                    tool.buildModels();
                } else if (cmdName.equalsIgnoreCase("save")) {
                    tool.saveModels();
                } else if (cmdName.equalsIgnoreCase("load")) {
                    tool.loadModels();
                } else if (cmdName.equalsIgnoreCase("prune")) {
                    tool.pruneModels();
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
    private static String readInputLine(String prompt) {
        System.out.print(prompt);
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        try {
            return br.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Impossible error", e);
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    private static class ModelBuilderOptions {
        
        private static final int DEFAULT_MAX_NGRAM_LENGTH = 4;
        
        private static final double DEFAULT_MIN_NGRAM_FREQUENCY = ModelBuilder.DEFAULT_MIN_NGRAM_FREQUENCY;
        

        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
        // TODO support multiple input files and directories.
        private String _inputDir = null;
        private String _inputFile = null;
        
        private int _maxNGramLength = DEFAULT_MAX_NGRAM_LENGTH;
        private double _minNGramFrequency = DEFAULT_MIN_NGRAM_FREQUENCY;
        
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
        
        @Option(name = "-minngramfreq", usage = "minimum frequency of ngrams to be included in model", required = false)
        public void setMinNGramFrequency(double minNGramFrequency) {
            _minNGramFrequency = minNGramFrequency;
        }

        public double getMinNGramFrequency() {
            return _minNGramFrequency;
        }
    }
}
