package com.scaleunlimited.yalder;

import java.util.HashMap;
import java.util.Map;

public class IntCounter {

    private Map<String, Integer> _counters;
    
    public IntCounter() {
        _counters = new HashMap<String, Integer>();
    }
    
    public int increment(String key) {
        return increment(key, 1);
    }

    public int increment(String key, int value) {
        Integer curCount = _counters.get(key);
        if (curCount == null) {
            _counters.put(key, value);
            return value;
        } else {
            _counters.put(key, curCount + value);
            return curCount + value;
        }
    }

    public int get(String key) {
        Integer curValue = _counters.get(key);
        return (curValue == null ? 0 : curValue);
    }

}
