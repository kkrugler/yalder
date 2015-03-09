package com.scaleunlimited.yalder;

import java.nio.CharBuffer;

public class NGramTokenizer extends BaseTokenizer {

    public NGramTokenizer(CharSequence buffer, int min, int max) {
        super(buffer, min, max);
    }

    // Return the next ngram.
    public CharSequence next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram to return");
        }

        CharSequence result = CharBuffer.wrap(_normalized, _normalizedPos, _curNGramSize);

        expandNGram();

        return result;
    }

}

