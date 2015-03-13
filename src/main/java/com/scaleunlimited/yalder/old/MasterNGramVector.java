package com.scaleunlimited.yalder.old;

import java.util.Arrays;

public class MasterNGramVector {

    private NGramVector _vector;
    private boolean[] _marked;
    private int _numMarked;
    
    public MasterNGramVector(NGramVector vector) {
        _vector = new NGramVector(vector);
        _marked = new boolean[_vector.size()];
        _numMarked = 0;
    }
    
    public void clearMarks() {
        Arrays.fill(_marked, false);
        _numMarked = 0;
    }
    
    /**
     * If the <hash> entry exists, set the corresponding
     * flag to true (mark it) so we know it exists.
     * 
     * TODO really we want three results - doesn't exist so not set,
     * exists but wasn't flagged so set flag to true, exists and
     * was flagged so did nothing.
     * 
     * @param hash
     * @return true if this entry exists, so we marked it.
     */
    public boolean mark(int hash) {
        int index = _vector.getIndex(hash);
        if (index >= 0) {
            if (!_marked[index]) {
                _marked[index] = true;
                _numMarked += 1;
            }
            
            return true;
        } else {
            return false;
        }
    }
    
    public NGramVector makeVector() {
        NGramVector result = new NGramVector(_numMarked);
        
        int index = 0;
        for (int i = 0; i < _marked.length; i++) {
            if (_marked[i]) {
                result.set(index++, _vector._terms[i]);
            }
        }
        
        return result;
    }
    
}
