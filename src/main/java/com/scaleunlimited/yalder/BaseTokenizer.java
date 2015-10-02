package com.scaleunlimited.yalder;

import it.unimi.dsi.fastutil.chars.Char2CharOpenHashMap;

public abstract class BaseTokenizer {
    
    private static final Char2CharOpenHashMap CHARMAP = new Char2CharOpenHashMap();
    static {
        CHARMAP.defaultReturnValue('\0');
        
        for (int i = 0; i < 0x80; i++) {
            char c = (char)i;
            if (!Character.isLetter(c)) {
                CHARMAP.put(c, ' ');
            }
        }
        
        for (int i = 0x80; i < 0xFFFF; i++) {
            char c = (char)i;
            if (Character.isWhitespace(c)) {
                CHARMAP.put(c, ' ');
            }
        }
        
        CHARMAP.put('»', ' ');
        CHARMAP.put('«', ' ');
        CHARMAP.put('º', ' ');
        CHARMAP.put('°', ' ');
        
        CHARMAP.put('–', ' ');
        CHARMAP.put('―', ' ');
        CHARMAP.put('—', ' ');
        
        CHARMAP.put('”', ' ');
        CHARMAP.put('“', ' ');
        
        CHARMAP.put('’', ' ');
        CHARMAP.put('‘', ' ');
        
        CHARMAP.put('‚', ' ');
        CHARMAP.put('‛', ' ');
        
        CHARMAP.put('„', ' ');
        CHARMAP.put('‟', ' ');
        
        CHARMAP.put('½', ' ');
        CHARMAP.put('…', ' ');
        
        CHARMAP.put('\u00A0', ' ');    // Non-breaking space
        CHARMAP.put('\u00AD', ' ');    // SOFT HYPHEN
        CHARMAP.put('\u2022', ' ');    // Bullet
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
        _normalizedLength = 0;
        _normalizedPos = 0;

        _curNGramLength = 1;
    }

    protected void fillNormalized() {
        // while ((_bufferPos < _buffer.length()) && (_normalizedLength < _curNGramLength)) {
        while ((_bufferPos < _buffer.length()) && (_normalizedLength < _curNGramLength)) {
            // TODO normalize blocks of text to a single character...need to flag somehow
            char curChar = _buffer.charAt(_bufferPos++);
            char newChar = CHARMAP.get(curChar);
            if (newChar != 0) {
                curChar = newChar;
            }
            
            // If we have two spaces in a row, skip this character.
            if ((curChar == ' ')
             && (_normalizedLength > 0)
             && (CHARMAP.get(_normalized[_normalizedPos + _normalizedLength - 1]) == ' ')) {
                continue;
            }

            // Reset the buffer positions if we're at the end.
            if (_normalizedPos + _normalizedLength >= _normalized.length) {
                System.arraycopy(_normalized, _normalizedPos, _normalized, 0, _normalizedLength);
                _normalizedPos = 0;
            }
            
            _normalized[_normalizedPos + _normalizedLength] = Character.toLowerCase(curChar);
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
