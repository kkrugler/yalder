package com.scaleunlimited.yald;

public class HashTokenizer extends BaseTokenizer {

    public HashTokenizer(CharSequence buffer, int minLength, int maxLength) {
        super(buffer, minLength, maxLength);
    }

    // Return the next hashed ngram.
    public int next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram hash to return");
        }

        // TODO use utility routine to combine raw hash with length.
        int hash = CharUtils.joaat_hash(_normalized, _normalizedPos, _curNGramSize);
        int result = (_curNGramSize << 29) | hash;

        expandNGram();

        return result;
    }

}
