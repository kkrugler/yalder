package com.scaleunlimited.yalder;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class NGramVector extends BaseNGramVector {

    private final IntOpenHashSet _set;
    protected int _lengthSquared = 0;

    public NGramVector() {
        this(BaseNGramVector.EXPECTED_NGRAM_COUNT);
    }
    
    public NGramVector(int ngramCount) {
        _set = new IntOpenHashSet(ngramCount);
    }
    
    public NGramVector(NGramVector vector) {
        _set = new IntOpenHashSet(vector._set);
        _lengthSquared = vector.getLengthSquared();
    }
    
    @Override
    public int get(int hash) {
        if (_set.contains(hash)) {
            return getLength(hash);
        } else {
            return 0;
        }
    }

    @Override
    public boolean set(int hash) {
       if (_set.add(hash)) {
           int length = getLength(hash);
           _lengthSquared += (length * length);
           return true;
       } else {
           return false;
       }
    }

    @Override
    public int size() {
        return _set.size();
    }

    @Override
    public void merge(BaseNGramVector vector) {
        NGramVector v = (NGramVector)vector;
        _set.addAll(v._set);
    }

    @Override
    public int getLengthSquared() {
        return _lengthSquared;
    }

    @Override
    public boolean contains(int hash) {
        return _set.contains(hash);
    }

    @Override
    public double score(BaseNGramVector o) {
        NGramVector v = (NGramVector)o;
        final IntOpenHashSet set = v._set;
        
        IntIterator iter;
        IntOpenHashSet otherSet;
        if (_set.size() < set.size()) {
            iter = _set.iterator();
            otherSet = set;
        } else {
            iter = set.iterator();
            otherSet = _set;
        }
        
        int dotProduct = 0;
        while (iter.hasNext()) {
            int hash = iter.nextInt();
            if (otherSet.contains(hash)) {
                int length = getLength(hash);
                dotProduct += (length * length);
            }
        }
        
        return (double)dotProduct / Math.sqrt((double)(getLengthSquared() * v.getLengthSquared()));
    }

    public void clear() {
        _set.clear();
        _lengthSquared = 0;
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(String.format("Vector of size %d:\n", _set.size()));
        IntIterator iter = _set.iterator();
        while (iter.hasNext()) {
            result.append('\t');
            result.append(iter.nextInt());
            result.append('\n');
        }
        
        return result.toString();
    }

}
