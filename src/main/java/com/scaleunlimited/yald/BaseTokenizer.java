package com.scaleunlimited.yald;

public abstract class BaseTokenizer {
    private final CharSequence _buffer;
    private int _bufferPos; // Position we're at in _buffer for getting raw
                            // (unnormalized) chars.

    protected int _minLength;
    protected int _maxLength;

    protected char[] _normalized;   // Normalized results, which might be less than
                                    // what's in _buffer.
    protected int _normalizedLength; // Length of _normalized data.
    protected int _normalizedPos;   // Position we're at in _normalized for
                                    // generating ngrams

    protected int _curNGramSize;

    public BaseTokenizer(CharSequence buffer, int minLength, int maxLength) {
        _buffer = buffer;
        _minLength = minLength;
        _maxLength = maxLength;

        _bufferPos = 0;

        // TODO only allocate up to a reasonable limit (say 8K), and expand as
        // needed (should be rare)
        _normalized = new char[_buffer.length()];
        _normalizedLength = 0;
        _normalizedPos = 0;

        _curNGramSize = _minLength;
    }

    protected int getNumNormalizedChars() {
        return _normalizedLength - _normalizedPos;
    }

    protected void loadChars() {
        while ((_bufferPos < _buffer.length()) && (getNumNormalizedChars() < _maxLength)) {
            // TODO normalize blocks of text to a single character...need to flag somehow
            char curChar = _buffer.charAt(_bufferPos++);

            boolean charIsWhitespace = false;
            if (curChar < 0x80) {
                if (!Character.isLetter(curChar)) {
                    curChar = ' ';
                    charIsWhitespace = true;
                }
            } else if (Character.isWhitespace(curChar)) {
                curChar = ' ';
                charIsWhitespace = true;
            }

            boolean prevIsWhitespace = (_normalizedLength > 0)
                            && Character.isWhitespace(_normalized[_normalizedLength - 1]);
            if (prevIsWhitespace && charIsWhitespace) {
                continue;
            }

            _normalized[_normalizedLength++] = Character.toLowerCase(curChar);
        }
    }

    public boolean hasNext() {
        loadChars();

        // If we don't have enough chars for the current size, but we could if
        // we went for a shorter ngram, reset to min and advance.
        if ((getNumNormalizedChars() < _curNGramSize) && (_curNGramSize > _minLength)
                        && (_normalizedPos < _normalizedLength)) {
            _curNGramSize = _minLength;
            _normalizedPos += 1;
        }

        return getNumNormalizedChars() >= _curNGramSize;
    }

    // TODO make this part of hasNext(), which means hasNext() must set up
    // a separate current pos/length variable pair for normalized data that
    // can be used by next().
    protected void expandNGram() {
        // Expand the ngram size, but shift forward if we're at our max.
        if (_curNGramSize < _maxLength) {
            _curNGramSize += 1;
        } else {
            _normalizedPos += 1;
            _curNGramSize = _minLength;
        }
    }
}
