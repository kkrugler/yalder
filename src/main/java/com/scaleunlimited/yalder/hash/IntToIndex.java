package com.scaleunlimited.yalder.hash;

import java.util.Arrays;

public class IntToIndex {

    private static final int DEFAULT_CAPACITY = 1000;
    
    private int[] _entries;
    private int _size;
    
    
    public IntToIndex() {
        this(DEFAULT_CAPACITY);
    }
    
    public IntToIndex(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        
        _entries = new int[initialCapacity];
        _size = 0;
    }
    
    public int size() {
        return _size;
    }
    
    public boolean contains(int key) {
        int index = findIndex(key);
        return ((index < _size) && (_entries[index] == key));
    }
    
    // Put the key.
    public void add(int key) {
        
        int index = findIndex(key);
        if ((index < _size) && (_entries[index] == key)) {
            // Entry already exists
        } else {
            if (_size == _entries.length) {
                int[] newEntries = new int[(_size * 3) / 2];
                System.arraycopy(_entries, 0, newEntries, 0, _size);
                _entries = newEntries;
            }
            
            System.arraycopy(_entries, index, _entries, index + 1, _size - index);
            _size += 1;
            _entries[index] = key;
        }
    }
    
    public int getIndex(int key) {
        int index = findIndex(key);
        if (index >= _size) {
            return -1;
        } else if (_entries[index] != key) {
            return -1;
        } else {
            return index;
        }
    }
    
    private int findIndex(int key) {
        if (_size == 0) {
            return 0;
        } else {
            int index = Arrays.binarySearch(_entries, 0, _size, key);
            if (index < 0) {
                // Key doesn't exist, return position where it should be inserted.
                return -index - 1;
            } else {
                return index;
            }
        }
    }
    
}
