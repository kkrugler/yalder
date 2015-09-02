package com.scaleunlimited.yalder;

public class NGramStats {
    
    private int _docCount;
    private int _ngramCount;
    
    public NGramStats() {
        _docCount = 0;
        _ngramCount = 0;
    }
    
    public void incDocCount() {
        _docCount += 1;
    }

    public void incNGramCount(int count) {
        _ngramCount += count;
    }
    
    public void incNGramCount() {
        incNGramCount(1);
    }
    
    public void setNGramCount(int count) {
        _ngramCount = count;
    }
    
    public int getNGramCount() {
        return _ngramCount;
    }
    
    public int getDocCount() {
        return _docCount;
    }
    
}
