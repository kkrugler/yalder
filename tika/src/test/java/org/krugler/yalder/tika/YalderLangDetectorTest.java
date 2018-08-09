package org.krugler.yalder.tika;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
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

}
