package org.krugler.yalder.hash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.junit.Test;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.hash.HashLanguageModel;
import org.krugler.yalder.hash.IntIntMap;

public class LanguageModelTest {

    @Test
    public void testEquality() throws Exception {
        LanguageLocale modelLanguage = LanguageLocale.fromString("eng");
        final int maxNGramLength = 4;
        final int alpha = 10;
        IntIntMap normalizedCounts1 = new IntIntMap();
        normalizedCounts1.put("abc".hashCode(), 1);
        normalizedCounts1.put("a".hashCode(), 6);
        normalizedCounts1.put("ab".hashCode(), 2);
        normalizedCounts1.put("ad".hashCode(), 2);
        normalizedCounts1.put("abc".hashCode(), 1);
        normalizedCounts1.put("abb".hashCode(), 1);
        HashLanguageModel model1 = new HashLanguageModel(modelLanguage, maxNGramLength, alpha, normalizedCounts1);

        IntIntMap normalizedCounts2 = new IntIntMap(normalizedCounts1);
        HashLanguageModel model2 = new HashLanguageModel(modelLanguage, maxNGramLength, alpha, normalizedCounts2);
        assertEquals(model1, model2);

        normalizedCounts2.put("abb".hashCode(), 2);
        model2 = new HashLanguageModel(modelLanguage, maxNGramLength, alpha, normalizedCounts2);
        assertFalse(model1.equals(model2));
        
        normalizedCounts2.put("abb".hashCode(), 1);
        model2 = new HashLanguageModel(modelLanguage, maxNGramLength, alpha, normalizedCounts2);
        assertEquals(model1, model2);

        model2 = new HashLanguageModel(modelLanguage, maxNGramLength-1, alpha, normalizedCounts2);
        assertFalse(model1.equals(model2));
    }
    
    @Test
    public void testSerialization() throws Exception {
        LanguageLocale modelLanguage = LanguageLocale.fromString("eng");
        final int maxNGramLength = 4;
        final int alpha = 10;
        IntIntMap normalizedCounts = new IntIntMap();
        normalizedCounts.put("abc".hashCode(), 1);
        normalizedCounts.put("a".hashCode(), 6);
        normalizedCounts.put("ab".hashCode(), 2);
        normalizedCounts.put("ad".hashCode(), 2);
        normalizedCounts.put("abc".hashCode(), 1);
        normalizedCounts.put("abb".hashCode(), 1);

        HashLanguageModel model1 = new HashLanguageModel(modelLanguage, maxNGramLength, alpha, normalizedCounts);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        model1.writeAsBinary(dos);
        dos.close();
        
        System.out.println("Model serialized size = " + baos.size());
        
        // Now create a model using that same data.
        HashLanguageModel model2 = new HashLanguageModel();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        model2.readAsBinary(dis);
        assertEquals(model1, model2);
    }
    
}
