package com.scaleunlimited.yalder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MasterNGramVectorTest {

    @Test
    public void testVectorCreation() {
        NGramVector vector1 = new NGramVector();
        for (int i = 0; i < 1000; i++) {
            String ngram = "a" + i;
            assertTrue(vector1.set(ngram, ngram.length()));
        }
        
        MasterNGramVector masterVector = new MasterNGramVector(vector1);
        
        for (int i = 0; i < 1000; i++) {
            String ngram = "a" + i;
            assertTrue(masterVector.mark(ngram));
        }

        NGramVector vector2 = masterVector.makeVector();
        assertEquals(1000, vector2.size());
        assertEquals(15250, vector1.getLengthSquared());
        assertEquals(1000, vector2.getLengthSquared());

        // Score will be 2 x 10 + 3 * 90 + 4 * 900 / sqrt(squared length * squared length)
        double expected = (20 + 270 + 3600) / Math.sqrt(15250 * 1000);
        assertEquals(expected, vector1.score(vector2), 0.0001);
    }

}
