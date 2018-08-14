package org.krugler.yalder.hash;

import static org.junit.Assert.*;

import org.junit.Test;

public class HashTokenizerTest {

    @Test
    public void testManyIncrementalAdds() throws Exception {
        final int maxNGramLength = 3;
        HashTokenizer tokenizer = new HashTokenizer(maxNGramLength);

        final int numBigrams = 1000;
        int numNGrams = 0;

        for (int i = 0; i < numBigrams; i++) {
            tokenizer.addText("ab");
            while (tokenizer.hasNext()) {
                tokenizer.next();
                numNGrams += 1;
            }
        }
        
        tokenizer.complete();
        while (tokenizer.hasNext()) {
            tokenizer.next();
            numNGrams += 1;
        }

        // We should get maxNGramLength * (N - 1) ngrams. Since the tokenizer
        // adds a space at the beginning & end, this means N = chars + 2.
        final int totalChars = (numBigrams * 2) + 2;
        final int exectedNGrams = maxNGramLength * (totalChars - 1);
        assertEquals(exectedNGrams, numNGrams);
    }

    @Test
    public void testIncrementalAdd() throws Exception {
        final int maxNGramLength = 3;
        HashTokenizer tokenizer = new HashTokenizer(maxNGramLength);

        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" "), tokenizer.next());
        assertFalse(tokenizer.hasNext());
        tokenizer.addText("ab");
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" a"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" ab"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("a"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("ab"), tokenizer.next());
        assertFalse(tokenizer.hasNext());
        
        tokenizer.addText("c");
        
        // Buffer has " abc". We should be able to return all ngrams
        // from position 1 now.
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("abc"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("b"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("bc"), tokenizer.next());
        assertFalse(tokenizer.hasNext());
        
        tokenizer.complete();
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("bc "), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("c"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash("c "), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" "), tokenizer.next());
        assertFalse(tokenizer.hasNext());
    }

    @Test
    public void testReset() throws Exception {
        final int maxNGramLength = 3;
        HashTokenizer tokenizer = new HashTokenizer("superduper", maxNGramLength);
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" "), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" s"), tokenizer.next());
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" su"), tokenizer.next());

        tokenizer.reset();
        assertTrue(tokenizer.hasNext());
        assertEquals(HashTokenizer.calcHash(" "), tokenizer.next());
        assertFalse(tokenizer.hasNext());
    }
}
