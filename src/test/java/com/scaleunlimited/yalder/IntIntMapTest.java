package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import org.junit.Test;

public class IntIntMapTest {

    @Test
    public void testSimple() {
        IntIntMap map = new IntIntMap();
        
        assertEquals(0, map.getValue(0));
        assertFalse(map.contains(0));
        
        map.add(0, 100);
        assertEquals(100, map.getValue(0));
        assertTrue(map.contains(0));
        
        assertEquals(0, map.getValue(1));
        assertFalse(map.contains(1));
        
        // Insert value after existing key.
        map.add(1, 200);
        assertEquals(200, map.getValue(1));
        assertTrue(map.contains(1));
        
        // make sure initial entry hasn't been disturbed.
        assertEquals(100, map.getValue(0));
        assertTrue(map.contains(0));
    }
    
    @Test
    public void testManyInserts() {
        // Start off with 1000 entries.
        IntIntMap map = new IntIntMap(1000);
        assertEquals(0, map.size());
        
        for (int i = 0; i < 2000; i++) {
            assertEquals(0, map.getValue(i));
            assertFalse(map.contains(i));
            
            int value = (i + 1) * 100;
            map.add(i,  value);
            assertEquals(value, map.getValue(i));
            assertTrue(map.contains(i));
        }
        
        assertEquals(2000, map.size());
        
        for (int i = 0; i < 2000; i++) {
            int value = (i + 1) * 100;
            assertEquals(value, map.getValue(i));
        }
    }
    


}
