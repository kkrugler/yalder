package com.scaleunlimited.yalder.hash;

import com.scaleunlimited.yalder.BaseTokenizer;
import com.scaleunlimited.yalder.CharUtils;

public class HashTokenizer extends BaseTokenizer {

    public HashTokenizer(CharSequence buffer, int minLength, int maxLength) {
        super(buffer, minLength, maxLength);
    }

    // Return the next hashed ngram.
    public int next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram hash to return");
        }

        int hash = CharUtils.joaat_hash(_normalized, _normalizedPos, _curNGramSize);

        expandNGram();

        return hash;
    }

}
