package org.krugler.yalder.html;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.XMLConstants;

import org.apache.log4j.Logger;
import org.ccil.cowan.tagsoup.Parser;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

public class HtmlParser {
    private static final Logger LOGGER = Logger.getLogger(HtmlParser.class);

    private static class DowngradeXmlFilter extends XMLFilterImpl {
        
        @Override
        public void startElement(
                String uri, String localName, String name, Attributes atts)
                throws SAXException {
            String lower = localName.toLowerCase();

            AttributesImpl attributes = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                String local = atts.getLocalName(i);
                String qname = atts.getQName(i);
                if (!XMLConstants.NULL_NS_URI.equals(atts.getURI(i).length())
                        && !local.equals(XMLConstants.XMLNS_ATTRIBUTE)
                        && !qname.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                    attributes.addAttribute(
                            atts.getURI(i), local, qname,
                            atts.getType(i), atts.getValue(i));
                }
            }

            super.startElement(XMLConstants.NULL_NS_URI, lower, lower, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String name)
                throws SAXException {
            String lower = localName.toLowerCase();
            super.endElement(XMLConstants.NULL_NS_URI, lower, lower);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
        }

        @Override
        public void endPrefixMapping(String prefix) {
        }
    }
    
    
    private String _defaultCharset;
    private transient SAXReader _reader = null;

    public HtmlParser(String defaultCharset) {
        _defaultCharset = defaultCharset;
    }

    private synchronized void init() {
        if (_reader == null) {
            _reader = new SAXReader(new Parser());
            _reader.setXMLFilter(new DowngradeXmlFilter());
        }
    }

    public static String getHostname(String url) {
        try {
            URL realUrl = new URL(url);
            return realUrl.getHost().toLowerCase();
        } catch (MalformedURLException e) {
            LOGGER.error("Should never happen - bad URL: " + url);
            return url;
        }
    }

    public static String getBaseUrl(String url, Document doc) {
        Node baseNode = doc.selectSingleNode("/html/head/base/@href");
        if (baseNode != null) {
            try {
                URL baseUrl = new URL(baseNode.getText());
                return baseUrl.toExternalForm();
            } catch (MalformedURLException e) {
                LOGGER.warn("Invalid base URL: " + baseNode.getText());
                return url;
            }
        } else {
            return url;
        }
    }

    public static String resolveUrl(String baseUrl, String link) {
        try {
            URL realUrl = new URL(baseUrl);
            URL resolvedUrl = new URL(realUrl, link);
            return resolvedUrl.toExternalForm();
        } catch (MalformedURLException e) {
            LOGGER.warn("Can't resolve relative link: " + link);
            return link;
        }
    }

    public Document parse(byte[] buf) throws DocumentException {
        String charset = _defaultCharset;
        InputStream is = new ByteArrayInputStream(buf);
        return parse(is, charset);
    }

    public Document parse(InputStream is) throws DocumentException {
        return parse(is, _defaultCharset);
    }

    public Document parse(InputStream is, String encoding) throws DocumentException {
        init();

        _reader.setEncoding(encoding);
        return _reader.read(is);
    }
}
