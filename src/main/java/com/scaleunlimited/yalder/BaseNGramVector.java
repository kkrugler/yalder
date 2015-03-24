package com.scaleunlimited.yalder;

import java.util.Iterator;


public abstract class BaseNGramVector {

    // This constant depends on how many terms we typically create for a language model,
    // and thus depends on values used by ModelBuilder.
    public static final int EXPECTED_NGRAM_COUNT = 5000;
    
    // Return the weight of the ngram, given its hash
    public abstract int get(int hash);
    
    // Set the ngram with weight, given its hash.
    // Return true if the vector didn't already contain the
    // ngram (new entry)
    public abstract boolean set(int hash, int weight);
    
    public abstract boolean contains(int hash);

    public abstract int size();

    public abstract int quantizeWeight(double weight);
    
    public int get(CharSequence ngram) {
        return get(CharUtils.joaat_hash(ngram));
    }
    
    public boolean set(CharSequence ngram, double weight) {
        return set(ngram, quantizeWeight(weight));
    }
    
    public boolean set(CharSequence ngram, int weight) {
        return set(CharUtils.joaat_hash(ngram), weight);
    }
    
    public boolean contains(CharSequence ngram) {
        return contains(CharUtils.joaat_hash(ngram));
    }
    
    /**
     * Merge all values from <termVector> into this vector.
     * 
     * @param termVector
     */
    public abstract void merge(BaseNGramVector vector);

    public abstract void clear();
    
    public abstract int getLengthSquared();


    public abstract double score(BaseNGramVector o);

    public abstract Iterator<Integer> getIterator();

}
