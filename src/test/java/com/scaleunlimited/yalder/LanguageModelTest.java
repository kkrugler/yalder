package com.scaleunlimited.yalder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class LanguageModelTest {

    @Test
    public void testEquality() throws Exception {
        LanguageLocale modelLanguage = LanguageLocale.fromString("eng");
        final int maxNGramLength = 4;
        Map<String, Integer> normalizedCounts1 = new HashMap<>();
        normalizedCounts1.put("abc", 1);
        normalizedCounts1.put("a", 6);
        normalizedCounts1.put("ab", 2);
        normalizedCounts1.put("ad", 2);
        normalizedCounts1.put("abc", 1);
        normalizedCounts1.put("abb", 1);
        LanguageModel model1 = new LanguageModel(modelLanguage, maxNGramLength, normalizedCounts1);

        Map<String, Integer> normalizedCounts2 = new HashMap<>(normalizedCounts1);
        LanguageModel model2 = new LanguageModel(modelLanguage, maxNGramLength, normalizedCounts2);
        assertEquals(model1, model2);

        normalizedCounts2.put("abb", 2);
        model2 = new LanguageModel(modelLanguage, maxNGramLength, normalizedCounts2);
        assertFalse(model1.equals(model2));
        
        normalizedCounts2.put("abb", 1);
        model2 = new LanguageModel(modelLanguage, maxNGramLength, normalizedCounts2);
        assertEquals(model1, model2);

        model2 = new LanguageModel(modelLanguage, maxNGramLength-1, normalizedCounts2);
        assertFalse(model1.equals(model2));
    }
    
    @Test
    public void testSerialization() throws Exception {
        LanguageLocale modelLanguage = LanguageLocale.fromString("eng");
        final int maxNGramLength = 4;
        Map<String, Integer> normalizedCounts = new HashMap<>();
        normalizedCounts.put("abc", 1);
        normalizedCounts.put("a", 6);
        normalizedCounts.put("ab", 2);
        normalizedCounts.put("ad", 2);
        normalizedCounts.put("abc", 1);
        normalizedCounts.put("abb", 1);

        LanguageModel model1 = new LanguageModel(modelLanguage, maxNGramLength, normalizedCounts);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos, "UTF-8");
        model1.writeModel(osw);
        osw.close();
        
        // Now create a model using that same data.
        LanguageModel model2 = new LanguageModel();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        InputStreamReader isr = new InputStreamReader(bais, "UTF-8");
        model2.readModel(isr);
        assertEquals(model1, model2);
    }

}
