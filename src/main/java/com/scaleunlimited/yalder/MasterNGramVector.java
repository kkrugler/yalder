package com.scaleunlimited.yalder;


public class MasterNGramVector {

    public enum MarkResult {
        MISSING,
        EXISTING,
        NEW
    }
    
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
     * @param hash
     * @return result of marking this entry.
     */
    public MarkResult mark(int hash) {
        if (_master.contains(hash)) {
            
            // We set the weight to 1, as we treat the target as an unweighted
            // vector of boolean term existence flags.
            if (_marked.set(hash, 1)) {
                return MarkResult.NEW;
            } else {
                return MarkResult.EXISTING;
            }
        } else {
            return MarkResult.MISSING;
        }
    }
    
    public MarkResult mark(CharSequence ngram) {
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
