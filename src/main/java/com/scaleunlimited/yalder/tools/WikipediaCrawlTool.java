package com.scaleunlimited.yalder.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.InvalidXPathException;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bixo.config.UserAgent;
import bixo.exceptions.BaseFetchException;
import bixo.fetcher.FetchedResult;
import bixo.fetcher.SimpleHttpFetcher;

public class WikipediaCrawlTool {

    private static final String XML_SUBDIR_NAME = "xml";
    private static final String TEXT_SUBDIR_NAME = "text";
    
    // Map from Wikipedia language name to ISO 639-2 language code.
    private static final Map<String, String> WIKIPEDIA_TO_ISO_LANGUAGE = new HashMap<String, String>();
    static {
        WIKIPEDIA_TO_ISO_LANGUAGE.put("en", "eng");
    }
    
    private WikipediaCrawlOptions _options;
    private Document _curPage = null;
    private SimpleHttpFetcher _fetcher;
    
    public WikipediaCrawlTool(WikipediaCrawlOptions options) {
        _options = options;
        
        _fetcher = new SimpleHttpFetcher(new UserAgent("yalder", "ken@transpac.com", "http://ken-blog.krugler.org"));
        // Some pages are > 1MB is size, yikes!
        _fetcher.setDefaultMaxContentSize(10 * 1024 * 1024);
        
    }
    
    private void displayHelp() {
        System.out.println("help - print this help text");
        System.out.println("fetch - load HTML document from file or URL");
        System.out.println("dump - dump previously loaded HTML document");
        System.out.println("clean - dump text extracted from previously loaded HTML document");
        System.out.println("crawl - crawl Wikipedia pages, given a list of (English) titles");
        System.out.println("xpath - evaluate XPath expressions using previously loaded HTML document");
        System.out.println("merge - generate one document from fetched content");
        System.out.println("quit - quit");
    }
    
    private Document fetchPage(String docName) throws IOException, DocumentException, BaseFetchException {
        if (docName == null) {
            docName = readInputLine("Enter HTML document file path or URL: ");
        }
        
        if (docName.length() == 0) {
            return null;
        }
        
        InputStream is;
        
        if (docName.startsWith("http://") || docName.startsWith("https://")) {
            FetchedResult result = _fetcher.fetch(docName);
            is = new ByteArrayInputStream(result.getContent());
        } else {
            is = new FileInputStream(docName);
        }
        
        HtmlParser parser = new HtmlParser("UTF-8");
        return parser.parse(is);
    }
    
    private void doDump() throws IOException {
        if (_curPage == null) {
            System.out.println("Can't dump the page if it hasn't been loaded yet!");
            return;
        }
        
        String filename = readInputLine("Enter output file path: ");
        if (filename.length() == 0) {
            System.out.println(formatXml(_curPage));
        } else {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
            osw.write(formatXml(_curPage));
            osw.close();
        }
    }

    private void doClean() throws IOException {
        if (_curPage == null) {
            System.out.println("Can't output clean content for the page if it hasn't been loaded yet!");
            return;
        }
        
        String filename = readInputLine("Enter output file path: ");
        if (filename.length() == 0) {
            System.out.println(extractContent(_curPage));
        } else {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
            osw.write(extractContent(_curPage));
            osw.close();
        }
    }

    private void mergeResults() throws IOException {
        String dirname = readInputLine("Enter input directory path: ");

        File inputDir = new File(dirname);
        if (!inputDir.exists()) {
            throw new IllegalArgumentException(String.format("The directory '%s' doesn't exist", inputDir.toString()));
        }
        
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
        }
        
        String outputFilename = readInputLine("Enter ouput file path: ");
        
        File outputFile = new File(outputFilename);
        outputFile.delete();
        
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        
        System.out.println(String.format("Loading text lines from files in '%s'...", inputDir.getCanonicalPath()));

