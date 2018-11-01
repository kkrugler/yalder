package org.krugler.yalder.tools;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.InvalidXPathException;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.krugler.yalder.html.BoilerpipeContentHandler;
import org.krugler.yalder.html.DOM2SAX;
import org.krugler.yalder.html.HtmlParser;
import org.xml.sax.SAXException;

import crawlercommons.fetcher.BaseFetchException;
import crawlercommons.fetcher.FetchedResult;
import crawlercommons.fetcher.http.BaseHttpFetcher;
import crawlercommons.fetcher.http.SimpleHttpFetcher;
import crawlercommons.fetcher.http.UserAgent;

/**
 * Tool for crawling a single web site to collect pages in a target language.
 *
 */
public class OneDomainCrawlTool {
    public static final Logger LOGGER = Logger.getLogger(OneDomainCrawlTool.class);

    private static final String XML_SUBDIR_NAME = "xml";
    private static final String TEXT_SUBDIR_NAME = "text";

    private static final int MIN_PAGE_CONTENT_LENGTH = 200;
    
    private Document _curPage = null;
    private SimpleHttpFetcher _fetcher;
    private long _lastFetchTime;
    
    public OneDomainCrawlTool() {
        
        _fetcher = new SimpleHttpFetcher(new UserAgent("yalder", "ken@transpac.com", "http://ken-blog.krugler.org"));
        // Some pages are > 1MB is size, yikes!
        _fetcher.setDefaultMaxContentSize(10 * 1024 * 1024);
        
        _lastFetchTime = 0;
    }
    
    private void displayHelp() {
        System.out.println("help - print this help text");
        System.out.println("load - load page from URL");
        System.out.println("dump - dump previously loaded page (as XML)");
        System.out.println("clean - dump text extracted from previously loaded page");
        System.out.println("crawl - crawl pages, given a target domain");
        System.out.println("xpath - evaluate XPath expressions using previously loaded HTML document");
        System.out.println("merge - merge files in text sub-dir into one new file");
        System.out.println("quit - quit");
    }
    
    private void loadDocument() throws IOException, DocumentException, BaseFetchException {
        String docName = readInputLine("Enter HTML document file path or URL: ");
        
        if (docName.length() == 0) {
            return;
        }
        
        String acceptLanguage = null;
        if (docName.startsWith("http:") || docName.startsWith("https:")) {
            acceptLanguage = readInputLine("Enter \"Accept-Language\" for request: ");
            if (acceptLanguage.isEmpty()) {
                acceptLanguage = null;
            }
        }
        
        Document doc = loadAndParseHTML(docName, acceptLanguage);
        setCurPage(doc);
    }        

    private Document loadAndParseHTML(String docName, String acceptLanguage) throws IOException, DocumentException, BaseFetchException {
        InputStream is;
        
        if (docName.startsWith("http:") || docName.startsWith("https:")) {
            FetchedResult result = fetchPage(docName, acceptLanguage);
            is = new ByteArrayInputStream(result.getContent());
        } else {
            is = new FileInputStream(docName);
        }
        
        HtmlParser parser = new HtmlParser("UTF-8");
        return parser.parse(is);
    }
    
