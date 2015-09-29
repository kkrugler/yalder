package com.scaleunlimited.yalder;

import java.util.Arrays;

public class IntIntMap {

    private static final int DEFAULT_CAPACITY = 1000;
    
    private long[] _entries;
    private int _size;
    
    /**
     * Native int to int map that uses a single sorted array of longs to store
     * the key (high int) and value (low int).
     * 
     * 
     */
    public IntIntMap() {
        this(DEFAULT_CAPACITY);
    }
    
    public IntIntMap(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        
        _entries = new long[initialCapacity];
        _size = 0;
    }
    
    public int size() {
        return _size;
    }
    
    public boolean contains(int key) {
        int index = findIndex(key);
        return ((index < _size) && (extractKey(_entries[index]) == key));
    }
    
    // Put the entry (key, value) into the map. If it already exists,
    // add the value to what's there.
    public void add(int key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be > 0");
        }
        
        int index = findIndex(key);
        if ((index < _size) && (extractKey(_entries[index]) == key)) {
            // Just add the new value to the current entry.
            _entries[index] = makeEntry(key, value + extractValue(_entries[index]));
        } else {
            if (_size == _entries.length) {
                long[] newEntries = new long[(_size * 3) / 2];
                System.arraycopy(_entries, 0, newEntries, 0, _size);
                _entries = newEntries;
            }
            
            System.arraycopy(_entries, index, _entries, index + 1, _size - index);
            _size += 1;
            _entries[index] = makeEntry(key, value);
        }
    }
    
    public int getValue(int key) {
        int index = findIndex(key);
        if (index >= _size) {
            return 0;
        }
        
        long curEntry = _entries[index];
        int curCur = extractKey(curEntry);
        if (curCur == key) {
            return extractValue(curEntry);
        } else {
            return 0;
        }
    }
    
    private int findIndex(int key) {
        if (_size == 0) {
            return 0;
        } else {
            return -Arrays.binarySearch(_entries, 0, _size, makeEntry(key, 0)) - 1;
        }
    }
    
    private long makeEntry(int key, int value) {
        return ((long)key << 32) | ((long)value & 0x0FFFFFFFF);
    }
    
    private int extractKey(long entry) {
        return (int)(entry >> 32);
    }
    
    private int extractValue(long entry) {
        return (int)(entry & 0x0FFFFFFFF);
    }
}
