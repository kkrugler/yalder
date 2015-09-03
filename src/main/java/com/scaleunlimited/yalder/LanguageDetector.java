package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanguageDetector {

    private static final double ALPHA = 0.5/10000;

    private static final double MIN_LANG_PROBABILITY = 0.1;
    
    private Collection<LanguageModel> _models;
    private Map<CharSequence, Map<String, Double>> _ngramProbabilities;
    private Map<String, Double> _langProbabilities;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        _models = models;
        
        // TODO here's the approach
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        Map<CharSequence, Map<String, Integer>> ngramCounts = new HashMap<CharSequence, Map<String, Integer>>();
        _langProbabilities = new HashMap<String, Double>();
        
        for (LanguageModel model : _models) {
            String language = model.getLanguage();
            _langProbabilities.put(language, 0.0);
            
            Map<CharSequence, Integer> langCounts = model.getNGramCounts();
            for (CharSequence ngram : langCounts.keySet()) {
                Map<String, Integer> curCounts = ngramCounts.get(ngram);
                if (curCounts == null) {
                    curCounts = new HashMap<String, Integer>();
                    ngramCounts.put(ngram, curCounts);
                }
                
                int newCount = langCounts.get(ngram);
                Integer curCount = curCounts.get(language);
                if (curCount == null) {
                    curCounts.put(language, newCount);
                } else {
                    curCounts.put(language, curCount + newCount);
                }
            }
        }
        
        // Now we can calculate the probabilities
        _ngramProbabilities = new HashMap<CharSequence, Map<String,Double>>();
        for (CharSequence ngram : ngramCounts.keySet()) {
            Map<String, Integer> counts = ngramCounts.get(ngram);
            double totalCount = 0;
            for (String language : counts.keySet()) {
                totalCount += counts.get(language);
            }
            
            Map<String, Double> probabilities = new HashMap<String, Double>();
            for (String language : counts.keySet()) {
                probabilities.put(language, counts.get(language)/totalCount);
            }
            
            _ngramProbabilities.put(ngram, probabilities);
        }
    }
    
    public Collection<DetectionResult> detect(CharSequence text) {
        double startingProb = 1.0 / _langProbabilities.size();
        for (String language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
        }
        
        int numKnownNGrams = 0;
        int numUnknownNGrams = 0;
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, ModelBuilder.MAX_NGRAM_LENGTH);
        while (tokenizer.hasNext()) {
            CharSequence ngram = tokenizer.next();
            
            Map<String, Double> probs = _ngramProbabilities.get(ngram);
            if (probs == null) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                numUnknownNGrams += 1;
                continue;
            }
            
            numKnownNGrams += 1;
            
            for (String language : _langProbabilities.keySet()) {
                Double probObj = probs.get(language);
                double prob = (probObj == null ? ALPHA : probObj);
                double curProb = _langProbabilities.get(language);
                curProb *= prob;
                _langProbabilities.put(language, curProb);
            }
            
            if ((numKnownNGrams % 10) == 0) {
                normalizeLangProbabilities();
            }
        }
        
        normalizeLangProbabilities();

        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (String language : _langProbabilities.keySet()) {
            double curProb = _langProbabilities.get(language);
            
            if (curProb >= MIN_LANG_PROBABILITY) {
                result.add(new DetectionResult(language, curProb));
            }
        }

        return result;
    }

    private void normalizeLangProbabilities() {
        double totalProb = 0.0;
        for (String language : _langProbabilities.keySet()) {
            totalProb += _langProbabilities.get(language);
        }

        double scalar = 1.0/totalProb;
        
        for (String language : _langProbabilities.keySet()) {
            double curProb = _langProbabilities.get(language);
            curProb *= scalar;
            _langProbabilities.put(language, curProb);
        }
    }

    private LanguageModel getModel(String language) {
        for (LanguageModel model : _models) {
            
            if (model.getLanguage().equals(language)) {
                return model;
            }
        }
        
        throw new IllegalArgumentException("Unknown language: " + language);
    }

}
