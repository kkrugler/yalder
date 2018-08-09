package org.krugler.yalder.tools;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.hash.HashLanguageDetector;
import org.krugler.yalder.hash.HashLanguageModel;

public class TextAnalysisTool {
    public static final Logger LOGGER = Logger.getLogger(TextAnalysisTool.class);

    private static final LanguageLocale ENGLISH = LanguageLocale.fromString("en");

    private HashLanguageDetector _detector;
    
    public TextAnalysisTool() throws IOException {
        Collection<BaseLanguageModel> models = loadModelsFromResource();
        _detector = new HashLanguageDetector(models);
    }
    
    public void processFile(String filename) throws IOException {
        LineIterator iter = IOUtils.lineIterator(FileUtils.openInputStream(new File(filename)), "UTF-8");
        while (iter.hasNext()) {
            String line = iter.next().trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // TODO check options for whether to only classify outside of std. Latin
            Character.UnicodeBlock block = Character.UnicodeBlock.of(line.charAt(0));
            if ((block == Character.UnicodeBlock.BASIC_LATIN) || (block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT)) {
                continue;
            }
            
            _detector.reset();
            _detector.addText(line);
            Collection<DetectionResult> results = _detector.detect();
            if (results.isEmpty()) {
                LOGGER.info("No language: " + line);
            } else {
                Iterator<DetectionResult> langIter = results.iterator();
                DetectionResult first = langIter.next();
                // TODO check options for target language
                if (!first.getLanguage().weaklyEqual(ENGLISH)) {
                    if (langIter.hasNext()) {
                        DetectionResult second = langIter.next();
                        // double scoreDelta = first.getScore() - second.getScore();
                        LOGGER.info(String.format("Best language = %s (%f) vs. %s (%f): %s", first.getLanguage(), first.getScore(), second.getLanguage(), second.getScore(), line));
                    } else {
                        LOGGER.info(String.format("One language = %s: %s", first.getLanguage(), line));
                    }
                }

            }
        }
    }

    public void processDirectory(String dirname) {
        
    }

    public static void main(String[] args) {
        TextAnalysisOptions options = new TextAnalysisOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        
        try {
            TextAnalysisTool tool = new TextAnalysisTool();
            if (options.getInputFile() != null) {
                tool.processFile(options.getInputFile());
            }
            
            if (options.getInputDir() != null) {
                tool.processDirectory(options.getInputDir());
            }
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    // TODO move this into yalder-core, and find resources by name
    public static Collection<BaseLanguageModel> loadModelsFromResource() throws IOException {
        Set<BaseLanguageModel> result = new HashSet<>();
        
        // TODO derive from resource list
        String[] languages = new String[] {
                        "ara",
                        "bul",
                        "cat",
                        "ces",
                        "dan",
                        "deu",
                        "ell",
                        "eng",
                        "est",
                        "fas",
                        "fin",
                        "fra",
                        "heb",
                        "hrv",
                        "hun",
                        "ind",
                        "ita",
                        "kor",
                        "lav",
                        "lit",
                        "nld",
                        "nno",
                        "nob",
                        "pol",
                        "por",
                        "ron",
                        "rus",
                        "slk",
                        "slv",
                        "spa",
                        "srp",
                        "swe",
                        "tha",
                        "tur",
                        "ukr",
                        "vie",
                        "zho"
        };
        
        for (String language : languages) {
            String resourceName = String.format("/org/krugler/yalder/models/core/yalder_model_%s.bin", language);
            try (InputStream is = TextAnalysisTool.class.getResourceAsStream(resourceName)) {
                DataInputStream dis = new DataInputStream(is);
                HashLanguageModel binaryModel = new HashLanguageModel();
                binaryModel.readAsBinary(dis);
                result.add(binaryModel);
            }
        }

        return result;
    }

    private static class TextAnalysisOptions {
        
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
