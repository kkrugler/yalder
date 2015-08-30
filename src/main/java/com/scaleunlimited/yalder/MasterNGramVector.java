package com.scaleunlimited.yalder;

import java.util.BitSet;


public class MasterNGramVector {

    public enum MarkResult {
        MISSING,
        EXISTING,
        NEW
    }

    private static final int NUM_BITS = 18;
    private static final int NUM_BIT_HASHES = 1 << NUM_BITS;
    private static final int BIT_HASH_MASK = 0x03FFFF;
    
    private NGramVector _master;
    private BitSet _marked;
    
    public MasterNGramVector(NGramVector vector) {
        _master = new NGramVector(vector);
        _marked = new BitSet(NUM_BIT_HASHES);
    }
    
    public void clearMarks() {
        _marked.clear();
    }
    
    private int makeBitsetHash(int hash) {
        return (hash >> 3) & BIT_HASH_MASK; 
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
            int bitsetHash = makeBitsetHash(hash);
            if (_marked.get(bitsetHash)) {
                return MarkResult.EXISTING;
            } else {
                _marked.set(bitsetHash);
                return MarkResult.NEW;
            }
        } else {
            return MarkResult.MISSING;
        }
    }
    
    public MarkResult mark(CharSequence ngram) {
        return mark(CharUtils.joaat_hash(ngram));
    }
    
    public double score(NGramVector langVector) {
        int score = 0;
        for (int term : langVector._terms) {
            if (_marked.get(makeBitsetHash(term))) {
                score += NGramVector.makeWeight(term);
            }
        }
        
        return score / Math.sqrt(langVector.getLengthSquared());
    }
    
    
    @Override
    public String toString() {
        return _master.toString() + "\n\n" + _marked.toString();
    }
    
}