    private FetchedResult fetchPage(String pageURL, String acceptLanguage) throws IOException, DocumentException, BaseFetchException {
        long curTime = System.currentTimeMillis();
        long delay = (_lastFetchTime + 1000L) - curTime;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // Ignore interrupted exception.
            }
        }
        
        if (acceptLanguage != null) {
            _fetcher.setAcceptLanguage(acceptLanguage);
        } else {
            _fetcher.setAcceptLanguage(BaseHttpFetcher.DEFAULT_ACCEPT_LANGUAGE);
        }
        
        FetchedResult result = _fetcher.fetch(pageURL);
        _lastFetchTime = System.currentTimeMillis();
        
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

    private void doClean() throws IOException, SAXException {
        if (_curPage == null) {
            System.out.println("Can't output clean content for the page if it hasn't been loaded yet!");
            return;
        }
        
        String filename = readInputLine("Enter output file path: ");
        if (filename.length() == 0) {
            System.out.println(extractContent(_curPage, true));
        } else {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
            osw.write(extractContent(_curPage, true));
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
        
        String skipAsciiAsStr = readInputLine("Skip ASCII text (y/n)? [y]: ");
        boolean skipAscii = skipAsciiAsStr.isEmpty() ? true : skipAsciiAsStr.equals("y");

        String dedupAsStr = readInputLine("Deduplicate lines (y/n)? [n]: ");
        boolean dedup = dedupAsStr.isEmpty() ? false : dedupAsStr.equals("y");
        
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        
        System.out.println(String.format("Loading text lines from files in '%s'...", textDir.getCanonicalPath()));
        
        Pattern filenamePattern = Pattern.compile("(.+)_(.+).txt");
        
        List<String> dedupedLines = new ArrayList<>();
        
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
                    // that is just noise. This is from Wikipedia.
                    line = line.replaceAll("\\[[ ]*\\d+[ ]*\\]", " ");
                    
                    // If we're getting rid of ascii text, do that now.
                    if (skipAscii) {
                        line = line.replaceAll("[\\x20-\\xFF…“”‘’]{2,}", " ");
                    }
                    
                    // We wind up with a lot of multi-space sequences, due to markup in text always getting converted to
                    // text by inserting a space.
                    line = line.replaceAll("[ \t]+", " ").trim();
                    if (line.length() < 2) {
                        continue;
                    }

                    if (dedup) {
                        dedupedLines.add(String.format("%s\t%s", language, line));
                    } else {
                        osw.write(language);
                        osw.write('\t');
                        osw.write(line);
                        osw.write('\n');
                    }
                }
                
                if (dedup) {
                    dedupedLines.sort(null);
                    int index = 1;
                    while (index < dedupedLines.size()) {
                        if (dedupedLines.get(index - 1).equals(dedupedLines.get(index))) {
                            dedupedLines.remove(index);
                        } else {
                            index++;
                        }
                    }
                }
            }
            
            // Defer writing out lines until after we're all done.
            if (dedup) {
                for (String line : dedupedLines) {
                    osw.write(line);
                    osw.write('\n');
                }
            }
        } finally {
            osw.close();
        }
    }
    
    private int saveFetchedPage(Document doc, File parentFolder, String language, boolean overwrite) throws IOException, SAXException {
        String content = extractContent(doc, true);
        
        // Don't bother saving super-short pages.
        if (content.length() < MIN_PAGE_CONTENT_LENGTH) {
            return 0;
        }
        
        // Write out the original content, as XML, so we could potentially re-process it
        File xmlDir = new File(parentFolder, XML_SUBDIR_NAME);
        xmlDir.mkdirs();
        
        String filename = String.format("%d_%s", Math.abs(doc.hashCode()), language);
        File xmlFile = new File(xmlDir, filename + ".xml");
        if (overwrite || !xmlFile.exists()) {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8");
            osw.write(formatXml(doc));
            osw.close();
        }
        
        File textDir = new File(parentFolder, TEXT_SUBDIR_NAME);
        textDir.mkdirs();

        // Now save the main content
        File textFile = new File(textDir, filename + ".txt");
        if (overwrite || !textFile.exists()) {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(textFile), "UTF-8");
            osw.write(content);
            osw.close();
            
            return content.length();
        } else {
            return 0;
        }
        
    }
    
    private String extractContent(Document page, boolean skipAscii) throws SAXException {
        StringWriter writer = new StringWriter();
        BoilerpipeContentHandler handler = new BoilerpipeContentHandler(writer);
        DOM2SAX d2s = new DOM2SAX(handler);
        d2s.writeDocument(page, false);

        if (!skipAscii) {
            return writer.toString();
        }
        
        // Now we want to process the text twice - once to remove ascii, and the other to
        // remove empty lines that might result.
        StringBuilder noAscii = new StringBuilder();
        StringBuffer original = writer.getBuffer();
        Matcher m = Pattern.compile("[\\x20-\\xFF…“”‘’]{2,}").matcher(original);
        int offset = 0;
        while (m.find(offset)) {
            int matchStart = m.start();
            if (matchStart > offset) {
                noAscii.append(original.substring(offset, matchStart));
            }
            
            offset = m.end();
        }
        
        if (offset < original.length()) {
            noAscii.append(original.substring(offset));
        }
        
        // Now get rid of empty lines.
        StringBuilder result = new StringBuilder();
        m = Pattern.compile("^\n", Pattern.MULTILINE).matcher(noAscii);
        offset = 0;
        while (offset < noAscii.length() && m.find(offset)) {
            int matchStart = m.start();
            if (matchStart > offset) {
                result.append(noAscii.substring(offset, matchStart));
            }
            
            // Skip newline
            offset = m.end() + 1;
        }
        
        if (offset < noAscii.length()) {
            result.append(noAscii.substring(offset));
        }
        
        return result.toString();
    }

    private List<String> extractLinks(Document page) {
        List<String> result = new ArrayList<>();
        List<Node> nodes = selectNodes(page, "//a");
        for (Node link : nodes) {
            Element e = (Element)link;
            String url = e.attributeValue("href");
            if (url != null) {
                result.add(url);
            }
        }
        
        return result;
    }

    private void doCrawl() throws IOException, DocumentException, BaseFetchException, SAXException {
        String domain = readInputLine("Enter a domain (e.g. my-domain.com): ");
        if (domain.trim().isEmpty()) {
            return;
        }
        
        String language = readInputLine("Enter a target language (3 character code): ");
        if (language.trim().isEmpty()) {
            return;
        }
        
        String acceptLanguage = makeAcceptLanguageFromLanguage(language);
        
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
        
        final int targetChars = 100000;
        final int targetPages = 100;
        
        int totalPages = 0;
        int numCharsExtracted = 0;
        
        Set<String> fetched = new HashSet<>();
        List<String> urls = new LinkedList<>();
        urls.add("http://" + domain);
        
        Random rand = new Random(System.currentTimeMillis());
        
        while ((numCharsExtracted < targetChars) && (totalPages < targetPages) && !urls.isEmpty()) {
            // Get another page from our list of URLs.
            int urlIndex = rand.nextInt(urls.size());
            String pageUrl = urls.remove(urlIndex);
            if (fetched.contains(pageUrl)) {
                continue;
            }
            
            // Always say we've fetched it, so that if we fail we don't try to refetch again.
            fetched.add(pageUrl);
            
            Document page = loadAndParseHTML(pageUrl, acceptLanguage);
            for (String link : extractLinks(page)) {
                try {
                    URL url = new URL(link);
                    if (!url.getHost().endsWith(domain)) {
                        // Skip URLs that aren't for our target domain.
                        continue;
                    }
                } catch (MalformedURLException e) {
                    // Ignore.
                    continue;
                }
                
                if (!fetched.contains(link)) {
                    // We might add a URL that already exists, but we'll dedup by seeing
                    // that it's been fetched already.
                    urls.add(link);
                }
            }
            
            int numChars = saveFetchedPage(page, parentFolder, language, true);
            if (numChars > 0) {
                LOGGER.debug(String.format("Fetched %d chars from page '%s'", numChars, pageUrl));
                numCharsExtracted += numChars;
            }
            
            totalPages += 1;
        }
        
        // Print some statistics...
        System.out.println(String.format("'%s': %d chars", domain, numCharsExtracted));
    }
    
    /**
     * The only languages we currently care about are Chinese (Traditional & Simplified).
     * Some sites need the Accept-Language value to be an ISO two character name, so do
     * an explicit mapping here.
     * 
     * TODO - extract the language code portion, and try to map from ISO 639-2 (3 char) to
     * ISO-639-1 (2 char)...if the mapping exists, update it and return that result.
     * 
     * @param language Language name (using ISO 639-2 codes)
     * @return language name (using ISO 639-1 codes)
     */
    private String makeAcceptLanguageFromLanguage(String language) {
        if (language.equals("zho-Hant")) {
            return "zh-Hant";
        } else if (language.equals("zho-Hans")) {
            return "zh-Hans";
        } else if (language.equals("dzo")) {
            return "dz";
        } else {
            return language;
        }
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


    public static void main(String[] args) {
        OneDomainCrawlTool tool = new OneDomainCrawlTool();
        
        try {
            
            // Now loop, getting commands
            while (true) {
                String cmdName = readInputLine("Enter command (help, load, dump, clean, xpath, crawl, merge, quit): ");
                if (cmdName.equalsIgnoreCase("help")) {
                    tool.displayHelp();
                } else if (cmdName.equalsIgnoreCase("load")) {
                    tool.loadDocument();
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
        return readInputLine().trim();
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
    
}
