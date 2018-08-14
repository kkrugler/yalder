package org.krugler.yalder;


public abstract class BaseTokenizer {
    
    private static final int STARTING_BUFFER_SIZE = 1024;
    private static final int NEW_BUFFER_SLOP = 128;

    private static final char[] CHARMAP = new char[65536];

    static {

        for (int i = 0; i < 0x10000; i++) {
            char ch = (char)i;
            
            if ((i < 0x80) && !Character.isLetter(ch)) {
                CHARMAP[i] = ' ';
            } else if (Character.isWhitespace(ch)) {
                CHARMAP[i] = ' ';
            } else {
                // FUTURE allow models to provide their own re-mapping data, and move Japanese/Korean support there
                Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
                if ((block == Character.UnicodeBlock.KATAKANA)
                 || (block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS)) {
                    CHARMAP[i] = '\u30A2';
                } else if (block == Character.UnicodeBlock.HIRAGANA) {
                    CHARMAP[i] = '\u3042';
                } else if ((block == Character.UnicodeBlock.HANGUL_JAMO)
                        || (block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO)
                        || (block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A)
                        || (block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B)
                        || (block == Character.UnicodeBlock.HANGUL_SYLLABLES)) {
                    CHARMAP[i] = '\uAC00';
                } else {
                    // FUTURE what about math, dingbats, etc? Is there a general way to detect
                    // those and map to space?
                    CHARMAP[i] = Character.toLowerCase(ch);
                }
            }
        }
        
        CHARMAP['»'] = ' ';
        CHARMAP['«'] = ' ';
        CHARMAP['º'] = ' ';
        CHARMAP['°'] = ' ';
        
        CHARMAP['–'] = ' ';
        CHARMAP['―'] = ' ';
        CHARMAP['—'] = ' ';
        
        CHARMAP['”'] = ' ';
        CHARMAP['“'] = ' ';
        
        CHARMAP['’'] = ' ';
        CHARMAP['‘'] = ' ';
        
        CHARMAP['‚'] = ' ';
        CHARMAP['‛'] = ' ';
        
        CHARMAP['„'] = ' ';
        CHARMAP['‟'] = ' ';
        
        CHARMAP['½'] = ' ';
        CHARMAP['…'] = ' ';
        
        CHARMAP['\u00A0'] = ' ';    // Non-breaking space
        CHARMAP['\u00AD'] = ' ';    // SOFT HYPHEN
        CHARMAP['\u2022'] = ' ';    // Bullet
    }
    
    private char[] _buffer;
    private int _bufferLen; // Number of remaining chars in buffer.
    private int _bufferPos; // Position we're at in _buffer for getting raw
                            // (unnormalized) chars.
    private boolean _completed;
    
    protected int _maxNGramLength;

    protected char[] _normalized;   // Normalized results.
    protected int _normalizedPos;   // Start of current ngram in _normalized buffer.
    protected int _normalizedLength; // Length of _normalized data.
    private boolean _normalizedSpace;   // True if last normalized char was a space.
    
    protected int _curNGramLength;

    public BaseTokenizer(int maxNGramLength) {
        _maxNGramLength = maxNGramLength;
        _buffer = new char[STARTING_BUFFER_SIZE];
        
        reset();
    }

    public void reset() {
        _bufferLen = 0;
        _bufferPos = 0; 
        
       _normalized = new char[1000];
        _normalizedLength = 0;
        _normalizedPos = 0;
        _normalizedSpace = false;
        
        _curNGramLength = 1;
        _completed = false;
        
        // Start with a space in the buffer
        addText(new char[]{' '}, 0, 1);
    }
    
    public void addText(String text) {
        addText(text.toCharArray(), 0, text.length());
    }
    
    public void addText(char[] buffer, int offset, int length) {
        if (_completed) {
            throw new IllegalStateException("Can't call addText() after calling complete() without a reset()");
        }

        if (_bufferLen + length > _buffer.length) {
            char[] newBuffer = new char[_bufferLen + length + NEW_BUFFER_SLOP];
            System.arraycopy(_buffer, _bufferPos, newBuffer, 0, _bufferLen);
            _buffer = newBuffer;
            _bufferPos = 0;
        } else {
            // It will fit without reallocation, now see if we have to shift things down.
            if (_bufferPos + _bufferLen + length > _buffer.length) {
                System.arraycopy(_buffer, _bufferPos, _buffer, 0, _bufferLen);
                _bufferPos = 0;
            }
        }

        // Add the new characters to the end.
        System.arraycopy(buffer, offset, _buffer, _bufferPos + _bufferLen, length);
        _bufferLen += length;
    }
    
    /**
     * Flag that we're ready to process all of the text in the buffer (no more
     * addText calls will be made).
     */
    public void complete() {
        if (!_completed) {
            addText(new char[]{' '}, 0, 1);
            _completed = true;
        }
    }
    
    /**
     * Add normalized characters from _buffer to _normalized, until we have enough for
     * the current ngram we're trying to return, or we run out of (usable) characters
     * in the buffer.
     */
    protected void addNormalized() {
        while ((_bufferLen > 0) && (_normalizedLength < _curNGramLength)) {
            char curChar = CHARMAP[_buffer[_bufferPos++]];
            _bufferLen--;
            
            // If we have two spaces in a row, skip this character.
            if (curChar == ' ') {
                if (_normalizedSpace) {
                    continue;
                } else {
                    _normalizedSpace = true;
                }
            } else {
                _normalizedSpace = false;
            }

            // Reset the buffer positions if we're at the end.
            if (_normalizedPos + _normalizedLength >= _normalized.length) {
                System.arraycopy(_normalized, _normalizedPos, _normalized, 0, _normalizedLength);
                _normalizedPos = 0;
            }
            
            _normalized[_normalizedPos + _normalizedLength] = curChar;
            _normalizedLength += 1;
        }
    }

    public boolean hasNext() {
        addNormalized();
        
        // Check if we have reached the end of the buffer, but we've got enough chars left
        // to continue with short ngrams
        if ((_curNGramLength > _normalizedLength) && (_curNGramLength > 1) && _completed) {
            advanceNGram();
        }
        
        return _curNGramLength <= _normalizedLength;
    }

    // Called by the next() method from the tokenizers after they've returned the current ngram, thus setting
    // up for subsequent hasNext()/next() calls.
    protected void nextNGram() {
        _curNGramLength++;
        
        if (_curNGramLength > _maxNGramLength) {
            advanceNGram();
       }
    }

    private void advanceNGram() {
        _normalizedPos++;
        _normalizedLength--;
        _curNGramLength = 1;
    }
}