        try {
            for (File file : FileUtils.listFiles(inputDir, new String[]{"txt"}, true)) {
                String filename = file.getName();
                Pattern p = Pattern.compile("(.+)_(.+).txt");
                Matcher m = p.matcher(filename);
                if (!m.matches()) {
                    throw new IllegalArgumentException(String.format("Found file '%s' without a language code", filename));
                }

                String language = m.group(2);
                List<String> lines = FileUtils.readLines(file, "UTF-8");
                for (String line : lines) {
                    line = line.replaceAll("[ \t]+", " ");
                    line = line.trim();
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    osw.write(language);
                    osw.write('\t');
                    osw.write(line);
                    osw.write('\n');
                }
            }
        } finally {
            osw.close();
        }
    }
    
    private void saveWikipediaPage(Document doc, File parentFolder, String topicName, String language) throws IOException {
        // Write out the original content, as XML, so we could potentially re-process it
        File xmlDir = new File(parentFolder, XML_SUBDIR_NAME);
        xmlDir.mkdirs();
        
        File xmlFile = new File(xmlDir, String.format("%s_%s.xml", topicName, language));
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8");
        osw.write(formatXml(doc));
        osw.close();

        File textDir = new File(parentFolder, TEXT_SUBDIR_NAME);
        textDir.mkdirs();

        // Now save the main content
        File textFile = new File(parentFolder, String.format("%s_%s.txt", topicName, language));
        osw = new OutputStreamWriter(new FileOutputStream(textFile), "UTF-8");
        osw.write(extractContent(doc));
        osw.close();
    }
    
    private String extractContent(Document doc) {
        StringBuffer mainContent = new StringBuffer();
        List<Node> nodes = selectNodes(doc, "//div[@id=\"mw-content-text\"]");
        if (nodes.size() != 1) {
            throw new IllegalStateException("No mw-content-text node in document");
        }
        
        nodes = getChildrenFromNode(nodes.get(0));
        for (Node node : nodes) {
            String nodeType = node.getName();
            if (nodeType == null) {
                continue;
            }
            
            if (nodeType.equalsIgnoreCase("p") || nodeType.equalsIgnoreCase("div")) {
                Element e = (Element)node;
                String id = e.attributeValue("id");
                if (nodeType.equalsIgnoreCase("div") && (id != null) && (id.equals("toc"))) {
                    // Skip the table of contents
                    continue;
                }

                String nodeText = getTextFromNode(node, true);
                if (nodeText == null) {
                    // We hit something that tells us we're at the end of the actual content.
                    break;
                }
                
                mainContent.append(nodeText);
                mainContent.append("\n");
            }
        }
        
        return mainContent.toString();
    }

    private void doCrawl() throws IOException, DocumentException, BaseFetchException {
        String qCodeOrCount = readInputLine("Enter Q-code, or number to randomly select from our important list");
        if (qCodeOrCount.trim().isEmpty()) {
            return;
        }
        
        List<String> qCodes = new ArrayList<String>();
        if (qCodeOrCount.startsWith("Q")) {
            qCodes.add(qCodeOrCount);
        } else {
            int numToProcess = Integer.parseInt(qCodeOrCount);
            List<String> importantQCodes = IOUtils.readLines(WikipediaCrawlTool.class.getResourceAsStream("/wikipedia-key-articles.txt"));
            Collections.shuffle(importantQCodes);
            qCodes = importantQCodes.subList(0, numToProcess);
        }
        
        String directoryName = readInputLine("Enter output directory: ");
        if (directoryName.length() == 0) {
            return;
        }
        
        File parentFolder = new File(directoryName);
        if (!parentFolder.exists()) {
            System.err.println("Output directory must exist");
            return;
        }
        if (!parentFolder.isDirectory()) {
            System.err.println("Output path specified isn't a directory");
            return;
        }
        
        for (String qCode : qCodes) {
            Document page = fetchPage("https://www.wikidata.org/wiki/" + qCode);
            List<Node> nodes = selectNodes(page, "//span[@class=\"wikibase-title-label\"]");
            if (nodes.size() != 1) {
                throw new IllegalArgumentException(String.format("Wikidata page %s doesn't have a title label", qCode));
            }
            
            String topic = nodes.get(0).getText();
            topic = topic.replaceAll(" ", "_");
            List<String> pageURLs = getWikiPagesFromWikidata(page);
            
            for (String pageURL : pageURLs) {
                // TODO extract language from URL, map it to our 639-2 code.
                // https://af.wikipedia.org/wiki/Vincent_van_Gogh
                Pattern urlPattern = Pattern.compile("https://(.+?).wikipedia.org/.+");
                Matcher m = urlPattern.matcher(pageURL);
                String language = WIKIPEDIA_TO_ISO_LANGUAGE.get(m.group(1));
                Document subPage = fetchPage(pageURL);
                saveWikipediaPage(subPage, parentFolder, topic, language);
            }
        }
        

//        for (String topic : topics) {
//            Document topicPage = fetchPage("https://en.wikipedia.org/wiki/" + topic);
//            
//            saveWikipediaPage(topicPage, parentFolder, topic, "eng");
//            
//            // Now extract links to all of the other pages, and crawl those as well
//            List<Node> nodes = selectNodes(_curPage, "//li[contains(concat(' ', @class, ' '), ' interlanguage-link ')]");
//            for (Node node : nodes) {
//                Element link = (Element)node.selectSingleNode("./a");
//                String language = link.attributeValue("lang");
//                
//                String isoCode = mapWikipediaLanguage(language);
//                if (isoCode == null) {
//                    // Not one of the languages we want
//                    continue;
//                }
//                
//                String url = link.attributeValue("href");
//                if (url.startsWith("//")) {
//                    url = "https:" + url;
//                }
//                
//                Document subPage = fetchPage(url);
//                saveWikipediaPage(subPage, parentFolder, topic, language);
//            }
//        }
    }
    
    private List<String> getWikiPagesFromWikidata(Document page) throws IOException, DocumentException, BaseFetchException {
        
        List<String> result = new ArrayList<String>();
        List<Node> nodes = selectNodes(page, "//div[@data-wb-sitelinks-group=\"wikipedia\"]//span[@class=\"wikibase-sitelinkview-page\"]/a");
        for (Node node : nodes) {
            Element link = (Element)node;
            String language = link.attributeValue("hreflang");
            if (WIKIPEDIA_TO_ISO_LANGUAGE.containsKey(language)) {
                String url = link.attributeValue("href");
                result.add(url);
            }
        }

        return result;
    }

    private String mapWikipediaLanguage(String language) {
        // TODO Auto-generated method stub
        return null;
    }

    private void doXpath() throws IOException {
        if (_curPage == null) {
            System.out.println("Can't query the page if it hasn't been loaded yet!");
            return;
        }

        // Now loop, getting XPath expressions and evaluating them
        while (true) {
            System.out.println();
            System.out.flush();

            String xpathExpression = readInputLine("Enter XPath expression (or <return> when done): ");
            if (xpathExpression.length() == 0) {
                break;
            }

            try {
                List<Node> nodes = selectNodes(_curPage, xpathExpression);
                System.out.println(String.format("%d nodes", nodes.size()));
                System.out.flush();
                
                while (true) {
                    String xpathCmd = readInputLine("What next (a, f, e, t): ");
                    if (xpathCmd.length() == 0) {
                        xpathCmd = "e";
                    }

                    if (xpathCmd.equalsIgnoreCase("e")) {
                        break;
                    } else if (xpathCmd.equalsIgnoreCase("a")) {
                        for (Node node : nodes) {
                            System.out.println(formatXml(node));
                        }
                    } else if (xpathCmd.equalsIgnoreCase("t")) {
                        for (Node node : nodes) {
                            System.out.println(getTextFromNode(node, true));
                        }
                    } else if (xpathCmd.equalsIgnoreCase("f")) {
                        if (nodes.isEmpty()) {
                            System.out.println("No first node!");
                        } else {
                            System.out.println(formatXml(nodes.get(0)));
                            System.out.println("=======================");
                            System.out.println(nodes.get(0).getText().trim());
                        }
                    } else {
                        System.out.println("Unknown command: " + xpathCmd);
                    }
                }
            } catch (InvalidXPathException e) {
                System.out.println(e.getMessage());
            } catch (Exception e) {
                System.out.println("Error executing query: " + e.getMessage());
                e.printStackTrace(System.out);
            }
        }

    }
    
    public static String getTextFromNode(Node node, boolean stripReturns) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            String result = node.getText().trim();
            if (stripReturns) {
                return result.replaceAll("[\n\r]", " ");
            } else {
                return result;
            }
        } else if (node.getName().equalsIgnoreCase("br")) {
            return "\n";
        } else if (node.getName().equalsIgnoreCase("ol")) {
            Element e = (Element)node;
            String className = e.attributeValue("class");
            if ((className != null) && (className.equals("references"))) {
                // We've hit the end of the main content, so exclude everything
                // from the top-level node down to this point too, by returning null.
                return null;
            }
        }
        
        StringBuilder result = new StringBuilder();
        List<Node> children = getChildrenFromNode(node);
        for (Node child : children) {
            String text = getTextFromNode(child, stripReturns);
            if (text == null) {
                return null;
            }
            
            result.append(text);
            // Put space between elements.
            result.append(' ');
        }
        
        return result.toString();
    }
    
    public static List<Node> getChildrenFromNode(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        
        Element e = (Element)node;
        List<Node> result = new ArrayList<Node>(e.nodeCount());
        
        Iterator<Node> iter = e.nodeIterator();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        
        return result;
    }


    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    public static void main(String[] args) {
        WikipediaCrawlOptions options = new WikipediaCrawlOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        WikipediaCrawlTool tool = new WikipediaCrawlTool(options);
        
        try {
            
            // Now loop, getting commands
            while (true) {
                String cmdName = readInputLine("Enter command (help, fetch, dump, clean, xpath, crawl, merge, quit): ");
                if (cmdName.equalsIgnoreCase("help")) {
                    tool.displayHelp();
                } else if (cmdName.equalsIgnoreCase("fetch")) {
                    Document newDoc = tool.fetchPage(null);
                    if (newDoc != null) {
                        tool.setCurPage(newDoc);
                    }
                } else if (cmdName.equalsIgnoreCase("dump")) {
                    tool.doDump();
                } else if (cmdName.equalsIgnoreCase("clean")) {
                    tool.doClean();
                } else if (cmdName.equalsIgnoreCase("xpath")) {
                    tool.doXpath();
                } else if (cmdName.equalsIgnoreCase("crawl")) {
                    tool.doCrawl();
                } else if (cmdName.equalsIgnoreCase("merge")) {
                    tool.mergeResults();
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

    private void setCurPage(Document newDoc) {
        _curPage = newDoc;
    }

    /**
     * Read one line of input from the console, after displaying prompt
     * 
     * @return Text that the user entered
     * @throws IOException
     */
    private static String readInputLine(String prompt) throws IOException {
        System.out.print(prompt);
        return readInputLine();
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

    /**
     * Returns the given xml document as nicely formated string.
     * 
     * @param node
     *            The xml document.
     * @return the formated xml as string.
     */
    private static String formatXml(Node node) {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setIndentSize(4);
        format.setTrimText(true);
        format.setExpandEmptyElements(true);
        
        StringWriter stringWriter = new StringWriter();
        XMLWriter xmlWriter = new XMLWriter(stringWriter, format);
        try {
            xmlWriter.write(node);
            xmlWriter.flush();
        } catch (IOException e) {
            // this should never happen
            throw new RuntimeException(e);
        }

        return stringWriter.getBuffer().toString();
    }

    private static List<Node> selectNodes(Document doc, String xpathExpression) {
        return doc.selectNodes(xpathExpression);
    }
    
    private static class WikipediaCrawlOptions {
        
        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
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
        
        @Option(name = "-inputfile", usage = "input HTML file", required = false)
        public void setInputFile(String inputFile) {
            _inputFile = inputFile;
        }

        public String getInputFile() {
            return _inputFile;
        }

    }
}
