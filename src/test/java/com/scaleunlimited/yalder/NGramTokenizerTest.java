package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.scaleunlimited.yalder.NGramTokenizer;

public class NGramTokenizerTest {

    @Test
    public void testMyNGramIteratorTrigrams() throws Exception {
        NGramTokenizer tokenizer = new NGramTokenizer("abc", 1, 3);
        
        Set<CharSequence> ngrams = new HashSet<CharSequence>();
        
        while (tokenizer.hasNext()) {
            CharSequence ngram = tokenizer.next();
            assertTrue(ngrams.add(ngram));
        }
        
        assertEquals(6, ngrams.size());
    }
    
    @Test
    public void testNormalizationAndSpaceCollapse() throws Exception {
        NGramTokenizer tokenizer = new NGramTokenizer("A ,!", 1, 3);
        
        Set<String> ngrams = new HashSet<String>();
        while (tokenizer.hasNext()) {
            CharSequence ngram = tokenizer.next();
            assertTrue(ngrams.add(ngram.toString()));
        }
        
        assertEquals(3, ngrams.size());
        assertTrue(ngrams.contains("a"));
        assertTrue(ngrams.contains("a "));
        assertTrue(ngrams.contains(" "));
    }

}
