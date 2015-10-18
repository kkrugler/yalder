package org.krugler.yalder.hash;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IntIntMap {

    private static final int DEFAULT_CAPACITY = 1000;
    
    private Int2IntOpenHashMap _map;
    
    /**
     * Native int to int map that uses a single sorted array of longs to store
     * the key (high int) and value (low int).
     * 
     * 
     */
    public IntIntMap() {
        this(DEFAULT_CAPACITY);
    }
    
    public IntIntMap(IntIntMap source) {
        _map = new Int2IntOpenHashMap(source._map);
    }
    
    public IntIntMap(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        
        _map = new Int2IntOpenHashMap(initialCapacity);
        _map.defaultReturnValue(0);
    }
    
    public int size() {
        return _map.size();
    }
    
    public int sum() {
        int result = 0;
        for (int v : _map.values()) {
            result += v;
        }
        
        return result;
    }
    
    public int[] keySet() {
        int[] result = new int[_map.size()];
        
        int index = 0;
        for (int k : _map.keySet()) {
            result[index++] = k;
        }
        
        return result;
    }
    
    public boolean contains(int key) {
        return _map.containsKey(key);
    }
    
    public void put(int key, int value) {
        _map.put(key, value);
    }
    
    // Put the entry (key, value) into the map. If it already exists,
    // add the value to what's there.
    public void add(int key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be > 0");
        }
        
        _map.addTo(key, value);
    }
    
    public int getValue(int key) {
        return _map.get(key);
    }
    
    @Override
    public int hashCode() {
        return _map.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IntIntMap other = (IntIntMap) obj;
        return _map.equals(other._map);
    }
    
}
