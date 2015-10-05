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
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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

import com.scaleunlimited.yalder.LanguageLocale;

import bixo.config.UserAgent;
import bixo.exceptions.BaseFetchException;
import bixo.fetcher.FetchedResult;
import bixo.fetcher.SimpleHttpFetcher;

public class WikipediaCrawlTool {
    public static final Logger LOGGER = Logger.getLogger(WikipediaCrawlTool.class);

    private static final String XML_SUBDIR_NAME = "xml";
    private static final String TEXT_SUBDIR_NAME = "text";
    
    // Map from Wikipedia language name to ISO 639-2 language code.
    // Use mapWikipediaLanguage() to get the mapping, as that loads the data.
    private static final Map<String, String> WIKIPEDIA_TO_ISO_LANGUAGE = new HashMap<String, String>();
    
    private WikipediaCrawlOptions _options;
    private Document _curPage = null;
    private SimpleHttpFetcher _fetcher;
    private Map<String, Long> _lastFetchTime;
    
    public WikipediaCrawlTool(WikipediaCrawlOptions options) {
        _options = options;
        
        _fetcher = new SimpleHttpFetcher(new UserAgent("yalder", "ken@transpac.com", "http://ken-blog.krugler.org"));
        // Some pages are > 1MB is size, yikes!
        _fetcher.setDefaultMaxContentSize(10 * 1024 * 1024);
        
        _lastFetchTime = new HashMap<>();
    }
    
    private void displayHelp() {
        System.out.println("help - print this help text");
        System.out.println("load - load Wikipedia page from file or URL");
        System.out.println("dump - dump previously loaded page (as XML)");
        System.out.println("clean - dump text extracted from previously loaded page");
        System.out.println("crawl - crawl Wikipedia pages, given a list of Q-codes");
        System.out.println("random - crawl random Wikipedia pages, given a list of language codes");
        System.out.println("xpath - evaluate XPath expressions using previously loaded HTML document");
        System.out.println("quit - quit");
    }
    
    private Document loadDocument(String docName) throws IOException, DocumentException, BaseFetchException {
        if (docName == null) {
            docName = readInputLine("Enter HTML document file path or URL: ");
        }
        
        if (docName.length() == 0) {
            return null;
        }
        
        InputStream is;
        
        if (docName.startsWith("http://") || docName.startsWith("https://")) {
            FetchedResult result = fetchPage(docName);
            is = new ByteArrayInputStream(result.getContent());
        } else {
            is = new FileInputStream(docName);
        }
        
        HtmlParser parser = new HtmlParser("UTF-8");
        return parser.parse(is);
    }
    
