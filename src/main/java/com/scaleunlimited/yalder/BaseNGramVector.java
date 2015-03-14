package com.scaleunlimited.yalder;

import java.util.Iterator;


public abstract class BaseNGramVector {

    // This constant depends on how many terms we typically create for a language model,
    // and thus depends on values used by ModelBuilder.
    public static final int EXPECTED_NGRAM_COUNT = 5000;
    
    public abstract int get(int hash);
    public abstract boolean set(int hash);
    public abstract int size();

    /**
     * Merge all values from <termVector> into this vector.
     * 
     * @param termVector
     */
    public abstract void merge(BaseNGramVector vector);

    public abstract void clear();
    
    public abstract int getLengthSquared();

    public abstract boolean contains(int hash);

    public abstract double score(BaseNGramVector o);

    public abstract Iterator<Integer> getIterator();
    
    // Static helper routines.
    
    public static int calcHash(CharSequence ngram) {
        int length = ngram.length();
        if ((length == 0) || (length > 7)) {
            throw new IllegalArgumentException("Length of ngram must be >= 1 and <= 7, got " + ngram);
        }
        
        return (length << 29) | CharUtils.joaat_hash(ngram);
    }

    
    /**
     * Length of ngram is stored in high three bits.
     * 
     * @param hash
     * @return length of the ngram
     */
    public static int getLength(int hash) {
        return (hash >> 29) & 0x07;
    }
    
    

}
