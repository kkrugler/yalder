package org.krugler.yalder.text;

import java.nio.CharBuffer;

import org.krugler.yalder.BaseTokenizer;

public class TextTokenizer extends BaseTokenizer {

    public TextTokenizer(String text, int maxNGramLength) {
        this(text.toCharArray(), 0, text.length(), maxNGramLength);
    }

    public TextTokenizer(char[] buffer, int offset, int length, int maxNGramLength) {
        super(maxNGramLength);
        addText(buffer, offset, length);
        complete();
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

