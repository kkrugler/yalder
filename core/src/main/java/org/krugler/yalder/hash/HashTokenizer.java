package org.krugler.yalder.hash;

import org.krugler.yalder.BaseTokenizer;
import org.krugler.yalder.CharUtils;

public class HashTokenizer extends BaseTokenizer {

    public HashTokenizer(int maxNGramLength) {
        super(maxNGramLength);
    }
    
    public HashTokenizer(String buffer, int maxNGramLength) {
        super(maxNGramLength);
        addText(buffer);
    }

    public HashTokenizer(char[] buffer, int offset, int length, int maxNGramLength) {
        super(maxNGramLength);
        addText(buffer, offset, length);
    }

    // Return the next hashed ngram.
    public int next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram hash to return");
        }

        int hash = calcHash(_normalized, _normalizedPos, _curNGramLength);

        nextNGram();

        return hash;
    }
    
    protected static int calcHash(char[] buffer, int pos, int length) {
        return CharUtils.joaat_hash(buffer, pos, length);
    }
    
    protected static int calcHash(String buffer) {
        return calcHash(buffer.toCharArray(), 0, buffer.length());
    }
}
