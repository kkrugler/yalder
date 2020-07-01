package com.scaleunlimited.yalder.old;

import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.Arrays;

import com.scaleunlimited.yalder.BaseNGramVector;

public class NGramVector extends BaseNGramVector {

    // TODO support serialization using efficient format.

    // We use a more efficient format here of sorted array of hash codes, where high 3 bits
    // are for the length of the ngram. The length of 5 is what we use for a collapsed
    // character one-gram, to distinguish it from a regular single character.
    
    protected int[] _terms;
    protected int _numTerms;
    protected int _lengthSquared;
    
    public NGramVector() {
        this(EXPECTED_NGRAM_COUNT);
    }
    
    public NGramVector(int ngramCount) {
        _terms = new int[ngramCount];
        _numTerms = 0;
        _lengthSquared = 0;
    }
    
    public NGramVector(NGramVector source) {
        _numTerms = source._numTerms;
        _terms = new int[_numTerms];
        System.arraycopy(source._terms, 0, _terms, 0, _numTerms);
        _lengthSquared = source._lengthSquared;
    }
    
    @Override
    public int get(int hash) {
        // 
        int index = getIndex(hash);
        if (index < 0) {
            return 0;
        } else {
            // TODO use mapping table to convert length to weight
            return getLength(_terms[index]);
        }
    }
    
    @Override
    public boolean set(int hash) {
        int index = getIndex(hash);
        if (index >= 0) {
            return false;
        } else {
            insert(hash, -index - 1);
            int length = getLength(hash);
            // TODO use mapping table to convert length to weight
            _lengthSquared += (length * length);
            return true;
        }
    }
    
    /**
     * Special version that is used when the caller knows the position
     * to insert <hash>.
     * 
     * @param index
     * @param hash
     */
    protected void set(int index, int hash) {
        _terms[index] = hash;
        _numTerms = Math.max(_numTerms, index + 1);
        
        int length = getLength(hash);
        // TODO use mapping table to convert length to weight
        _lengthSquared += (length * length);
    }
    
    /**
     * Insert hash at position index, expanding the vector
     * as needed.
     * 
     * @param hash
     * @param index
     */
    private void insert(int hash, int index) {
        // See if we need to expand the vector.
        if (_terms.length == _numTerms) {
            int[] newVector = new int[(_numTerms * 3) / 2];
            System.arraycopy(_terms, 0, newVector, 0, _numTerms);
            _terms = newVector;
        }
        
        // Make room, and do the insert.
        System.arraycopy(_terms, index, _terms, index + 1, _numTerms - index);
        _terms[index] = hash;
        _numTerms += 1;
    }

    /**
     * Return the dot product of this vector with <o>, where we need to normalize
     * the length of each vector to be one.
     * 
     * @param o
     * @return
     */
    @Override
    public double score(BaseNGramVector vector) {
        NGramVector o = (NGramVector)vector;
        
        // Iterate over the two vectors, multiplying any value that
        // exists in in both
        int thisIndex = 0;
        int thatIndex = 0;
        
        int[] thisVector;
        int thisLimit;
        int[] thatVector;
        int thatLimit;
        if (_numTerms < o._numTerms) {
            thisVector = _terms;
            thisLimit = _numTerms;
            thatVector = o._terms;
            thatLimit = o._numTerms;
        } else {
            thisVector = o._terms;
            thisLimit = o._numTerms;
            thatVector = _terms;
            thatLimit = _numTerms;
        }
        
        int dotProduct = 0;
        while (thisIndex < thisLimit) {
            int thisHash = thisVector[thisIndex++];
            
            while ((thatIndex < thatLimit) && (thatVector[thatIndex] < thisHash)) {
                thatIndex ++;
            }
            
            if (thatIndex == thatLimit) {
                break;
            } else if (thisHash == thatVector[thatIndex]) {
                int length = getLength(thisHash);
                // TODO use mapping table to convert length to weight
                dotProduct += (length * length);
            }
        }
        
        return (double)dotProduct / Math.sqrt((double)(_lengthSquared * o._lengthSquared));
    }

    /**
     * Merge all values from <termVector> into this vector.
     * 
     * @param termVector
     */
    @Override
    public void merge(BaseNGramVector o) {
        NGramVector vector = (NGramVector)o;
        for (int i = 0; i < vector._numTerms; i++) {
            set(vector._terms[i]);
        }
    }

    @Override
    public int getLengthSquared() {
        return _lengthSquared;
    }

    @Override
    public int size() {
        return _numTerms;
    }

    @Override
    public boolean contains(int hash) {
        return getIndex(hash) >= 0;
    }

    protected int getIndex(int hash) {
        return Arrays.binarySearch(_terms, 0, _numTerms, hash);
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(String.format("Vector of size %d:\n", _numTerms));
        for (int i = 0; i < _numTerms; i++) {
            result.append('\t');
            result.append(_terms[i]);
            result.append('\n');
        }
        
        return result.toString();
    }

}
