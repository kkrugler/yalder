package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.scaleunlimited.yalder.MasterNGramVector.MarkResult;

public class LanguageDetector {

    private Collection<LanguageModel> _models;
    private MasterNGramVector _modelNGrams;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        _models = models;
        
        // TODO is it better to have a single master vector for all models, or should
        // we re-tokenize when we get one of our combo-results?
        _modelNGrams = new MasterNGramVector(LanguageModel.createComboVector(_models));
        
        // System.out.println(_modelNGrams);
    }
    
    public Collection<DetectionResult> detect(CharSequence text) {
        return detect(text, true);
    }
    
    public Collection<DetectionResult> detect(CharSequence text, boolean rescoreGroups) {
        _modelNGrams.clearMarks();
        
        // Stop processing text when the ratio of new ngrams to old ngrams drops too low.
        // I.e. every time we get N good ngrams, if less than M of those were new, stop.
        
        // FUTURE for some odd cases (e.g. text in language we don't support) we won't get
        // many good ngrams, so we could have a separate check for that case, as otherwise
        // we'll grind through the entire document collecting only a handful of not-very-
        // useful ngrams.
        int goodNGrams = 0;
        int newNGrams = 0;
        HashTokenizer tokenizer = new HashTokenizer(text, ModelBuilder.MIN_NGRAM_LENGTH, ModelBuilder.MAX_NGRAM_LENGTH);
        while (tokenizer.hasNext()) {
            
            int hash = tokenizer.next();
            MarkResult result = _modelNGrams.mark(hash);
            if (result != MarkResult.MISSING) {
                goodNGrams += 1;
                if (result == MarkResult.NEW) {
                    newNGrams += 1;
                }
                
                if ((goodNGrams % 20) == 0) {
                    if (newNGrams < 5) {
                        break;
                    }
                    
                    goodNGrams = 0;
                    newNGrams = 0;
                }
            }
        }
        
        NGramVector target = _modelNGrams.makeVector();
        
        // We now have <target> that we can compare to our set of models.
        List<DetectionResult> result = new ArrayList<DetectionResult>(_models.size());
        for (LanguageModel model : _models) {
            // Skip model if it's a specific lang-lang model (e.g. en versus es model)
            // We only want to use those if the top two entries are too close.
            if (model.isPairwise()) {
                continue;
            }
            
            double score = model.getVector().score(target);
            result.add(new DetectionResult(model.getLanguage(), score));
        }
        
        // Sort the results from high to low score.
        Collections.sort(result);
        
        // If our top result is a combo, rescore it.
        // TODO make it so
        DetectionResult topResult = result.get(0);
        if (rescoreGroups) {
            if (topResult.getLanguage().equals("cs+sk")) {
                double csScore = getModel("cs").getVector().score(target);
                double skScore = getModel("sk").getVector().score(target);
                if (csScore > skScore) {
                    topResult.setLanguage("cs");
                    topResult.setScore(csScore);
                } else {
                    topResult.setLanguage("sk");
                    topResult.setScore(skScore);
                }
            } else if (topResult.getLanguage().equals("da+de+sv")) {
                double daScore = getModel("da").getVector().score(target);
                double deScore = getModel("de").getVector().score(target);
                double svScore = getModel("sv").getVector().score(target);
                if (daScore > deScore) {
                    if (daScore > svScore) {
                        topResult.setLanguage("da");
                        topResult.setScore(daScore);
                    } else {
                        topResult.setLanguage("sv");
                        topResult.setScore(svScore);
                    }
                } else if (deScore > svScore) {
                    topResult.setLanguage("de");
                    topResult.setScore(deScore);
                } else {
                    topResult.setLanguage("sv");
                    topResult.setScore(svScore);
                }
            }
        }
        
        // FUTURE support option to rescore any combo results, not just top one - more
        // time, but higher accuracy.
        
        // Calculate confidence based on absolute score and delta from next closest.
        // TODO use absolute score here as a factor?
        double topScore = topResult.getScore();
        double nextScore = result.size() > 1 ? result.get(1).getScore() : 0.0;
        double delta = topScore - nextScore;
        
        // Confidence is 1.0 if top score is more than 2x the next score, or 0.0 if they
        // are the same.
        double topConfidence = Math.min(1.0, delta/nextScore);
        
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

    private LanguageModel getModel(String language) {
        for (LanguageModel model : _models) {
            
            if (model.getLanguage().equals(language)) {
                return model;
            }
        }
        
        throw new IllegalArgumentException("Unknown language: " + language);
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
