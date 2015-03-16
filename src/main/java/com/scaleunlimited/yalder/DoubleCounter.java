package com.scaleunlimited.yalder;

import java.util.HashMap;
import java.util.Map;

public class DoubleCounter {

    private Map<String, Double> _counters;
    
    public DoubleCounter() {
        _counters = new HashMap<String, Double>();
    }
    
    public double increment(String key, double value) {
        Double curCount = _counters.get(key);
        if (curCount == null) {
            _counters.put(key, value);
            return value;
        } else {
            _counters.put(key, curCount + value);
            return curCount + value;
        }
    }

    public double get(String key) {
        Double curValue = _counters.get(key);
        return (curValue == null ? 0.0 : curValue);
    }

}
