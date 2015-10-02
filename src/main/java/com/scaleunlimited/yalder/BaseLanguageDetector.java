package com.scaleunlimited.yalder;

import java.util.Collection;
import java.util.Iterator;

import com.scaleunlimited.yalder.text.TextLanguageModel;

public abstract class BaseLanguageDetector {

    public static final double DEFAULT_ALPHA = 0.000002;
    public static final double DEFAULT_DAMPENING = 0.001;

    protected static final double MIN_LANG_PROBABILITY = 0.1;
    protected static final double MIN_GOOD_LANG_PROBABILITY = 0.99;
    
    protected int _maxNGramLength;
    
    // The probability we use for an ngram when we have no data for it for a given language.
    protected double _alpha;
    
    // Used to increase an ngram's probability, to reduce rapid swings in short text
    // snippets. The probability is increased by (1 - prob) * dampening.
    protected double _dampening = DEFAULT_DAMPENING;

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

    public abstract Collection<DetectionResult> detect(String text);
}
