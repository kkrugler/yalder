package com.scaleunlimited.yalder;


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

    private String _modelLanguage;      // ISO 639-1 code
    
    private int[] _ngrams;
    private float[] _probabilities;
        
    public LanguageModel(String modelLanguage, int[] ngrams, float[] probabilities) {
        _modelLanguage = modelLanguage;
        _ngrams = ngrams;
        _probabilities = probabilities;
        
        if (ngrams.length != probabilities.length) {
            throw new IllegalArgumentException("Length of ngrams and probabilities arrays must be equal");
        }
    }
    
    public String getLanguage() {
        return _modelLanguage;
    }
    
    public int[] getNGrams() {
        return _ngrams;
    }
    
    public float[] getProbabilities() {
        return _probabilities;
    }
    
    @Override
    public String toString() {
        return String.format("'%s': %d ngrams", _modelLanguage, _ngrams.length);
    }

}
