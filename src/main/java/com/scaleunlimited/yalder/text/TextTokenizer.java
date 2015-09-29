package com.scaleunlimited.yalder.text;

import java.nio.CharBuffer;

import com.scaleunlimited.yalder.BaseTokenizer;

public class TextTokenizer extends BaseTokenizer {

    public TextTokenizer(CharSequence buffer, int min, int max) {
        super(buffer, min, max);
    }

    // Return the next ngram.
    public String next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram to return");
        }

        CharSequence result = CharBuffer.wrap(_normalized, _normalizedPos, _curNGramSize);

        expandNGram();

        return result.toString();
    }

}

