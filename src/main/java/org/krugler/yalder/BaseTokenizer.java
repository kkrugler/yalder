package org.krugler.yalder;


public abstract class BaseTokenizer {
    
    private static final char[] CHARMAP = new char[65536];

    static {

        for (int i = 0; i < 0x10000; i++) {
            char ch = (char)i;
            
            if ((i < 0x80) && !Character.isLetter(ch)) {
                CHARMAP[i] = ' ';
            } else if (Character.isWhitespace(ch)) {
                CHARMAP[i] = ' ';
            } else {
                // TODO allow models to provide their own re-mapping data, and move Japanese/Korean support there
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
                    // TODO what about math, dingbats, etc? Is there a general way to detect
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
    
    private final CharSequence _buffer;
    private int _bufferPos; // Position we're at in _buffer for getting raw
                            // (unnormalized) chars.

    protected int _maxNGramLength;

    protected char[] _normalized;   // Normalized results.
    protected int _normalizedPos;   // Start of current ngram in _normalized buffer.
    protected int _normalizedLength; // Length of _normalized data.

    protected int _curNGramLength;

    public BaseTokenizer(CharSequence buffer, int maxNGramLength) {
        _buffer = buffer;
        _maxNGramLength = maxNGramLength;

        _bufferPos = 0;

        // TODO start with a space in the buffer?
        _normalized = new char[1000];
        _normalized[0] = ' ';
        _normalizedLength = 1;
        _normalizedPos = 0;

        _curNGramLength = 1;
    }

    protected void fillNormalized() {
        while ((_bufferPos <= _buffer.length()) && (_normalizedLength < _curNGramLength)) {
            char curChar;
            if (_bufferPos < _buffer.length()) {
                curChar = CHARMAP[_buffer.charAt(_bufferPos++)];
            } else {
                curChar = ' ';
                _bufferPos += 1;
            }
            
            // If we have two spaces in a row, skip this character.
            if ((curChar == ' ')
             && (_normalizedLength > 0)
             && (CHARMAP[_normalized[_normalizedPos + _normalizedLength - 1]] == ' ')) {
                continue;
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
        fillNormalized();
        
        // Check if we have reached the end of the buffer, but we've got enough chars left
        // to continue with short ngrams
        if ((_curNGramLength > _normalizedLength) && (_curNGramLength > 1)) {
            advanceNGram();
        }
        
        return _curNGramLength <= _normalizedLength;
    }

    // Called by the next() method from the tokenizers after they've returned the current ngram, thus setting
    // up for subsequent hasNext()/next() calls.
    protected void nextNGram() {
        _curNGramLength += 1;
        
        if (_curNGramLength > _maxNGramLength) {
            advanceNGram();
       }
    }

    private void advanceNGram() {
        _normalizedPos += 1;
        _normalizedLength -= 1;
        _curNGramLength = 1;
    }
}
