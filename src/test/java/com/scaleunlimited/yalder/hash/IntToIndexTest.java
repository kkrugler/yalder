package com.scaleunlimited.yalder.hash;

import static org.junit.Assert.*;

import org.junit.Test;

import com.scaleunlimited.yalder.hash.IntToIndex;

public class IntToIndexTest {

    @Test
    public void testSimple() {
        IntToIndex i2i = new IntToIndex();
        assertFalse(i2i.contains(-10000));
        assertEquals(-1, i2i.getIndex(-10000));
        assertFalse(i2i.contains(0));
        assertEquals(-1, i2i.getIndex(0));
        assertFalse(i2i.contains(10000));
        assertEquals(-1, i2i.getIndex(10000));

        i2i.add(10000);
        assertTrue(i2i.contains(10000));
        assertEquals(0, i2i.getIndex(10000));
        
        i2i.add(0);
        assertTrue(i2i.contains(0));
        assertEquals(0, i2i.getIndex(0));
        assertEquals(1, i2i.getIndex(10000));
        
        i2i.add(-10000);
        assertTrue(i2i.contains(-10000));
        assertEquals(0, i2i.getIndex(-10000));
        assertEquals(1, i2i.getIndex(0));
        assertEquals(2, i2i.getIndex(10000));
    }
    
    @Test
    public void testManyInserts() {
        final int numEntries = 2000;

        // Force array expansion
        IntToIndex i2i = new IntToIndex(numEntries/2);
        assertEquals(0, i2i.size());
        
        
        for (int i = numEntries - 1; i >= 0; i--) {
            assertFalse(i2i.contains(i));
            i2i.add(i);
            assertTrue(i2i.contains(i));
        }
        
        assertEquals(numEntries, i2i.size());
        
        for (int i = 0; i < numEntries; i++) {
            assertEquals(i, i2i.getIndex(i));
        }
    }

}
