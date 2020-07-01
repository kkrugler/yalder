package org.krugler.yalder.hash;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IntToIndex {

    private static final int DEFAULT_CAPACITY = 1000;
    
    private Int2IntOpenHashMap _map;
    
    public IntToIndex() {
        this(DEFAULT_CAPACITY);
    }
    
    public IntToIndex(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        
        _map = new Int2IntOpenHashMap(initialCapacity);
        _map.defaultReturnValue(-1);
    }
    
    public int size() {
        return _map.size();
    }
    
    public boolean contains(int key) {
        return _map.containsKey(key);
    }
    
    // Put the key.
    public void add(int key) {
        _map.put(key, -1);
    }
    
    public void remove(int key) {
        _map.remove(key);
    }
    
    public void setIndexes() {
        int index = 0;
        for (int key : _map.keySet()) {
            _map.put(key, index++);
        }
    }
    
    public int getIndex(int key) {
        return _map.get(key);
    }
    
}
