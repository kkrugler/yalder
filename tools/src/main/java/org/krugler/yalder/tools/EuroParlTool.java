package org.krugler.yalder.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.krugler.yalder.LanguageLocale;

public class EuroParlTool {
    public static final Logger LOGGER = Logger.getLogger(EuroParlTool.class);

    private EuroParlOptions _options;
    
    public EuroParlTool(EuroParlOptions options) {
        _options = options;
    }
    
    /**
     * Given a directory of EU parliamentary sample text, for each file we find we want to extract the actual
     * text snippets and write to separate output files, one for each language.
     * 
     * @throws IOException
     */
    private void run() throws IOException {
        File inputDir = new File(_options.getInputDir());
        if (!inputDir.exists()) {
            throw new IllegalArgumentException(String.format("The input directory '%s' doesn't exist", inputDir.toString()));
        }
        
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
        }
        
        File outputDir = new File(_options.getOutputDir());
        outputDir.mkdirs();
        
        Pattern dirnamePattern = Pattern.compile("(..)");
        OutputStreamWriter osw = null;
        
        if (_options.isMerged()) {
            File outputFile = new File(outputDir, "Europarl_merged.txt");
            osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        }
        
        try {
            for (File languageSubdir : inputDir.listFiles()) {
                if (languageSubdir.getName().equals(".DS_Store")) {
                    continue;
                }
                
                if (!languageSubdir.isDirectory()) {
                    LOGGER.warn("Got file in EuroParl input directory: " + languageSubdir);
                    continue;
                }
                
                Matcher m = dirnamePattern.matcher(languageSubdir.getName());
                if (!m.matches()) {
                    LOGGER.warn("Got subdir in EuroParl input directory with invalid language name: " + languageSubdir);
                    continue;
                }
                
                String epLanguage = m.group(1);
                LanguageLocale ll = LanguageLocale.fromString(epLanguage);
                
                if (!_options.isMerged()) {
                    File outputFile = new File(outputDir, String.format("Europarl_%s.txt", ll.getName()));
                    osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
                }
                
                System.out.println(String.format("Loading text lines from files in '%s'...", languageSubdir.getCanonicalPath()));
                
                for (File languageFile : FileUtils.listFiles(languageSubdir, new String[]{"txt"}, false)) {
                    extractText(languageFile, ll.getName(), osw);
                }
                
                if (!_options.isMerged()) {
                    osw.close();
                    osw = null;
                }
            }

        } finally {
            IOUtils.closeQuietly(osw);
        }
    }
    
    
    private void extractText(File languageFile, String language, OutputStreamWriter osw) throws IOException {
        double percentage = _options.getPercentage();
        int linesPerLanguage = _options.getLinesPerLanguage();
        
        List<String> lines = FileUtils.readLines(languageFile, "UTF-8");
        Collections.shuffle(lines);
        
        if (linesPerLanguage == 0) {
            if (percentage == 0.0) {
                percentage = EuroParlOptions.DEFAULT_PERCENTAGE;
            }
            
            linesPerLanguage = (int)Math.round(lines.size() * percentage);
        }
        
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("<")) {
                // Ignore <CHAPTER> and <SPEAKER> tags.
                continue;
            }
            
            // Get rid of (blah) text, if it exists.
            line = line.replaceAll("\\(.+\\)", "");
            
            // Get rid of boilerplate at end of sentences, e.g. <blah>: See minutes
            line = line.replaceFirst("\\:.{1,20}$", "");
            
            line = line.trim();
            
            if (line.length() < 20) {
                // Get rid of really short lines, like just '.'
                continue;
            }
            
            osw.write(language);
            osw.write('\t');
            osw.write(line);
            osw.write('\n');
            
            linesPerLanguage -= 1;
            if (linesPerLanguage <= 0) {
                break;
            }
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    public static void main(String[] args) {
        EuroParlOptions options = new EuroParlOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
            
            if ((options.getPercentage() > 0.0) && (options.getLinesPerLanguage() > 0)) {
                throw new IllegalArgumentException("You cannot specify both a sample percentage and a target lines per language");
            }
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        EuroParlTool tool = new EuroParlTool(options);
        
        try {
            tool.run();
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }

    }

    private static class EuroParlOptions {
        
        public static final double DEFAULT_PERCENTAGE = 0.001;
        
        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
        private String _inputDir = null;
        private String _outputDir = null;
        private double _percentage = 0.00;
        private boolean _merged = false;
        private int _linesPerLanguage = 0;
        
        @Option(name = "-debug", usage = "debug logging", required = false)
        public void setDebugLogging(boolean debugLogging) {
            _debugLogging = debugLogging;
        }

        @Option(name = "-trace", usage = "trace logging", required = false)
        public void setTraceLogging(boolean traceLogging) {
            _traceLogging = traceLogging;
        }

        @Option(name = "-inputdir", usage = "input directory", required = true)
        public void setIputDir(String inputDir) {
            _inputDir = inputDir;
        }

        public String getInputDir() {
            return _inputDir;
        }

        @Option(name = "-outputdir", usage = "output directory", required = true)
        public void setOutputDir(String outputDir) {
            _outputDir = outputDir;
        }

        public String getOutputDir() {
            return _outputDir;
        }

        @Option(name = "-percent", usage = "percentage of lines to extract", required = false)
        public void setPercentage(double percentage) {
            _percentage = percentage;
        }

        public double getPercentage() {
            return _percentage;
        }

        @Option(name = "-lpl", usage = "lines per language to extract", required = false)
        public void setLinesPerLanguage(int linesPerLanguage) {
            _linesPerLanguage = linesPerLanguage;
        }

        public int getLinesPerLanguage() {
            return _linesPerLanguage;
        }

        @Option(name = "-merged", usage = "create a single merged file", required = false)
        public void setMerged(boolean merged) {
            _merged = merged;
        }

        public boolean isMerged() {
            return _merged;
        }

    }
}
