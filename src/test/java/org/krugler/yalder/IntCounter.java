package org.krugler.yalder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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

    public int sum() {
        int total = 0;
        for (Integer value : _counters.values()) {
            total += value;
        }
        
        return total;
    }

    public Set<Entry<String, Integer>> entrySet() {
        return _counters.entrySet();
    }

}
