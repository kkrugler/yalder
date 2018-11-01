package org.krugler.yalder.tika;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.junit.Test;

public class YalderLangDetectorTest {

    @Test
    public void test() throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        LanguageDetector detector = new YalderLangDetector().loadModels();
        LanguageHandler handler = new LanguageHandler(detector);
        Metadata metadata = new Metadata();
        try (InputStream stream = YalderLangDetectorTest.class.getResourceAsStream("/test-es.txt")) {
            parser.parse(stream, handler, metadata);
        }
        
        LanguageResult result = handler.getLanguage();
        assertEquals("es", result.getLanguage());
        assertEquals(LanguageConfidence.HIGH, result.getConfidence());
    }
    
    @Test
    public void testNoTikaParsers() throws Exception {
        MediaType mediaType = MediaType.TEXT_PLAIN;
        Charset charset = StandardCharsets.UTF_8;
        MediaType type = new MediaType(mediaType, charset);
        
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, type.toString());

        LanguageDetector detector = new YalderLangDetector().loadModels();
        LanguageHandler handler = new LanguageHandler(detector);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("p");
        
        try (Reader reader = new InputStreamReader(YalderLangDetectorTest.class.getResourceAsStream("/test-es.txt"), charset)) {
            char[] buffer = new char[4096];
            int n = reader.read(buffer);
            while (n != -1) {
                xhtml.characters(buffer, 0, n);
                n = reader.read(buffer);
            }
        }

        xhtml.endElement("p");
        xhtml.endDocument();
        
        LanguageResult result = handler.getLanguage();
        assertEquals("es", result.getLanguage());
        assertEquals(LanguageConfidence.HIGH, result.getConfidence());
    }

}
