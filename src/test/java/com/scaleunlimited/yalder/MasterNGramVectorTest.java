package com.scaleunlimited.yalder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.scaleunlimited.yalder.MasterNGramVector.MarkResult;

public class MasterNGramVectorTest {

    @Test
    public void testScoring() {
        NGramVector vector1 = new NGramVector();
        for (int i = 0; i < 1000; i++) {
            String ngram = "a" + i;
            assertTrue(vector1.set(ngram, ngram.length()));
        }
        
        MasterNGramVector masterVector = new MasterNGramVector(vector1);
        
        for (int i = 0; i < 1000; i++) {
            String ngram = "a" + i;
            assertEquals("Item not new: " + i, MarkResult.NEW, masterVector.mark(ngram));
        }

        assertEquals(15250, vector1.getLengthSquared());

        // Score will be 2 * 10 + 3 * 90 + 4 * 900 / sqrt(squared length * squared length)
        double expected = (20 + 270 + 3600) / Math.sqrt(15250);
        assertEquals(expected, masterVector.score(vector1), 0.0001);
    }

}
