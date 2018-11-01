package org.krugler.yalder;

import java.util.Collection;
import java.util.Iterator;

public abstract class BaseLanguageDetector {

    // Probability for an ngram that doesn't exist in a language's model.
    // TODO currently if ngram is only in one language, then that language
    // probability is like 99.9%, which completely skews results - every
    // other language probability goes to almost 0.0%
    public static final double DEFAULT_ALPHA = 0.000002;
    
    public static final double DEFAULT_DAMPENING = 0.001;

    protected static final double MIN_LANG_PROBABILITY = 0.1;
    protected static final double MIN_GOOD_LANG_PROBABILITY = 0.99;

    protected static final int DEFAULT_RENORMALIZE_INTERVAL = 10;
    
    protected int _maxNGramLength;
    
    // The probability we use for an ngram when we have no data for it for a given language.
    protected double _alpha;
    
    // Applied to an ngram's probability, to reduce rapid swings in short text
    // snippets. The probability is increased by (1 - prob) * dampening, before
    // it's multiplied into the ngram's current probability for the text being
    // analyzed. A dampening of 1.0 would mean all probabilities are effectively
    // equal to 1.0, while a dampening of 0 means there's no adjustment.
    protected double _dampening = DEFAULT_DAMPENING;

    protected int _renormalizeInterval = DEFAULT_RENORMALIZE_INTERVAL;
    
    protected Collection<? extends BaseLanguageModel> _models;
    
    protected static int getMaxNGramLengthFromModels(Collection<? extends BaseLanguageModel> models) {
        int maxNGramLength = 0;
        Iterator<? extends BaseLanguageModel> iter = models.iterator();
        while (iter.hasNext()) {
            maxNGramLength = Math.max(maxNGramLength, iter.next().getMaxNGramLength());
        }
        
        return maxNGramLength;
    }

    public BaseLanguageDetector(Collection<? extends BaseLanguageModel> models, int maxNGramLength) {
        _models = models;
        _maxNGramLength = maxNGramLength;
        _alpha = DEFAULT_ALPHA;
    }
    
    public BaseLanguageDetector setAlpha(double alpha) {
        _alpha = alpha;
        return this;
    }
    
    public double getAlpha() {
        return _alpha;
    }
    
    public BaseLanguageDetector setDampening(double dampening) {
        _dampening = dampening;
        return this;
    }
    
    public double getDampening() {
        return _dampening;
    }
    
    public BaseLanguageDetector setRenormalizeInterval(int renormalizeInterval) {
        _renormalizeInterval = renormalizeInterval;
        return this;
    }
    
    public int getRenormalizeInterval() {
        return _renormalizeInterval;
    }
    
    /**
     * Return true if at least one of the loaded models is for a language that
     * is weakly equal to <target>. This means that (in theory) we could get a
     * detection result that would also be weakly equal to <target>.
     * 
     * @param target Language of interest
     * @return true if it's supported by the loaded set of models.
     */
    
    public boolean supportsLanguage(LanguageLocale target) {
        for (BaseLanguageModel model : _models) {
            if (model.getLanguage().weaklyEqual(target)) {
                return true;
            }
        }
        
        return false;
    }
    
    public BaseLanguageModel getModel(LanguageLocale language) {
        for (BaseLanguageModel model : _models) {
            
            if (model.getLanguage().equals(language)) {
                return model;
            }
            
            // TODO if weakly equal, and no other set to weak, save it
        }
        
        throw new IllegalArgumentException("Unknown language: " + language);
    }

    public abstract void reset();
    
    public abstract void addText(char[] text, int offset, int length);
    
    public void addText(String text) {
        addText(text.toCharArray(), 0, text.length());
    }
    
    public abstract Collection<DetectionResult> detect();
    
    public boolean hasEnoughText() {
        return false;
    }
    
}
