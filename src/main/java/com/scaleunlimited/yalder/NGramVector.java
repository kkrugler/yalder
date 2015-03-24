package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import com.scaleunlimited.yalder.BaseNGramVector;

public class NGramVector extends BaseNGramVector {

    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 7;
    private static final double WEIGHT_RANGE = MAX_WEIGHT - MAX_WEIGHT;

    // TODO support serialization using efficient format.

    private static final int CONTAINS_BITSET_SIZE = 64 * 1024;

    private static final int CONTAINS_BITSET_MASK = 0x00FFFF;
    
    // We use a more efficient format here of sorted array of hash codes, where low 3 bits
    // are for the weight of the ngram. So to convert a hash + a weight into a term value
    // in the vector, we knock out the low 3 bits of the hash and insert the weight bits.
    // Which means we have to be careful with the "contains" bitset, as we need to shift
    // right before taking the number of bits we want.
    
    protected int[] _terms;
    protected int _numTerms;
    protected int _lengthSquared;
    private BitSet _contains;

    public NGramVector() {
        this(EXPECTED_NGRAM_COUNT);
    }
    
    public NGramVector(int ngramCount) {
        _terms = new int[ngramCount];
        _numTerms = 0;
        _lengthSquared = 0;
        
        _contains = new BitSet(CONTAINS_BITSET_SIZE);
    }
    
    public NGramVector(NGramVector source) {
        _numTerms = source._numTerms;
        _terms = new int[_numTerms];
        System.arraycopy(source._terms, 0, _terms, 0, _numTerms);
        _lengthSquared = source._lengthSquared;
        
        _contains = new BitSet(CONTAINS_BITSET_SIZE);
        _contains.or(source._contains);
    }
    
    private static int makeTerm(int hash, int weight) {
        return hash | weight;
    }
    
    /**
     * Return the hash without the weight.
     * 
     * @param term
     * @return
     */
    public static int makeHash(int term) {
        return (term & ~0x07);
    }
    
    public static int makeWeight(int term) {
        // TODO use table to map from 1..7 "raw weight" to true weight
        return term & 0x07;
    }
    
    @Override
    public int get(int hash) {
        int index = getIndex(hash);
        if (index < _numTerms) {
            int term = _terms[index];
            if (makeHash(term) == hash) {
                return makeWeight(term);
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }
    
    /* (non-Javadoc)
     * @see com.scaleunlimited.yalder.BaseNGramVector#set(int, int)
     */
    @Override
    public boolean set(int hash, int weight) {
        if ((weight < 1) || (weight > 7)) {
            throw new IllegalArgumentException("Weight must be 1..7, got " + weight);
        }

        int index = getIndex(hash);
        if (index < _numTerms) {
            // TODO you can set the same hash with a different weight, which is bad.
            // We'd have to see what's at the -index - 1 position and see if the hash
            // matches, and if so complain.
            if (makeHash(_terms[index]) == hash) {
                return false;
            }
        }

        int term = makeTerm(hash, weight);
        insert(term, index);
        return true;
    }
    
    /**
     * Insert term at position index, expanding the vector
     * as needed.
     * 
     * @param hash
     * @param index
     */
    private void insert(int term, int index) {
        // See if we need to expand the vector.
        if (_terms.length == _numTerms) {
            int[] newVector = new int[(_numTerms * 3) / 2];
            System.arraycopy(_terms, 0, newVector, 0, _numTerms);
            _terms = newVector;
        }
        
        // Make room, and do the insert.
        System.arraycopy(_terms, index, _terms, index + 1, _numTerms - index);
        _terms[index] = term;
        _numTerms += 1;
        
        int weight = makeWeight(term);
        _lengthSquared += (weight * weight);
        
        _contains.set(makeBitsetPos(term));
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
            int thisTerm = thisVector[thisIndex++];
            int thisHash = makeHash(thisTerm);
            
            while ((thatIndex < thatLimit) && (makeHash(thatVector[thatIndex]) < thisHash)) {
                thatIndex ++;
            }
            
            if (thatIndex == thatLimit) {
                break;
            } else if (thisHash == makeHash(thatVector[thatIndex])) {
                int thisWeight = makeWeight(thisTerm);
                int thatWeight = makeWeight(thatVector[thatIndex]);
                
                // TODO use mapping table to convert raw to scaled weights
                dotProduct += (thisWeight * thatWeight);
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
            int term = vector._terms[i];
            set(makeHash(term), makeWeight(term));
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

    private int makeBitsetPos(int hash) {
        return (hash >> 3) & CONTAINS_BITSET_MASK;
    }
    
    @Override
    public boolean contains(int hash) {
        if (_contains.get(makeBitsetPos(hash))) {
            int index = getIndex(hash);
            if (index < _numTerms) {
                return makeHash(_terms[index]) == hash;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected int getIndex(int hash) {
        int result = Arrays.binarySearch(_terms, 0, _numTerms, hash);
        if (result >= 0) {
            throw new IllegalStateException("Found term with 0 weight for hash " + hash);
        }
        
        return -result - 1;
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(String.format("Vector of size %d:\n", _numTerms));
        for (int i = 0; i < _numTerms; i++) {
            result.append('\t');
            result.append(makeHash(_terms[i]));
            result.append(", ");
            result.append(makeWeight(_terms[i]));
            result.append('\n');
        }
        
        return result.toString();
    }

    @Override
    public Iterator<Integer> getIterator() {
        List<Integer> result = new ArrayList<Integer>(_numTerms);
        for (int i = 0; i < _numTerms; i++) {
            result.add(_terms[i]);
        }
        
        return result.iterator();
    }

    @Override
    public void clear() {
        _numTerms = 0;
        _lengthSquared = 0;
        _contains.clear();
    }

    /**
     * Convert double value 0.0 -> 1.0 into a quantized weight
     * that is 1 -> 7
     * 
     * @param value
     * @return quantized value
     */
    @Override
    public int quantizeWeight(double weight) {
        if ((weight < 0.0) || (weight > 1.0)) {
            throw new IllegalArgumentException("Value to be quantized must be 0.0 to 1.0, got " + weight);
        }
        
       return (int)Math.round(MIN_WEIGHT + (WEIGHT_RANGE * weight));
    }

}
