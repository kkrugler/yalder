package org.krugler.yalder.text;

import java.nio.CharBuffer;

import org.krugler.yalder.BaseTokenizer;

public class TextTokenizer extends BaseTokenizer {

    public TextTokenizer(CharSequence buffer, int max) {
        super(buffer, max);
    }

    // Return the next ngram.
    public String next() {
        if (!hasNext()) {
            throw new IllegalStateException("No next ngram to return");
        }

        CharSequence result = CharBuffer.wrap(_normalized, _normalizedPos, _curNGramLength);

        nextNGram();

        return result.toString();
    }

}

