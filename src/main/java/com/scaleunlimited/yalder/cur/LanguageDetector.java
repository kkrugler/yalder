package com.scaleunlimited.yalder.cur;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.HashTokenizer;

public class LanguageDetector {

    private Collection<LanguageModel> _models;
    private MasterNGramVector _modelNGrams;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        _models = models;
        
        _modelNGrams = new MasterNGramVector(LanguageModel.createComboVector(_models));
        
        // System.out.println(_modelNGrams);
    }
    
    public Collection<DetectionResult> detect(CharSequence text) {
        _modelNGrams.clearMarks();
        
        // TODO stop processing text when the ratio of new ngrams to old ngrams drops too low.
        int totalNGrams = 0;
        int goodNGrams = 0;
        HashTokenizer tokenizer = new HashTokenizer(text, ModelBuilder.MIN_NGRAM_LENGTH, ModelBuilder.MAX_NGRAM_LENGTH);
        while (tokenizer.hasNext()) {
            totalNGrams += 1;
            
            int hash = tokenizer.next();
            if (_modelNGrams.mark(hash)) {
                goodNGrams += 1;
            }
        }
        
        System.out.println(String.format("%d total ngrams, %d good ngrams", totalNGrams, goodNGrams));
        
        NGramVector target = _modelNGrams.makeVector();
        // System.out.println(target);
        
        // We now have <target> that we can compare to our set of models.
        List<DetectionResult> result = new ArrayList<DetectionResult>(_models.size());
        for (LanguageModel model : _models) {
            // Skip model if it's a specific lang-lang model (e.g. en versus es model)
            // We only want to use those if the top two entries are too close.
            if (model.isPairwise()) {
                continue;
            }
            
            double score = model.compare(target);
            result.add(new DetectionResult(model.getLanguage(), score));
        }
        
        // Sort the results from high to low score.
        Collections.sort(result);
        
        // Calculate confidence based on absolute score and delta from next closest.
        // TODO use absolute score here as a factor?
        DetectionResult topResult = result.get(0);
        double topScore = topResult.getScore();
        DetectionResult nextResult = result.get(1);
        double nextScore = nextResult.getScore();
        double delta = topScore - nextScore;
        
        // Confidence is 1.0 if top score is more than 2x the next score, or 0.0 if they
        // are the same.
        double topConfidence = Math.min(1.0, delta/nextScore);
        
        // If top two entries are too close (topConfidence is too low), see if we have a 
        // more specific model that can be used to compare.
        // TODO reenable this? Was 0.10 level
        if (topConfidence <= 0.00) {
            String topLanguage = topResult.getLanguage();
            String nextLanguage = nextResult.getLanguage();
            if (hasSpecificModel(topLanguage, nextLanguage)) {
                // Apply more specific scoring
                double topRescored = getSpecificModel(topLanguage, nextLanguage).compare(target);
                double nextRescored = getSpecificModel(nextLanguage, topLanguage).compare(target);
                
                if (nextRescored > topRescored) {
                    System.out.println(String.format("Order changed using pairwise scoring for '%s' = %f and '%s' = %f", topLanguage, topRescored, nextLanguage, nextRescored));
                    // TODO  adjust the scores using something better than just swapping them. Then we'll need to recalc topConfidence,
                    // or do it in the loop below.
                    topResult.setScore(nextScore);
                    nextResult.setScore(topScore);
                    result.set(0, nextResult);
                    result.set(1, topResult);
                }
            }
        }
        
        for (int i = 0; i < result.size(); i++) {
            DetectionResult dr = result.get(i);
            if (i == 0) {
                dr.setConfidence(topConfidence);
            } else {
                double confidence = topConfidence * (dr.getScore() / topScore);
                dr.setConfidence(confidence);
            }
        }
        
        return result;
    }

    public LanguageModel getSpecificModel(String language, String pairwiseLanguage) {
        for (LanguageModel model : _models) {
            if (model.isPairwise()) {
                if (language.equals(model.getLanguage()) && pairwiseLanguage.equals(model.getPairwiseLanguage())) {
                    return model;
                }
            }
        }
        
        return null;
    }
        
    public boolean hasSpecificModel(String language, String pairwiseLanguage) {
        return (getSpecificModel(language, pairwiseLanguage) != null) &&
        (getSpecificModel(pairwiseLanguage, language) != null);
    }
}
