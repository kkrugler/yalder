package org.krugler.yalder.text;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.krugler.yalder.LanguageLocale;

public class TextLanguageModelTest {

    @Test
    public void testRoundTrip() throws IOException {
        Map<String, Integer> ngramCounts = new HashMap<>();
        
        ngramCounts.put("the", 100);
        ngramCounts.put("and", 20);

        final int maxNGramLength = 4;
        final int alpha = 10;
        
        LanguageLocale ll = LanguageLocale.fromString("eng");
        TextLanguageModel model1 = new TextLanguageModel(ll, maxNGramLength, alpha, ngramCounts);
        
        File testDir = new File("build/test/TextLanguageModelTest/testRoundTrip/");
        testDir.mkdirs();
        File testFile = new File(testDir, "model_eng.txt");
        testFile.delete();
        
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(testFile), StandardCharsets.UTF_8);
        model1.writeAsText(osw);
        osw.close();
        
        // Now read it back in.
        InputStreamReader isr = new InputStreamReader(new FileInputStream(testFile), StandardCharsets.UTF_8);
        TextLanguageModel model2 = new TextLanguageModel();
        model2.readAsText(isr);
        isr.close();
        
        assertEquals(ll, model2.getLanguage());
        assertEquals(maxNGramLength, model2.getMaxNGramLength());
        assertEquals(alpha, model2.getAlpha());
        
        assertEquals(ngramCounts.size(), model2.getNGramCounts().size());
        
        for (String ngram : ngramCounts.keySet()) {
            assertEquals(ngramCounts.get(ngram), (Integer)model2.getNGramCount(ngram));
        }
    }

}
