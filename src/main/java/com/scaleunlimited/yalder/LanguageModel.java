package com.scaleunlimited.yalder;

import java.util.Map;


/**
 * Encapsulation of model about a given language. This consists of
 * a list of ngrams (stored as 4-byte ints) and probabilities (floats).
 * The probability for an ngram is for it being that language, versus
 * all of the other known languages - so this has to be adjusted such
 * that the probabilities sum to 1.0 for the set of loaded languages.
 * 
 * Each ngram int is a right-flush (towards LSB) value, representing
 * packed character codes. So we can have a single character (e.g. 'a'
 * is stored as 0x00000061), or four characters (e.g. 'abcd' is stored
 * as '0x61626364'), or a single character that requires two bytes
 * (e.g. Hiragana 'a' is stored as 0x00003040), or any mix of one and
 * two byte values that fits into four bytes.
 * 
 * @author kenkrugler
 *
 */

public class LanguageModel {

    // The normalized counts are relative to this count, so that we
    // can combine languages built with different amounts of data.
    public static final int NORMALIZED_COUNT = 1000000;

    private String _modelLanguage;      // ISO 639-1 code
    
    private int _maxNGramLength;
    
    private Map<CharSequence, Integer> _normalizedCounts;
    
    public LanguageModel(String modelLanguage, int maxNGramLength, Map<CharSequence, Integer> normalizedCounts) {
        _modelLanguage = modelLanguage;
        _maxNGramLength = maxNGramLength;
        _normalizedCounts = normalizedCounts;
    }
    
    public String getLanguage() {
        return _modelLanguage;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public int getNGramCount(CharSequence ngram) {
        Integer result = _normalizedCounts.get(ngram);
        return result == null ? 0 : result;
    }
    
    public Map<CharSequence, Integer> getNGramCounts() {
        return _normalizedCounts;
    }
    
    @Override
    public String toString() {
        return String.format("'%s': %d ngrams", _modelLanguage, _normalizedCounts.size());
    }

}