    private FetchedResult fetchPage(String pageURL) throws IOException, DocumentException, BaseFetchException {
        URL url = new URL(pageURL);
        String domain = url.getHost();
        Long lastFetchTime = _lastFetchTime.get(domain);
        if (lastFetchTime == null) {
            lastFetchTime = 0L;
        }
        
        long curTime = System.currentTimeMillis();
        long delay = (lastFetchTime + 1000L) - curTime;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // Ignore interrupted exception.
            }
        }
        
        FetchedResult result = _fetcher.fetch(pageURL);
        _lastFetchTime.put(domain, System.currentTimeMillis());
        
        return result;
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

    /**
     * Given a crawl output directory (which has a text subdir), for each file we find we want
     * to extract and clean up the resulting text.
     * 
     * @throws IOException
     */
    private void mergeResults() throws IOException {
        String dirname = readInputLine("Enter crawl directory path: ");

        File inputDir = new File(dirname);
        if (!inputDir.exists()) {
            throw new IllegalArgumentException(String.format("The directory '%s' doesn't exist", inputDir.toString()));
        }
        
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
        }
        
        File textDir = new File(inputDir, TEXT_SUBDIR_NAME);
        if (!textDir.exists()) {
            throw new IllegalArgumentException(String.format("The directory '%s' doesn't exist", inputDir.toString()));
        }
        
        if (!textDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a directory", inputDir.toString()));
        }

        String outputFilename = readInputLine("Enter ouput file path: ");
        File outputFile = new File(outputFilename);
        outputFile.delete();
        
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        
        System.out.println(String.format("Loading text lines from files in '%s'...", textDir.getCanonicalPath()));
        
        Pattern filenamePattern = Pattern.compile("(.+)_(.+).txt");

        try {
            for (File file : FileUtils.listFiles(textDir, new String[]{"txt"}, true)) {
                String filename = file.getName();
                Matcher m = filenamePattern.matcher(filename);
                if (!m.matches()) {
                    throw new IllegalArgumentException(String.format("Found file '%s' without a language code", filename));
                }

                String language = m.group(2);
                List<String> lines = FileUtils.readLines(file, "UTF-8");
                for (String line : lines) {
                    // Get rid of [ <digits> ] sequence (where there may or may not be spaces), as that's a footnote ref
                    // that is just noise.
                    line = line.replaceAll("\\[[ ]*\\d+[ ]*\\]", " ");
                    
                    // We wind up with a lot of multi-space sequences, due to markup in text always getting converted to
                    // text by inserting a space.
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
    
    private int saveWikipediaPage(Document doc, File parentFolder, String topicName, String language, boolean overwrite) throws IOException {
        String content = extractContent(doc);
        
        // Don't bother saving super-short pages.
        if (content.length() < 100) {
            return 0;
        }
        
        // Write out the original content, as XML, so we could potentially re-process it
        File xmlDir = new File(parentFolder, XML_SUBDIR_NAME);
        xmlDir.mkdirs();
        
        File xmlFile = new File(xmlDir, String.format("%s_%s.xml", topicName, language));
        if (overwrite || !xmlFile.exists()) {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8");
            osw.write(formatXml(doc));
            osw.close();
        }
        
        File textDir = new File(parentFolder, TEXT_SUBDIR_NAME);
        textDir.mkdirs();

        // Now save the main content
        File textFile = new File(textDir, String.format("%s_%s.txt", topicName, language));
        if (overwrite || !textFile.exists()) {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(textFile), "UTF-8");
            osw.write(content);
            osw.close();
            
            return content.length();
        } else {
            return 0;
        }
        
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

    private void doRandomCrawl() throws IOException, DocumentException, BaseFetchException {
        String langCodes = readInputLine("Enter a comma-separated list of ISO 639-2 language codes: ");
        if (langCodes.trim().isEmpty()) {
            return;
        }

        String numCharsAsStr = readInputLine("Enter target number of chars per language: ");
        if (numCharsAsStr.trim().isEmpty()) {
            return;
        }
        int targetChars = Integer.parseInt(numCharsAsStr);
        
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
        
        for (String langCode : langCodes.split(",")) {
            langCode = langCode.trim();
            
            // Figure out the Wikipedia language. First do a bogus call to load up
            // the data.
            String wikiLang = mapISOLangToWikiLang(langCode);
            if (wikiLang == null) {
                System.err.println("Unknown ISO 639-2 lang code: " + langCode);
                return;
            }
            
            int totalPages = 0;
            int numCharsExtracted = 0;
            while ((numCharsExtracted < targetChars) && (totalPages < 100)) {
                // Get another random page.
                String pageURL = String.format("https://%s.wikipedia.org/wiki/Special:Random", wikiLang);
                Document subPage = loadDocument(pageURL);
                String topic = extractTopicName(subPage);
                topic = URLDecoder.decode(topic, "UTF-8");
                
                int numChars = saveWikipediaPage(subPage, parentFolder, topic, langCode, false);
                if (numChars > 0) {
                    LOGGER.debug(String.format("Fetched topic '%s' with %d chars for language '%s'", topic, numChars, langCode));
                    numCharsExtracted += numChars;
                }
                
                totalPages += 1;
            }
        }
    }
    
    /**
     * Try to extract the topic name from this page, by first finding the English URL. If we
     * can't find that, then extract the head/title node, split that on " - ", and take the
     * first part.
     * 
     * @param wikipediaPage
     * @return
     */
    private String extractTopicName(Document wikipediaPage) {
        // See if there's a link to the English version.
        List<Node> nodes = selectNodes(wikipediaPage, "//li[contains(concat(' ', @class, ' '), ' interwiki-en ')]/a");
        if (nodes.size() == 1) {
            String href = ((Element)nodes.get(0)).attributeValue("href");
            if (href != null) {
                Pattern p = Pattern.compile("en.wikipedia.org/wiki/(.+)");
                Matcher m = p.matcher(href);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        
        nodes = selectNodes(wikipediaPage, "//title");
        if (nodes.size() != 1) {
            return "unknown";
        } else {
            String title = nodes.get(0).getText();
            // Some Wikipedias use a long dash in the title...why?
            return title.split(" (\\-|—) ")[0];
        }
    }

    private void doCrawl() throws IOException, DocumentException, BaseFetchException {
        String qCodeOrCount = readInputLine("Enter a specific Q-code, or number of these to randomly select, or all: ");
        if (qCodeOrCount.trim().isEmpty()) {
            return;
        }
        
        int targetCharsPerLanguage = Integer.MAX_VALUE;
        int maxCharsPerPage = Integer.MAX_VALUE;
        
        Map<String, Integer> charsPerLanguage = new HashMap<>();
        Set<String> completedLanguages = new HashSet<>();
        
        List<String> qCodes = new ArrayList<String>();
        if (qCodeOrCount.startsWith("Q")) {
            qCodes.add(qCodeOrCount);
        } else if (qCodeOrCount.equals("all")) {
            qCodes = readImportantQCodes();
            Collections.shuffle(qCodes);
            
            String targetAsStr = readInputLine("Enter target number of characters per language: ");
            targetCharsPerLanguage = Integer.parseInt(targetAsStr);
            
            String perPageLimitAsStr = readInputLine("Enter max number of characters per page: ");
            if (perPageLimitAsStr.isEmpty()) {
                maxCharsPerPage = 10*1024;
            } else {
                maxCharsPerPage = Integer.parseInt(perPageLimitAsStr);
            }
        } else {
            int numToProcess = Integer.parseInt(qCodeOrCount);
            List<String> importantQCodes = readImportantQCodes();
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
        
        Pattern urlPattern = Pattern.compile("https://(.+?).wikipedia.org/.+");

        // TODO bail out of this loop if we process N qCodes in a row without adding any text
        // for a language (all that we've seen so far are complete).
        for (String qCode : qCodes) {
            // Fetch the QCode page
            Document page = loadDocument("https://www.wikidata.org/wiki/" + qCode);
            List<Node> nodes = selectNodes(page, "//span[@class=\"wikibase-title-label\"]");
            if (nodes.size() != 1) {
                throw new IllegalArgumentException(String.format("Wikidata page %s doesn't have a title label", qCode));
            }
            
            String topic = nodes.get(0).getText();
            topic = topic.replaceAll(" ", "_");
            List<String> pageURLs = getWikiPagesFromWikidata(page);
            
            for (String pageURL : pageURLs) {
                // extract language from URL, map it to our 639-2 code.
                // URL format is https://<language code>.wikipedia.org/wiki/<topic name>
                Matcher m = urlPattern.matcher(pageURL);
                if (!m.matches()) {
                    LOGGER.error("Got page URL with invalid format:" + pageURL);
                    continue;
                }
                
                String language = mapWikipediaLanguage(m.group(1));
                if (language == null) {
                    LOGGER.warn("Unknown Wikipedia language: " + m.group(1));
                } else if (language.equals("xxx")) {
                    // Ignore, not a language we want to process.
                } else if (completedLanguages.contains(language)) {
                    // Ignore, we have enough text from this language.
                } else {
                    Document subPage = loadDocument(pageURL);
                    int numChars = saveWikipediaPage(subPage, parentFolder, topic, language, true);
                    
                    // When tracking chars per language, limit # we get out of each page so we
                    // don't hit our target (e.g. 100K) with a single page.
                    numChars = Math.min(numChars, maxCharsPerPage);
                    Integer curChars = charsPerLanguage.get(language);
                    if (curChars == null) {
                        curChars = numChars;
                    } else {
                        curChars += numChars;
                    }
                    
                    charsPerLanguage.put(language, curChars);
                    if (curChars >= targetCharsPerLanguage) {
                        completedLanguages.add(language);
                    }
                }
            }
        }
        
        // Print some statistics for languages...
        for (String language : charsPerLanguage.keySet()) {
            System.out.println(String.format("'%s': %d chars", LanguageLocale.fromString(language), charsPerLanguage.get(language)));
        }
    }
    
    private List<String> readImportantQCodes() {
        List<String> result = new ArrayList<>();
        try (InputStream is = WikipediaCrawlTool.class.getResourceAsStream("/wikipedia-key-articles.txt")) {
            List<String> lines =  IOUtils.readLines(is);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                result.add(line);
            }
            
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Impossible exception!", e);
        }
    }

    private List<String> getWikiPagesFromWikidata(Document page) throws IOException, DocumentException, BaseFetchException {
        
        List<String> result = new ArrayList<String>();
        List<Node> nodes = selectNodes(page, "//div[@data-wb-sitelinks-group=\"wikipedia\"]//span[@class=\"wikibase-sitelinkview-page\"]/a");
        for (Node node : nodes) {
            Element link = (Element)node;
            result.add(link.attributeValue("href"));
        }

        return result;
    }
    
    private String mapISOLangToWikiLang(String langCode) {
        loadWikiLangMap();
        
        for (String wikiLang : WIKIPEDIA_TO_ISO_LANGUAGE.keySet()) {
            if (WIKIPEDIA_TO_ISO_LANGUAGE.get(wikiLang).equals(langCode)) {
                return wikiLang;
            }
        }

        return null;
    }

    private void loadWikiLangMap() {
        synchronized (WIKIPEDIA_TO_ISO_LANGUAGE) {
            if (WIKIPEDIA_TO_ISO_LANGUAGE.isEmpty()) {
                try (InputStream is = WikipediaCrawlTool.class.getResourceAsStream("/wikipedia-languages.txt")) {
                    List<String> lines = IOUtils.readLines(is, "UTF-8");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        
                        // Format is rank, language name, Wikipedia language name, ISO 639-2, optional comment
                        // E.g.
                        // 19   Serbo-Croatian  sh  xxx # Ignore obsolete language
                        String[] parts = line.split("\t");
                        if (parts.length < 4) {
                            throw new IllegalArgumentException(String.format("Line '%s' has invalid format", line));
                        }
                        
                        WIKIPEDIA_TO_ISO_LANGUAGE.put(parts[2], parts[3]);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Impossible exception!", e);
                }
            }
        }
    }
    
    private String mapWikipediaLanguage(String language) {
        loadWikiLangMap();
        
        return WIKIPEDIA_TO_ISO_LANGUAGE.get(language);
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
                String cmdName = readInputLine("Enter command (help, load, dump, clean, xpath, crawl, random, merge, quit): ");
                if (cmdName.equalsIgnoreCase("help")) {
                    tool.displayHelp();
                } else if (cmdName.equalsIgnoreCase("load")) {
                    Document newDoc = tool.loadDocument(null);
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
                } else if (cmdName.equalsIgnoreCase("random")) {
                    tool.doRandomCrawl();
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
