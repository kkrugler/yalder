package org.krugler.yalder.text;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.krugler.yalder.text.TextTokenizer;

public class TextTokenizerTest {

    @Test
    public void testMyNGramIteratorTrigrams() throws Exception {
        TextTokenizer tokenizer = new TextTokenizer("abc", 3);
        
        Set<String> ngrams = new HashSet<String>();
        
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            System.out.println(ngram);
            assertTrue(ngrams.add(ngram));
        }
        
        assertEquals(6, ngrams.size());
    }
    
    @Test
    public void testNormalizationAndSpaceCollapse() throws Exception {
        TextTokenizer tokenizer = new TextTokenizer("A ,!", 3);
        
        Set<String> ngrams = new HashSet<String>();
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            assertTrue(ngrams.add(ngram.toString()));
        }
        
        assertEquals(3, ngrams.size());
        assertTrue(ngrams.contains("a"));
        assertTrue(ngrams.contains("a "));
        assertTrue(ngrams.contains(" "));
    }
    
    @Test
    public void testNormalizedBufferReset() throws Exception {
        int numChars = 2000;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numChars; i++) {
            sb.append((char)((int)'a' + (i % 10)));
        }
        
        // We should get 3(N - 1) ngrams
        int numNGrams = 0;
        TextTokenizer tokenizer = new TextTokenizer(sb.toString(), 3);
        while (tokenizer.hasNext()) {
            tokenizer.next();
            numNGrams += 1;
        }
        
        assertEquals(3 * (numChars - 1), numNGrams);
    }

}
