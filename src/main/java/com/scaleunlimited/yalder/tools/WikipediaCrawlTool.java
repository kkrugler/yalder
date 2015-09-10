package com.scaleunlimited.yalder.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    @SuppressWarnings("unchecked")
    private static List<Node> selectNodes(Document doc, String xpathExpression) {
        return doc.selectNodes(xpathExpression);
    }
    
    private static void doHelp() {
        System.out.println("help - print this help text");
        System.out.println("load - load HTML document from file or URL");
        System.out.println("dump - dump previously loaded HTML document");
        System.out.println("xpath - evaluate XPath expressions using previously loaded HTML document");
        System.out.println("quit - quit");
    }
    
    private static Document doLoad(SimpleHttpFetcher fetcher, String docName, String charset) throws IOException, DocumentException, BaseFetchException {
        if (docName == null) {
            System.out.print("Enter HTML document file path or URL: ");
            docName = readInputLine();
        }
        
        if (docName.length() == 0) {
            return null;
        }
        
        if (charset == null) {
            System.out.print("Enter charset name: ");
            charset = readInputLine();
        }
        
        if (charset.length() == 0) {
            charset = "UTF-8";
            System.out.println("Using UTF-8 as default charset");
        }
        
        InputStream is;
        
        if (docName.startsWith("http://") || docName.startsWith("https://")) {
            FetchedResult result = fetcher.fetch(docName);
            is = new ByteArrayInputStream(result.getContent());
        } else {
            is = new FileInputStream(docName);
        }
        
        HtmlParser parser = new HtmlParser(charset);
        return parser.parse(is, charset);
    }
    
    private static void doDump(Document doc) throws IOException {
        if (doc == null) {
            System.out.println("Can't dump the document if it hasn't been loaded yet!");
            return;
        }
        
        System.out.print("Enter output file path: ");
        String filename = readInputLine();
        if (filename.length() == 0) {
            System.out.println(formatXml(doc));
        } else {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
            osw.write(formatXml(doc));
            osw.close();
        }
    }

    private static void saveWikipediaPage(Document doc, File parentFolder, String basename, String language) throws IOException {
        // First get the main content
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
                mainContent.append(getTextFromNode(node, true));
                mainContent.append("\n");
            }
        }

        File outputFile = new File(parentFolder, String.format("%s_%s.txt", basename, language));
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        osw.write(mainContent.toString());
        osw.close();


    }
    private static void doCrawl(SimpleHttpFetcher fetcher, Document doc) throws IOException, DocumentException, BaseFetchException {
        if (doc == null) {
            System.err.println("Can't crawl if the top document hasn't been loaded yet!");
            return;
        }
        
        System.out.print("Enter output directory: ");
        String directoryName = readInputLine();
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
        
        System.out.print("Enter base filename: ");
        String filename = readInputLine();
        if (filename.length() == 0) {
            return;
        }
        
        // First get the main content
        saveWikipediaPage(doc, parentFolder, filename, "en");
        
        // Now extract links to all of the other pages, and crawl those as well
        List<Node> nodes = selectNodes(doc, "//li[contains(concat(' ', @class, ' '), ' interlanguage-link ')]");
        for (Node node : nodes) {
            Element link = (Element)node.selectSingleNode("./a");
            String language = link.attributeValue("lang");
            String url = link.attributeValue("href");
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
            
            Document subDoc = doLoad(fetcher, url, "UTF-8");
            saveWikipediaPage(subDoc, parentFolder, filename, language);
        }
    }
    
    private static void doXpath(Document doc) throws IOException {
        if (doc == null) {
            System.out.println("Can't query the document if it hasn't been loaded yet!");
            return;
        }

        // Now loop, getting XPath expressions and evaluating them
        while (true) {
            System.out.println();
            System.out.flush();
            System.out.print("Enter XPath expression (or <return> when done): ");

            String xpathExpression = readInputLine();
            if (xpathExpression.length() == 0) {
                break;
            }

            try {
                List<Node> nodes = selectNodes(doc, xpathExpression);
                System.out.println(String.format("%d nodes", nodes.size()));
                System.out.flush();
                
                while (true) {
                    System.out.println();
                    System.out.println("What next (a, f, e, t)? ");
                    System.out.flush();
                    
                    String xpathCmd = readInputLine();
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
        }
        
        StringBuilder result = new StringBuilder();
        List<Node> children = getChildrenFromNode(node);
        for (Node child : children) {
            result.append(getTextFromNode(child, stripReturns));
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
        XPathToolOptions options = new XPathToolOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        try {
            Document doc = null;

            SimpleHttpFetcher fetcher = new SimpleHttpFetcher(new UserAgent("yalder", "ken@transpac.com", "http://ken-blog.krugler.org"));
            // Some pages are > 1MB is size, yikes!
            fetcher.setDefaultMaxContentSize(10 * 1024 * 1024);
            
            if (options.getInputFile() != null) {
                doc = doLoad(fetcher, options.getInputFile(), options.getCharset());
            }

            // Now loop, getting commands
            while (true) {
                System.out.flush();
                System.out.println("Enter command (help, load, dump, xpath, crawl, quit): ");
                String cmdName = readInputLine();
                if (cmdName.equalsIgnoreCase("help")) {
                    doHelp();
                } else if (cmdName.equalsIgnoreCase("load")) {
                    Document newDoc = doLoad(fetcher, null, null);
                    if (newDoc != null) {
                        doc = newDoc;
                    }
                } else if (cmdName.equalsIgnoreCase("dump")) {
                    doDump(doc);
                } else if (cmdName.equalsIgnoreCase("xpath")) {
                    doXpath(doc);
                } else if (cmdName.equalsIgnoreCase("crawl")) {
                    doCrawl(fetcher, doc);
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

    private static class XPathToolOptions {
        
        private boolean _debugLogging = false;
        private boolean _traceLogging = false;
        
        private String _inputFile = null;
        private String _charset = "UTF-8";
        
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

        @Option(name = "-charset", usage = "charset for file", required = false)
        public void setCharset(String charset) {
            _charset = charset;
        }
        
        public String getInputFile() {
            return _inputFile;
        }

        public String getCharset() {
            return _charset;
        }
        

    }
}
