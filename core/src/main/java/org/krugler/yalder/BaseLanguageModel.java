package org.krugler.yalder;

public abstract class BaseLanguageModel {

    // Version of model, for de-serialization
    public static final int MODEL_VERSION = 1;
    
    // The normalized counts are relative to this count, so that we
    // can combine languages built with different amounts of data.
    public static final int NORMALIZED_COUNT = 1000000;

    protected LanguageLocale _modelLanguage;
    
    protected int _maxNGramLength;
    
    public BaseLanguageModel() {
        
    }
    
    public BaseLanguageModel(LanguageLocale modelLanguage, int maxNGramLength) {
        _modelLanguage = modelLanguage;
        _maxNGramLength = maxNGramLength;
    }
    
    public LanguageLocale getLanguage() {
        return _modelLanguage;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    @Override
    public String toString() {
        return String.format("'%s': %d ngrams", _modelLanguage, size());
    }

    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
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
