package org.krugler.yalder;

public abstract class BaseLanguageModel {

    // Version of model, for de-serialization
    public static final int NO_ALPHA_MODEL_VERSION = 1;
    public static final int MODEL_VERSION = 2;
    
    // The normalized counts are relative to this count, so that we
    // can combine languages built with different amounts of data.
    public static final int NORMALIZED_COUNT = 1000000;

    protected LanguageLocale _modelLanguage;
    protected int _maxNGramLength;
    protected int _alpha;
    
    public BaseLanguageModel() {
    }
    
    public BaseLanguageModel(LanguageLocale modelLanguage, int maxNGramLength, int alpha) {
        _modelLanguage = modelLanguage;
        _maxNGramLength = maxNGramLength;
        _alpha = alpha;
    }
    
    public LanguageLocale getLanguage() {
        return _modelLanguage;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public int getAlpha() {
        return _alpha;
    }
    
    public void setAlpha(int alpha) {
        _alpha = alpha;
    }
    
    @Override
    public String toString() {
        return String.format("'%s': %d ngrams", _modelLanguage, size());
    }

    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + _alpha;
        result = prime * result + _maxNGramLength;
        result = prime * result + ((_modelLanguage == null) ? 0 : _modelLanguage.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BaseLanguageModel other = (BaseLanguageModel) obj;
        if (_alpha != other._alpha)
            return false;
        if (_maxNGramLength != other._maxNGramLength)
            return false;
        if (_modelLanguage == null) {
            if (other._modelLanguage != null)
                return false;
        } else if (!_modelLanguage.equals(other._modelLanguage))
            return false;
        return true;
    }

    public abstract int size();
    public abstract int prune(int minNormalizedCount);
}
