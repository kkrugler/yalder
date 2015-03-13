package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import org.junit.Test;

import com.scaleunlimited.yalder.cur.NGramVector;

public class FastNGramVectorTest {

    @Test
    public void testInserts() {
        NGramVector vector = new NGramVector();
        
        assertTrue(vector.set(NGramVector.calcHash("a")));
        assertEquals(1, vector.getLengthSquared());
        assertTrue(vector.set(NGramVector.calcHash("ab")));
        assertEquals(5, vector.getLengthSquared());
        assertTrue(vector.set(NGramVector.calcHash("abc")));
        assertEquals(14, vector.getLengthSquared());
        assertTrue(vector.set(NGramVector.calcHash("abcd")));
        assertEquals(30, vector.getLengthSquared());
        assertEquals(4, vector.size());
        
        assertFalse(vector.set(NGramVector.calcHash("a")));
        assertFalse(vector.set(NGramVector.calcHash("ab")));
        assertFalse(vector.set(NGramVector.calcHash("abc")));
        assertFalse(vector.set(NGramVector.calcHash("abcd")));
    }
    
    @Test
    public void testDotProduct() {
        NGramVector vector1 = new NGramVector();
        assertTrue(vector1.set(NGramVector.calcHash("a")));
        
        NGramVector vector2 = new NGramVector();
        assertTrue(vector2.set(NGramVector.calcHash("a")));
        
        assertEquals(1.0, vector1.score(vector2), 0.0001);
        
        assertTrue(vector2.set(NGramVector.calcHash("b")));
        assertEquals(2, vector2.getLengthSquared());
        
        double score = 1.0 / Math.sqrt(2);
        assertEquals(score, vector1.score(vector2), 0.0001);

        assertTrue(vector1.set(NGramVector.calcHash("b")));
        assertEquals(1.0, vector1.score(vector2), 0.0001);
    }

    @Test
    public void testBigDotProduct() {
        NGramVector vector1 = new NGramVector();
        for (int i = 0; i < 1000; i++) {
            assertTrue(vector1.set(NGramVector.calcHash("a" + i)));
        }
        
        NGramVector vector2 = new NGramVector();
        for (int i = 0; i < 500; i++) {
            assertTrue(vector2.set(NGramVector.calcHash("a" + i)));
        }
        for (int i = 500; i < 1000; i++) {
            assertTrue(vector2.set(NGramVector.calcHash("b" + i)));
        }

        // We have 10 entries with length 2, 90 entries with length 3, and 900 entries with length 4
        // So the total length for both vectors should be 10*2^2 = 40 + 90 * 3^2 = 810 + 900 * 4^2 = 14400
        // Grand total is 15250
        assertEquals(15250, vector1.getLengthSquared());
        assertEquals(15250, vector2.getLengthSquared());
        
        // The dot product will have the first 500 elements being a match (a0...a499), so the sum of the squares
        // of those lengths / sqrt(15250)
        // So 40 + 810 + 400 * 4^2 (6400), grand total = 7250
        double expectedScore = 7250 / 15250.0;
        assertEquals(expectedScore, vector1.score(vector2), 0.0001);
    }
    

}
