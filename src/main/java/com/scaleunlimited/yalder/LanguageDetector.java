package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanguageDetector {

    public static final double DEFAULT_ALPHA = 0.0001; // 0.5/10000;

    private static final double MIN_LANG_PROBABILITY = 0.1;
    
    private Collection<LanguageModel> _models;
    private Map<CharSequence, Map<String, Double>> _ngramProbabilities;
    private Map<String, Double> _langProbabilities;
    private int _maxNGramLength;
    private double _alpha;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        this(models, models.iterator().next().getMaxNGramLength());
    }

    // FUTURE support varying max ngram length per model.
    public LanguageDetector(Collection<LanguageModel> models, int maxNGramLength) {
        _models = models;
        _maxNGramLength = maxNGramLength;
        _alpha = DEFAULT_ALPHA;
        
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
    
    public LanguageDetector setAlpha(double alpha) {
        _alpha = alpha;
        return this;
    }
    
    public Collection<DetectionResult> detect(CharSequence text) {
        return detect(text, false);
    }
    
    public Collection<DetectionResult> detect(CharSequence text, boolean provideDetails) {
        double startingProb = 1.0 / _langProbabilities.size();
        for (String language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
        }
        
        int numKnownNGrams = 0;
        int numUnknownNGrams = 0;
        StringBuilder details = provideDetails ? new StringBuilder() : null;
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, _maxNGramLength);
        while (tokenizer.hasNext()) {
            CharSequence ngram = tokenizer.next();
            
            Map<String, Double> probs = _ngramProbabilities.get(ngram);
            if (probs == null) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                numUnknownNGrams += 1;
                
                if (provideDetails) {
                    details.append(String.format("'%s': not found\n", ngram));
                }
                
                continue;
            }
            
            numKnownNGrams += 1;
            if (provideDetails) {
                details.append(String.format("'%s'\n", ngram));
            }
            
            
            for (String language : _langProbabilities.keySet()) {
                Double probObj = probs.get(language);
                double prob = (probObj == null ? _alpha : probObj);
                if (provideDetails && (probObj != null)) {
                    details.append(String.format("\t'%s': %f\n", language, prob));
                }
                
                double curProb = _langProbabilities.get(language);
                curProb *= prob;
                _langProbabilities.put(language, curProb);
            }
            
            if (provideDetails || (numKnownNGrams % 10) == 0) {
                normalizeLangProbabilities();
            }
            
            if (provideDetails) {
                details.append("Probabilities: ");
                for (String language : _langProbabilities.keySet()) {
                    double langProb = _langProbabilities.get(language);
                    if (langProb > 0.0000005) {
                        details.append(String.format("'%s'=%f ", language, langProb));
                    }
                }
                
                details.append('\n');
            }
        }
        
        normalizeLangProbabilities();

        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (String language : _langProbabilities.keySet()) {
            double curProb = _langProbabilities.get(language);
            
            if (curProb >= MIN_LANG_PROBABILITY) {
                DetectionResult dr = new DetectionResult(language, curProb);
                if (provideDetails) {
                    // TODO this adds the same details for every language.
                    dr.setDetails(details.toString());
                }
                
                result.add(dr);
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
