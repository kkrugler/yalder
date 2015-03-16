package com.scaleunlimited.yalder;


public class MasterNGramVector {

    private NGramVector _master;
    private NGramVector _marked;
    
    public MasterNGramVector(NGramVector vector) {
        _master = new NGramVector(vector);
        _marked = new NGramVector(vector.size());
    }
    
    public void clearMarks() {
        _marked.clear();
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
        if (_master.contains(hash)) {
            // We set the weight to 1, as we treat the target as an unweighted
            // vector of boolean term existence flags.
            _marked.set(hash, 1);
            return true;
        } else {
            return false;
        }
    }
    
    public boolean mark(CharSequence ngram) {
        return mark(CharUtils.joaat_hash(ngram));
    }
    
    public NGramVector makeVector() {
        return _marked;
    }
    
    @Override
    public String toString() {
        return _master.toString() + "\n\n" + _marked.toString();
    }
    
}
