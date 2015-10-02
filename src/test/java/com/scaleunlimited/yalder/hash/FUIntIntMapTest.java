package com.scaleunlimited.yalder.hash;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

import com.scaleunlimited.yalder.hash.FUIntIntMap;

public class FUIntIntMapTest {

    @Test
    public void testSimple() {
        FUIntIntMap map = new FUIntIntMap();
        
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
    public void testSum() {
        FUIntIntMap map = new FUIntIntMap(1000);
        
        final int numEntries = 2000;
        for (int i = 1; i <= numEntries; i++) {
            int value = i * 100;
            map.add(i,  value);
        }

        // Sum of 1...2000 is (2000*2000 + 2000)/2
        int target = 100 * ((numEntries * numEntries) + numEntries) / 2;
        assertEquals(target, map.sum());
    }
    
    @Test
    public void testKeySet() {
        FUIntIntMap map = new FUIntIntMap();
        
        final int numEntries = 100;
        for (int i = 0; i < numEntries; i++) {
            int value = (i + 1) * 100;
            map.add(i,  value);
        }
        
        int[] keys = map.keySet();
        Arrays.sort(keys);
        for (int i = 0; i < numEntries; i++) {
            assertEquals(i, keys[i]);
        }
    }
    
    @Test
    public void testManyInserts() {
        // Start off with 1000 entries.
        FUIntIntMap map = new FUIntIntMap(1000);
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
