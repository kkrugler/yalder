package com.scaleunlimited.yalder;

import java.util.Collection;

/**
 * Encapsulation of model about a given language. This consists of
 * a weighted term vector, where the terms are the hash values for
 * n-grams of various lengths, and the weights are based on the length
 * of the n-gram.
 * 
 * The terms are based on "key n-grams" when comparing this language
 * to either (a) all other languages we support, or (b) one specific
 * language where we need more precise comparison values.
 * 
 * @author kenkrugler
 *
 */

public class LanguageModel {

    private String _modelLanguage;
    private String _pairwiseLanguage;  // Null if this model is against all other languages
    
    private NGramVector _vector;
    
    public LanguageModel(String modelLanguage, String pairwiseLanguage, NGramVector vector) {
        _modelLanguage = modelLanguage;
        _pairwiseLanguage = pairwiseLanguage;
        _vector = vector;
    }
    
    public static NGramVector createComboVector(Collection<LanguageModel> models) {
        NGramVector result = new NGramVector();
        
        for (LanguageModel model : models) {
            NGramVector vector = model.getVector();
            result.merge(vector);
        }
        
        return result;
    }
    
    public String getLanguage() {
        return _modelLanguage;
    }
    
    public String getPairwiseLanguage() {
        return _pairwiseLanguage;
    }
    
    public NGramVector getVector() {
        return _vector;
    }
    
    /**
     * Calculate a score by comparing the target vector against this language model
     * 
     * @param target Vector for arbitrary document
     * @return
     */
    public double compare(NGramVector target) {
        return target.score(_vector);
    }
    
    /**
     * Calculate a score by comparing the term vectors for two models.
     * 
     * @param o The other language model
     * @return
     */
    public double compare(LanguageModel o) {
        // Complain if this model is against a specific pairwise language and
        // the other model isn't, or vice versa, or if they're both
        // for a pairwise language that's not the same (must be crosslinked,
        // e.g. en->es has to be compared with es->en)
        if (_pairwiseLanguage == null) {
            if (o._pairwiseLanguage != null) {
                throw new IllegalArgumentException("Can't compare general language model with explicit language model");
            }
        } else if (o._pairwiseLanguage == null) {
            throw new IllegalArgumentException("Can't compare explicit language model with general language model");
        } else if (!_pairwiseLanguage.equals(o._modelLanguage) || !o._pairwiseLanguage.equals(_modelLanguage)) {
            throw new IllegalArgumentException("Can't compare two explicit language models with different \"other\" languages");
        }

        return compare(o._vector);
    }

    public boolean isPairwise() {
        return _pairwiseLanguage != null;
    }
    
    @Override
    public String toString() {
        return String.format("'%s' vs '%s': %d terms", _modelLanguage, _pairwiseLanguage == null ? "all" : _pairwiseLanguage, _vector.size());
    }

}
