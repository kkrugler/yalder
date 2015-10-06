package org.krugler.yalder.hash;

import org.krugler.yalder.BaseTokenizer;
import org.krugler.yalder.CharUtils;

public class HashTokenizer extends BaseTokenizer {

    public HashTokenizer(CharSequence buffer, int maxLength) {
        super(buffer, maxLength);
    }

    // Return the next hashed ngram.
    public int next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram hash to return");
        }

        int hash = CharUtils.joaat_hash(_normalized, _normalizedPos, _curNGramLength);

        nextNGram();

        return hash;
    }

}
