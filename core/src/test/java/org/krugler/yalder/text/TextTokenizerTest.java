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
            ngrams.add(ngram);
        }
        
        // tokenizer automatically adds a space at the beginning & end, so
        // we actually have a string of 5 chars we're tokenizing.
        // But we have a duplicate space character (leading/trailing),
        // so the actual count is one less.
        assertEquals(12 - 1, ngrams.size());
    }
    
    @Test
    public void testNormalizationAndSpaceCollapse() throws Exception {
        TextTokenizer tokenizer = new TextTokenizer("A ,!", 3);
        Set<String> ngrams = new HashSet<String>();
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            ngrams.add(ngram.toString());
        }
        
        // Space is added twice, so only 5 (vs 6) ngrams.
        assertEquals(5, ngrams.size());
        assertTrue(ngrams.contains(" "));
        assertTrue(ngrams.contains(" a"));
        assertTrue(ngrams.contains(" a "));
        assertTrue(ngrams.contains("a"));
        assertTrue(ngrams.contains("a "));
    }
    
    @Test
    public void testReturnNormalization() throws Exception {
        TextTokenizer tokenizer = new TextTokenizer("\r\t\n", 1);
        
        assertTrue(tokenizer.hasNext());
        assertEquals(" ", tokenizer.next());
        assertFalse(tokenizer.hasNext());
        
        // Make sure nothing changes after we are complete.
        tokenizer.complete();
        assertFalse(tokenizer.hasNext());
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
        
        // Tokenizer adds a space at the beginning and end, so it's (numChars + 2)
        assertEquals(3 * ((numChars + 2) - 1), numNGrams);
    }

}
