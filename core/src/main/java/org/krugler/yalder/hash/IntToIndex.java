package org.krugler.yalder.hash;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IntToIndex {

    private static final int DEFAULT_CAPACITY = 1000;
    
    private Int2IntOpenHashMap _map;
    private boolean _frozen = false;
    
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
    
    /**
     * Add the key, with an unset index (value)
     * 
     * @param key
     */
    public void add(int key) {
        if (_frozen) {
            throw new IllegalStateException("Can't add a key to a frozen IntToIndex map");
        }
        
        _map.put(key, -1);
    }
    
    public void remove(int key) {
        if (_frozen) {
            throw new IllegalStateException("Can't remove a key from a frozen IntToIndex map");
        }
        
        _map.remove(key);
    }
    
    /**
     * One-time call (once all keys have been added) to
     * assign increasing index values for every key.
     */
    public void setIndexes() {
        if (_frozen) {
            throw new IllegalStateException("setIndexes() has already been called");
        }
        
        int index = 0;
        for (int key : _map.keySet()) {
            _map.put(key, index++);
        }
        
        _frozen = true;
    }
    
    public int getIndex(int key) {
        if (!_frozen) {
            throw new IllegalStateException("setIndexes() must be called before getIndex()");
        }
        
        return _map.get(key);
    }
    
}
