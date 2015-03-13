package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import org.junit.Test;

import com.scaleunlimited.yalder.old.MasterNGramVector;
import com.scaleunlimited.yalder.old.NGramVector;

public class MasterNGramVectorTest {

    @Test
    public void testVectorCreation() {
        NGramVector vector1 = new NGramVector();
        for (int i = 0; i < 1000; i++) {
            assertFalse(vector1.set(NGramVector.calcHash("a" + i)));
        }
        MasterNGramVector masterVector = new MasterNGramVector(vector1);
        
        for (int i = 0; i < 1000; i++) {
            assertTrue(masterVector.mark(NGramVector.calcHash("a" + i)));
        }

        NGramVector vector2 = masterVector.makeVector();
        assertEquals(1000, vector2.size());
        assertEquals(15250, vector1.getLengthSquared());
        assertEquals(15250, vector2.getLengthSquared());

        assertEquals(1.0, vector1.score(vector2), 0.0001);
    }

}
